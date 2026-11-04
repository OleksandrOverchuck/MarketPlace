package pl.jollycart.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.auth.dto.PendingRegistrationResponse;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;
import pl.jollycart.user.dto.UserResponse;

/**
 * Dwuetapowa rejestracja kontem lokalnym:
 * 1) startRegistration - walidacja + wysłanie kodu na email,
 * 2) verify            - sprawdzenie kodu i dopiero wtedy utworzenie konta.
 */
@Service
public class RegistrationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration PENDING_RETENTION = Duration.ofHours(24);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int codeLength;

    private final SecureRandom random = new SecureRandom();

    public RegistrationService(
            UserRepository userRepository,
            PendingRegistrationRepository pendingRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.verification.code-length:6}") int codeLength
    ) {
        this.userRepository = userRepository;
        this.pendingRepository = pendingRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.codeLength = codeLength;
    }

    /* ---------- krok 1: formularz rejestracji ---------- */

    @Transactional
    public PendingRegistrationResponse startRegistration(RegisterRequest request) {

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Hasła nie są zgodne");
        }

        String email = request.email().trim();
        String nickname = request.nickname().trim();

        // sprzątanie bardzo starych, porzuconych rejestracji
        pendingRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minus(PENDING_RETENTION)
        );

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email jest już zajęty");
        }

        if (userRepository.existsByNickname(nickname)
                || pendingRepository.existsByNicknameAndEmailNot(nickname, email)) {
            throw new IllegalArgumentException("Nickname jest już zajęty");
        }

        // ten sam email może zacząć rejestrację ponownie
        // (np. zapomniał kodu) - nadpisujemy poprzednią próbę
        PendingRegistration pending = pendingRepository
                .findByEmail(email)
                .orElseGet(PendingRegistration::new);

        ensureCooldownPassed(pending);

        pending.setEmail(email);
        pending.setNickname(nickname);
        pending.setPasswordHash(passwordEncoder.encode(request.password()));

        String code = issueNewCode(pending);

        pendingRepository.save(pending);

        // jeśli wysyłka się nie uda, wyjątek cofnie zapis do bazy
        emailService.sendVerificationCode(email, code, CODE_TTL.toMinutes());

        return response(email);
    }

    /* ---------- krok 2: wpisanie kodu ---------- */

    // noRollbackFor: licznik błędnych prób MUSI zapisać się w bazie
    // mimo rzucanego wyjątku, inaczej limit prób nic by nie dawał
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public UserResponse verify(String rawEmail, String code) {

        String email = rawEmail.trim();

        PendingRegistration pending = pendingRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nie znaleziono rejestracji dla tego adresu. Zarejestruj się ponownie."
                ));

        if (pending.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "Zbyt wiele błędnych prób. Wyślij nowy kod."
            );
        }

        if (pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Kod wygasł. Wyślij nowy kod."
            );
        }

        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingRepository.save(pending);

            int left = MAX_ATTEMPTS - pending.getAttempts();

            throw new IllegalArgumentException(
                    left > 0
                            ? "Nieprawidłowy kod. Pozostało prób: " + left
                            : "Zbyt wiele błędnych prób. Wyślij nowy kod."
            );
        }

        // kod OK - jeszcze raz sprawdzamy unikalność (ktoś mógł
        // w międzyczasie zająć email/nick, np. przez Google)
        if (userRepository.existsByEmail(pending.getEmail())) {
            pendingRepository.delete(pending);
            throw new IllegalArgumentException("Email jest już zajęty");
        }

        if (userRepository.existsByNickname(pending.getNickname())) {
            pendingRepository.delete(pending);
            throw new IllegalArgumentException(
                    "Nickname został w międzyczasie zajęty. Zarejestruj się ponownie."
            );
        }

        User user = new User();

        user.setNickname(pending.getNickname());
        user.setEmail(pending.getEmail());
        user.setPasswordHash(pending.getPasswordHash());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setProviderId(null);
        user.setAvatarUrl(null);

        User saved = userRepository.save(user);

        pendingRepository.delete(pending);

        return UserResponse.from(saved);
    }

    /* ---------- ponowne wysłanie kodu ---------- */

    @Transactional
    public PendingRegistrationResponse resendCode(String rawEmail) {

        String email = rawEmail.trim();

        PendingRegistration pending = pendingRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nie znaleziono rejestracji dla tego adresu. Zarejestruj się ponownie."
                ));

        ensureCooldownPassed(pending);

        String code = issueNewCode(pending);

        pendingRepository.save(pending);

        emailService.sendVerificationCode(email, code, CODE_TTL.toMinutes());

        return response(email);
    }

    /* ---------- pomocnicze ---------- */

    private void ensureCooldownPassed(PendingRegistration pending) {
        if (pending.getLastSentAt() == null) {
            return;
        }

        long secondsLeft = Duration
                .between(
                        LocalDateTime.now(),
                        pending.getLastSentAt().plus(RESEND_COOLDOWN)
                )
                .getSeconds();

        if (secondsLeft > 0) {
            throw new IllegalArgumentException(
                    "Odczekaj " + secondsLeft + " s przed wysłaniem kolejnego kodu."
            );
        }
    }

    /** Losuje nowy kod, ustawia hash, ważność i zeruje licznik prób. */
    private String issueNewCode(PendingRegistration pending) {
        int bound = (int) Math.pow(10, codeLength);

        String code = String.format(
                "%0" + codeLength + "d",
                random.nextInt(bound)
        );

        LocalDateTime now = LocalDateTime.now();

        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setExpiresAt(now.plus(CODE_TTL));
        pending.setLastSentAt(now);
        pending.setAttempts(0);

        return code;
    }

    private PendingRegistrationResponse response(String email) {
        return new PendingRegistrationResponse(
                email,
                CODE_TTL.getSeconds(),
                RESEND_COOLDOWN.getSeconds()
        );
    }
}
