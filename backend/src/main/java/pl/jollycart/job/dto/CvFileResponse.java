package pl.jollycart.job.dto;

import java.time.LocalDateTime;

import pl.jollycart.file.StoredFile;

public record CvFileResponse(
        Long id,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {

    public static CvFileResponse from(StoredFile file) {
        return new CvFileResponse(
                file.getId(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                file.getCreatedAt()
        );
    }
}