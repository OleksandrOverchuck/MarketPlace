package pl.jollycart.user.dto;

import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;

public record UserResponse(
        Long id,
        String nickname,
        String email,
        String avatarUrl,
        AuthProvider authProvider
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getAuthProvider()
        );
    }
}