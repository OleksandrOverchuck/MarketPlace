package pl.jollycart.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import pl.jollycart.auth.dto.LoginRequest;
import pl.jollycart.auth.dto.PendingRegistrationResponse;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.auth.dto.ResendCodeRequest;
import pl.jollycart.auth.dto.VerifyEmailRequest;
import pl.jollycart.user.User;
import pl.jollycart.user.UserService;
import pl.jollycart.user.dto.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RegistrationService registrationService;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    private final SecurityContextLogoutHandler logoutHandler =
            new SecurityContextLogoutHandler();

    public AuthController(
            AuthService authService,
            UserService userService,
            RegistrationService registrationService
    ) {
        this.authService = authService;
        this.userService = userService;
        this.registrationService = registrationService;
    }

    /**
     * Krok 1 rejestracji: NIE tworzy konta, tylko wysyła kod na email.
     */
    @PostMapping("/register")
    public ResponseEntity<PendingRegistrationResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        PendingRegistrationResponse response =
                registrationService.startRegistration(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    /**
     * Krok 2 rejestracji: sprawdza kod i dopiero wtedy tworzy konto.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<UserResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        UserResponse response = registrationService.verify(
                request.email(),
                request.code()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/resend-code")
    public ResponseEntity<PendingRegistrationResponse> resendCode(
            @Valid @RequestBody ResendCodeRequest request
    ) {
        return ResponseEntity.ok(
                registrationService.resendCode(request.email())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<Map<String, String>> handleEmailSend(
            EmailSendException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Authentication authentication =
                authService.authenticate(request);

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        User user = authService.getUserByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                UserResponse.from(user)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication
    ) {
        UserResponse user =
                userService.getUserByEmail(
                        authentication.getName()
                );

        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        logoutHandler.logout(
                request,
                response,
                authentication
        );

        return ResponseEntity.noContent().build();
    }
}