package pl.jollycart.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.jollycart.user.dto.UpdateUserRequest;
import pl.jollycart.user.dto.UserResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void shouldGetUserByEmail() {
        User user = new User();
        user.setId(1L);
        user.setNickname("TestUser");
        user.setEmail("test@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        UserResponse response =
                userService.getUserByEmail("test@example.com");

        assertEquals(1L, response.id());
        assertEquals("TestUser", response.nickname());
        assertEquals("test@example.com", response.email());
        assertEquals(AuthProvider.LOCAL, response.authProvider());
    }

    @Test
    void shouldUpdateOwnUser() {
        User user = new User();
        user.setId(1L);
        user.setNickname("OldName");
        user.setEmail("test@example.com");

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "NewName",
                        "https://example.com/avatar.png"
                );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("NewName", 1L))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response =
                userService.updateUser("test@example.com", request);

        assertEquals("NewName", response.nickname());
        assertEquals(
                "https://example.com/avatar.png",
                response.avatarUrl()
        );

        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectUpdateWhenNicknameIsTaken() {
        User user = new User();
        user.setId(1L);
        user.setNickname("OldName");
        user.setEmail("test@example.com");

        UpdateUserRequest request =
                new UpdateUserRequest("TakenName", null);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("TakenName", 1L))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userService.updateUser(
                                "test@example.com",
                                request
                        )
                );

        assertEquals("Nickname jest już zajęty", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
