package pl.jollycart.image;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pl.jollycart.file.FileService;
import pl.jollycart.image.dto.OfferImageResponse;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;

@Service
@Transactional
public class OfferImageService {

    private final OfferImageRepository offerImageRepository;
    private final OfferRepository offerRepository;
    private final FileService fileService;

    public OfferImageService(
            OfferImageRepository offerImageRepository,
            OfferRepository offerRepository,
            FileService fileService
    ) {
        this.offerImageRepository = offerImageRepository;
        this.offerRepository = offerRepository;
        this.fileService = fileService;
    }

    public OfferImageResponse uploadImage(
            Long offerId,
            String currentEmail,
            MultipartFile file
    ) {

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        if (!offer.getUser().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException(
                    "Nie możesz dodać zdjęcia do cudzej oferty"
            );
        }

        validateImage(file);

        String storedFileName = fileService.storeFile(file);

        OfferImage image = new OfferImage();

        image.setOffer(offer);
        image.setFileName(file.getOriginalFilename());
        image.setStoredFileName(storedFileName);
        image.setContentType(file.getContentType());
        image.setFileSize(file.getSize());

        OfferImage savedImage =
                offerImageRepository.save(image);

        return OfferImageResponse.from(savedImage);
    }

    @Transactional(readOnly = true)
    public List<OfferImageResponse> getImagesByOfferId(
            Long offerId
    ) {

        if (!offerRepository.existsById(offerId)) {
            throw new IllegalArgumentException(
                    "Oferta nie została znaleziona"
            );
        }

        return offerImageRepository
                .findByOfferIdOrderByCreatedAtAsc(offerId)
                .stream()
                .map(OfferImageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OfferImage getImageById(Long imageId) {

        return offerImageRepository.findById(imageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Zdjęcie nie zostało znalezione"
                        )
                );
    }

    public void deleteImage(
            Long imageId,
            String currentEmail
    ) {

        OfferImage image = offerImageRepository.findById(imageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Zdjęcie nie zostało znalezione"
                        )
                );

        Offer offer = image.getOffer();

        if (!offer.getUser().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException(
                    "Nie możesz usunąć zdjęcia z cudzej oferty"
            );
        }

        fileService.deleteFile(
                image.getStoredFileName()
        );

        offerImageRepository.delete(image);
    }

    private void validateImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Plik nie może być pusty"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !contentType.startsWith("image/")) {

            throw new IllegalArgumentException(
                    "Dozwolone są tylko pliki graficzne"
            );
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Obraz nie może być większy niż 10 MB"
            );
        }
    }
}