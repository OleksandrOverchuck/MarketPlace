package pl.jollycart.file;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CvFileService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final FileService fileService;
    private final StoredFileRepository storedFileRepository;

    public CvFileService(
            FileService fileService,
            StoredFileRepository storedFileRepository
    ) {
        this.fileService = fileService;
        this.storedFileRepository = storedFileRepository;
    }

    public StoredFile storeCv(MultipartFile file) {

        validatePdf(file);

        String storedFileName =
                fileService.storeFile(file);

        StoredFile storedFile = new StoredFile();

        storedFile.setOriginalFileName(
                file.getOriginalFilename()
        );
        storedFile.setStoredFileName(storedFileName);
        storedFile.setContentType("application/pdf");
        storedFile.setFileSize(file.getSize());

        return storedFileRepository.save(storedFile);
    }

    private void validatePdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Plik CV nie może być pusty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "CV nie może być większe niż 5 MB"
            );
        }

        String contentType = file.getContentType();

        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                    "CV musi być zapisane w formacie PDF"
            );
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null ||
                !fileName.toLowerCase().endsWith(".pdf")) {

            throw new IllegalArgumentException(
                    "CV musi mieć rozszerzenie .pdf"
            );
        }
    }
}