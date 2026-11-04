package pl.jollycart.admin;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/**
 * Przy starcie aplikacji tworzy konto administratora, jeśli w bazie
 * nie ma jeszcze żadnego ADMINA.
 *
 *  - dane konta: ADMIN_EMAIL / ADMIN_NICKNAME / ADMIN_PASSWORD
 *    (zmienne środowiskowe, patrz application.properties)
 *  - brak ADMIN_PASSWORD  -> hasło startowe z DEFAULT_PASSWORD, a konto
 *    musi je zmienić przy pierwszym logowaniu (panel jest zablokowany
 *    do czasu zmiany hasła).
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AdminInitializer.class);

    /** Hasło startowe - ZMIEŃ JE po pierwszym zalogowaniu. */
    public static final String DEFAULT_PASSWORD = "Admin12345";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminNickname;
    private final String adminPassword;

    public AdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.nickname}") String adminNickname,
            @Value("${app.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminNickname = adminNickname;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {

        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        Optional<User> existing = userRepository.findByEmail(adminEmail);

        if (existing.isPresent()) {
            User user = existing.get();

            user.setRole(Role.ADMIN);
            userRepository.save(user);

            log.warn(
                    "Konto {} już istniało - nadano mu rolę ADMIN "
                            + "(hasło bez zmian).",
                    adminEmail
            );
            return;
        }

        boolean usingDefault = adminPassword == null || adminPassword.isBlank();

        if (!usingDefault && adminPassword.length() < 8) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD musi mieć co najmniej 8 znaków"
            );
        }

        String rawPassword = usingDefault
                ? DEFAULT_PASSWORD
                : adminPassword;

        User admin = new User();

        admin.setEmail(adminEmail);
        admin.setNickname(uniqueNickname(adminNickname));
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setPasswordChangeRequired(usingDefault);

        userRepository.save(admin);

        if (usingDefault) {
            log.warn("""

                    ==================================================
                     UTWORZONO KONTO ADMINISTRATORA
                       email: {}
                       hasło: {}
                     Hasło tymczasowe - zostaniesz poproszony o jego
                     zmianę przy pierwszym zalogowaniu.
                    ==================================================
                    """,
                    adminEmail,
                    rawPassword
            );
        } else {
            log.info(
                    "Utworzono konto administratora {} (hasło z ADMIN_PASSWORD).",
                    adminEmail
            );
        }
    }

    private String uniqueNickname(String base) {

        String candidate = base;
        int counter = 1;

        while (userRepository.existsByNickname(candidate)) {
            String suffix = "_" + counter++;

            candidate = base.substring(
                    0,
                    Math.min(base.length(), 30 - suffix.length())
            ) + suffix;
        }

        return candidate;
    }

}
