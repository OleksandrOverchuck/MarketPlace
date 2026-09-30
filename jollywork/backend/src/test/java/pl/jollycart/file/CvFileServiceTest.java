package pl.jollycart.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvFileServiceTest {

    @Mock
    private FileService fileService;

    @Mock
    private StoredFileRepository storedFileRepository;

    private CvFileService cvFileService;

    @BeforeEach
    void setUp() {
        cvFileService = new CvFileService(
                fileService,
                storedFileRepository
        );
    }

    @Test
    void shouldStoreValidPdf() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cv.pdf",
                        "application/pdf",
                        "PDF content".getBytes()
                );

        when(fileService.storeFile(file))
                .thenReturn("stored-cv.pdf");
        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> {
                    StoredFile storedFile =
                            invocation.getArgument(0);
                    storedFile.setId(1L);
                    return storedFile;
                });

        StoredFile result = cvFileService.storeCv(file);

        assertEquals(1L, result.getId());
        assertEquals("cv.pdf", result.getOriginalFileName());
        assertEquals("stored-cv.pdf", result.getStoredFileName());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(file.getSize(), result.getFileSize());
        verify(fileService).storeFile(file);
        verify(storedFileRepository).save(any(StoredFile.class));
    }

    @Test
    void shouldRejectNonPdfContentType() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cv.pdf",
                        "image/png",
                        new byte[]{1, 2, 3}
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cvFileService.storeCv(file)
                );

        assertEquals(
                "CV musi być zapisane w formacie PDF",
                exception.getMessage()
        );
        verify(fileService, never()).storeFile(any());
    }

    @Test
    void shouldRejectWrongExtension() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cv.txt",
                        "application/pdf",
                        new byte[]{1, 2, 3}
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cvFileService.storeCv(file)
                );

        assertEquals(
                "CV musi mieć rozszerzenie .pdf",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFileLargerThan5Mb() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "cv.pdf",
                        "application/pdf",
                        content
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cvFileService.storeCv(file)
                );

        assertEquals(
                "CV nie może być większe niż 5 MB",
                exception.getMessage()
        );
    }
}
