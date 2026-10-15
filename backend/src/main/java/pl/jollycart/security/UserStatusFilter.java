package pl.jollycart.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/**
 * Działa przy każdym żądaniu zalogowanego użytkownika i sprawdza w bazie,
 * czy konto nadal istnieje, czy nie zostało zablokowane oraz jaką ma
 * aktualnie rolę.
 *
 * Dzięki temu:
 *  - ban działa NATYCHMIAST (także na aktywną sesję),
 *  - usunięcie konta przez admina wylogowuje użytkownika,
 *  - nadanie / odebranie roli moderatora działa bez ponownego logowania,
 *  - konto z wymuszoną zmianą hasła nie wejdzie do paneli staffu.
 */
public class UserStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserStatusFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> found =
                userRepository.findByEmail(authentication.getName());

        if (found.isEmpty()) {
            terminateSession(request);
            writeJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"message\":\"Konto nie istnieje\"}"
            );
            return;
        }

        User user = found.get();

        if (user.isBanned()) {
            terminateSession(request);

            String reason = user.getBanReason() == null
                    ? ""
                    : user.getBanReason();

            writeJson(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "{\"message\":\"Twoje konto zostało zablokowane\","
                            + "\"banned\":true,"
                            + "\"reason\":\"" + escape(reason) + "\"}"
            );
            return;
        }

        refreshRole(authentication, user);

        if (user.isPasswordChangeRequired() && isStaffPath(request)) {
            writeJson(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "{\"message\":\"Najpierw musisz zmienić hasło\","
                            + "\"passwordChangeRequired\":true}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isStaffPath(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/admin")
                || path.startsWith("/api/moderation");
    }

    private void refreshRole(Authentication authentication, User user) {

        String wanted = "ROLE_" + user.getRole().name();

        boolean hasWanted = false;
        boolean hasOtherRole = false;

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();

            if (name.equals(wanted)) {
                hasWanted = true;
            } else if (name.startsWith("ROLE_")) {
                hasOtherRole = true;
            }
        }

        if (hasWanted && !hasOtherRole) {
            return;
        }

        List<GrantedAuthority> fresh = new ArrayList<>();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (!authority.getAuthority().startsWith("ROLE_")) {
                fresh.add(authority);
            }
        }

        fresh.add(new SimpleGrantedAuthority(wanted));

        Authentication updated = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                fresh
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
    }

    private void terminateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
    }

    private void writeJson(
            HttpServletResponse response,
            int status,
            String json
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }

    private String escape(String value) {
        StringBuilder builder = new StringBuilder();

        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.toString();
    }
}
