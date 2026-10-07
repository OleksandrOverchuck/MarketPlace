package pl.jollycart.job.dto;

import java.time.LocalDateTime;

import pl.jollycart.job.JobApplication;
import pl.jollycart.job.JobApplicationStatus;

public record JobApplicationResponse(
        Long id,
        Long offerId,
        String offerTitle,
        Long userId,
        String userNickname,
        JobApplicationStatus status,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static JobApplicationResponse from(
            JobApplication application
    ) {
        return new JobApplicationResponse(
                application.getId(),
                application.getOffer().getId(),
                application.getOffer().getTitle(),
                application.getUser().getId(),
                application.getUser().getNickname(),
                application.getStatus(),
                application.getMessage(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}