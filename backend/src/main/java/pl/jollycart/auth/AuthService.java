package pl.jollycart.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jollycart.auth.dto.LoginRequest;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;
import pl.jollycart.user.dto.UserResponse;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public UserResponse register(RegisterRequest request) {

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException(
                    "Hasła nie są zgodne"
            );
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Email jest już zajęty"
            );
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new IllegalArgumentException(
                    "Nickname jest już zajęty"
            );
        }

        User user = new User();

        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setProviderId(null);
        user.setAvatarUrl(null);

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
    return userRepository.findByEmail(email)
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Użytkownik nie został znaleziony"
                    )
            );
}

    public Authentication authenticate(LoginRequest request) {

        return authenticationManager.authenticate(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );
    }
}