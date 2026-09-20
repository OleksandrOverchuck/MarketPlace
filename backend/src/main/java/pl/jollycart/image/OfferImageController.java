package pl.jollycart.image;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pl.jollycart.image.dto.OfferImageResponse;

@RestController
@RequestMapping("/api/offers")
public class OfferImageController {

    private final OfferImageService offerImageService;

    public OfferImageController(
            OfferImageService offerImageService
    ) {
        this.offerImageService = offerImageService;
    }

    @PostMapping(
            value = "/{offerId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<OfferImageResponse> uploadImage(
            @PathVariable Long offerId,
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {

        String currentEmail = authentication.getName();

        OfferImageResponse image =
                offerImageService.uploadImage(
                        offerId,
                        currentEmail,
                        file
                );

        return ResponseEntity.ok(image);
    }

    @GetMapping("/{offerId}/images")
    public ResponseEntity<List<OfferImageResponse>> getImages(
            @PathVariable Long offerId
    ) {

        return ResponseEntity.ok(
                offerImageService.getImagesByOfferId(offerId)
        );
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<Resource> getImage(
            @PathVariable Long imageId
    ) throws IOException {

        OfferImage image =
                offerImageService.getImageById(imageId);

        Path filePath = Paths.get("uploads")
                .toAbsolutePath()
                .normalize()
                .resolve(image.getStoredFileName())
                .normalize();

        Resource resource =
                new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(
                    image.getContentType()
            );
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" +
                                image.getFileName() +
                                "\""
                )
                .body(resource);
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long imageId,
            Authentication authentication
    ) {

        String currentEmail = authentication.getName();

        offerImageService.deleteImage(
                imageId,
                currentEmail
        );

        return ResponseEntity.noContent().build();
    }
}