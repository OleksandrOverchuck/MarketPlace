package pl.jollycart.user.dto;

import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;

public record UserResponse(
        Long id,
        String nickname,
        String email,
        String avatarUrl,
        String phone,
        String location,
        AuthProvider authProvider,
        Role role,
        boolean passwordChangeRequired
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getLocation(),
                user.getAuthProvider(),
                user.getRole(),
                user.isPasswordChangeRequired()
        );
    }
}
