package pl.jollycart.image.dto;

import java.time.LocalDateTime;

import pl.jollycart.image.OfferImage;

public record OfferImageResponse(
        Long id,
        String fileName,
        String storedFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {

    public static OfferImageResponse from(OfferImage image) {
        return new OfferImageResponse(
                image.getId(),
                image.getFileName(),
                image.getStoredFileName(),
                image.getContentType(),
                image.getFileSize(),
                image.getCreatedAt()
        );
    }
}