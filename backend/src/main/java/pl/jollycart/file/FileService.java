package pl.jollycart.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private final Path uploadDirectory;

    public FileService(
            @Value("${jollycart.upload.directory:uploads}") String uploadDirectory
    ) {
        this.uploadDirectory = Paths.get(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nie można utworzyć katalogu na pliki",
                    e
            );
        }
    }

    public String storeFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Plik nie może być pusty"
            );
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Nazwa pliku jest wymagana"
            );
        }

        String extension = getFileExtension(originalFileName);

        String storedFileName =
                UUID.randomUUID() + extension;

        Path targetPath = uploadDirectory
                .resolve(storedFileName)
                .normalize();

        if (!targetPath.getParent().equals(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Nieprawidłowa ścieżka pliku"
            );
        }

        try (InputStream inputStream = file.getInputStream()) {

            Files.copy(
                    inputStream,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nie udało się zapisać pliku",
                    e
            );
        }

        return storedFileName;
    }

    public void deleteFile(String storedFileName) {

        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }

        Path filePath = uploadDirectory
                .resolve(storedFileName)
                .normalize();

        if (!filePath.getParent().equals(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Nieprawidłowa ścieżka pliku"
            );
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nie udało się usunąć pliku",
                    e
            );
        }
    }

    private String getFileExtension(String fileName) {

        int lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1) {
            return "";
        }

        return fileName.substring(lastDot);
    }
}