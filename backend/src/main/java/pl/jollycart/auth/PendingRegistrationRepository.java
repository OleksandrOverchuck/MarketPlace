package pl.jollycart.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository
        extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByEmail(String email);

    boolean existsByNicknameAndEmailNot(String nickname, String email);

    void deleteByCreatedAtBefore(LocalDateTime threshold);
}
