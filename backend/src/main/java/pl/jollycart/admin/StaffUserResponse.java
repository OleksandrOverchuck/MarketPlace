package pl.jollycart.admin;

import java.time.LocalDateTime;

import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;

public record StaffUserResponse(
        Long id,
        String nickname,
        String email,
        Role role,
        AccountStatus status,
        AuthProvider authProvider,
        String avatarUrl,
        String banReason,
        LocalDateTime bannedAt,
        LocalDateTime createdAt,
        long offersCount
) {

    public static StaffUserResponse from(User user, long offersCount) {
        return new StaffUserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAuthProvider(),
                user.getAvatarUrl(),
                user.getBanReason(),
                user.getBannedAt(),
                user.getCreatedAt(),
                offersCount
        );
    }
}
