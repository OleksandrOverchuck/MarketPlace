package pl.jollycart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Nickname jest wymagany")
        @Size(
                min = 3,
                max = 30,
                message = "Nickname musi mieć od 3 do 30 znaków"
        )
        String nickname,

        @Size(
                max = 1000,
                message = "URL avatara może mieć maksymalnie 1000 znaków"
        )
        String avatarUrl
) {
}