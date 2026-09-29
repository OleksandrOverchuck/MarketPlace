package pl.jollycart.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;
import pl.jollycart.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager
        );
    }

    @Test
    void shouldRegisterNewUser() {

        RegisterRequest request = new RegisterRequest(
                "TestUser",
                "test@example.com",
                "TestPassword123!",
                "TestPassword123!"
        );

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(userRepository.existsByNickname("TestUser"))
                .thenReturn(false);

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setNickname("TestUser");
        savedUser.setEmail("test@example.com");
        savedUser.setPasswordHash(
                passwordEncoder.encode("TestPassword123!")
        );
        savedUser.setAuthProvider(AuthProvider.LOCAL);

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        UserResponse response =
                authService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("TestUser", response.nickname());
        assertEquals("test@example.com", response.email());
        assertEquals(AuthProvider.LOCAL, response.authProvider());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectRegistrationWhenPasswordsDoNotMatch() {

        RegisterRequest request = new RegisterRequest(
                "TestUser",
                "test@example.com",
                "TestPassword123!",
                "DifferentPassword123!"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.register(request)
                );

        assertEquals(
                "Hasła nie są zgodne",
                exception.getMessage()
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectRegistrationWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "TestUser",
                "test@example.com",
                "TestPassword123!",
                "TestPassword123!"
        );

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.register(request)
                );

        assertEquals(
                "Email jest już zajęty",
                exception.getMessage()
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectRegistrationWhenNicknameAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "TestUser",
                "test@example.com",
                "TestPassword123!",
                "TestPassword123!"
        );

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(userRepository.existsByNickname("TestUser"))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.register(request)
                );

        assertEquals(
                "Nickname jest już zajęty",
                exception.getMessage()
        );

        verify(userRepository, never()).save(any(User.class));
    }
}