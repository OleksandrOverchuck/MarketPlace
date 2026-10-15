package pl.jollycart.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.user.dto.ChangePasswordRequest;

@Service
@Transactional
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void changePassword(
            String email,
            ChangePasswordRequest request
    ) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        if (user.getAuthProvider() != AuthProvider.LOCAL
                || user.getPasswordHash() == null) {
            throw new IllegalArgumentException(
                    "To konto nie używa hasła (logowanie przez Google)"
            );
        }

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "Obecne hasło jest nieprawidłowe"
            );
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException(
                    "Hasła nie są zgodne"
            );
        }

        if (request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException(
                    "Nowe hasło musi różnić się od obecnego"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );
        user.setPasswordChangeRequired(false);

        userRepository.save(user);
    }
}
