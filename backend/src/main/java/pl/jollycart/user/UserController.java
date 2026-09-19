package pl.jollycart.user;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import pl.jollycart.user.dto.UpdateUserRequest;
import pl.jollycart.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;


    public UserController(UserService userService) {
        this.userService = userService;
    }


    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication
    ) {

        String email = authentication.getName();

        UserResponse user = userService.getUserByEmail(email);

        return ResponseEntity.ok(user);
    }


    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request
    ) {

        String email = authentication.getName();

        UserResponse updatedUser =
                userService.updateUser(email, request);

        return ResponseEntity.ok(updatedUser);
    }
}