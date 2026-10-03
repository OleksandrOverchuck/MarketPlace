package pl.jollycart.image;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.image.dto.OfferImageResponse;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;

@Service
@Transactional
public class OfferImageService {

    private final OfferImageRepository offerImageRepository;
    private final OfferRepository offerRepository;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;

    public OfferImageService(
            OfferImageRepository offerImageRepository,
            OfferRepository offerRepository,
            FileService fileService,
            ImageFileValidator imageFileValidator
    ) {
        this.offerImageRepository = offerImageRepository;
        this.offerRepository = offerRepository;
        this.fileService = fileService;
        this.imageFileValidator = imageFileValidator;
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

        imageFileValidator.validate(
                file,
                10 * 1024 * 1024
        );

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

    
}