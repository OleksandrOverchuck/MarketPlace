package pl.jollycart.image;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.image.dto.OfferImageResponse;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;
import pl.jollycart.user.User;

@ExtendWith(MockitoExtension.class)
class OfferImageServiceTest {

    @Mock
    private OfferImageRepository offerImageRepository;

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private FileService fileService;

    @Mock
    private ImageFileValidator imageFileValidator;

    private OfferImageService offerImageService;

    @BeforeEach
    void setUp() {
        offerImageService = new OfferImageService(
                offerImageRepository,
                offerRepository,
                fileService,
                imageFileValidator
        );
    }

    @Test
    void shouldUploadImageToOwnOffer() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(2L);
        offer.setUser(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(offerRepository.findById(2L))
                .thenReturn(Optional.of(offer));

        when(fileService.storeFile(file))
                .thenReturn("stored-photo.png");

        when(offerImageRepository.save(any(OfferImage.class)))
                .thenAnswer(invocation -> {
                    OfferImage image = invocation.getArgument(0);
                    image.setId(1L);
                    return image;
                });

        OfferImageResponse response =
                offerImageService.uploadImage(
                        2L,
                        "owner@example.com",
                        file
                );

        verify(imageFileValidator)
                .validate(file, 10 * 1024 * 1024);

        assertEquals(1L, response.id());
        assertEquals("photo.png", response.fileName());
        assertEquals("stored-photo.png", response.storedFileName());
        assertEquals("image/png", response.contentType());
        assertEquals(3L, response.fileSize());
    }

    @Test
    void shouldRejectImageUploadToAnotherUsersOffer() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(2L);
        offer.setUser(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{1}
        );

        when(offerRepository.findById(2L))
                .thenReturn(Optional.of(offer));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> offerImageService.uploadImage(
                                2L,
                                "other@example.com",
                                file
                        )
                );

        assertEquals(
                "Nie możesz dodać zdjęcia do cudzej oferty",
                exception.getMessage()
        );

        verify(imageFileValidator, never())
                .validate(any(), anyLong());

        verify(fileService, never())
                .storeFile(any());
    }

    @Test
    void shouldRejectNonImageFile() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(2L);
        offer.setUser(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                new byte[]{1, 2}
        );

        when(offerRepository.findById(2L))
                .thenReturn(Optional.of(offer));

        doThrow(new IllegalArgumentException(
                "Dozwolone są tylko pliki PNG i JPG"
        ))
                .when(imageFileValidator)
                .validate(file, 10 * 1024 * 1024);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> offerImageService.uploadImage(
                                2L,
                                "owner@example.com",
                                file
                        )
                );

        assertEquals(
                "Dozwolone są tylko pliki PNG i JPG",
                exception.getMessage()
        );

        verify(imageFileValidator)
                .validate(file, 10 * 1024 * 1024);

        verify(fileService, never())
                .storeFile(any());
    }

    @Test
    void shouldDeleteOwnImage() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(2L);
        offer.setUser(owner);

        OfferImage image = new OfferImage();
        image.setId(1L);
        image.setOffer(offer);
        image.setStoredFileName("stored-photo.png");

        when(offerImageRepository.findById(1L))
                .thenReturn(Optional.of(image));

        offerImageService.deleteImage(
                1L,
                "owner@example.com"
        );

        verify(fileService)
                .deleteFile("stored-photo.png");

        verify(offerImageRepository)
                .delete(image);
    }
}