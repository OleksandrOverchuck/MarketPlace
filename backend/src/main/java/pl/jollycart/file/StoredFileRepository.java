package pl.jollycart.file;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository
        extends JpaRepository<StoredFile, Long> {
}