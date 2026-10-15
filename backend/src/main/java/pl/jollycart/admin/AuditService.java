package pl.jollycart.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.user.User;

@Service
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(
            User actor,
            AuditAction action,
            String targetType,
            Long targetId,
            String details
    ) {
        String safeDetails = details;

        if (safeDetails != null && safeDetails.length() > 1000) {
            safeDetails = safeDetails.substring(0, 1000);
        }

        auditLogRepository.save(
                new AuditLog(
                        actor.getId(),
                        actor.getNickname(),
                        action,
                        targetType,
                        targetId,
                        safeDetails
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        return PageResponse.of(
                auditLogRepository.findAll(
                        PageRequest.of(
                                safePage,
                                safeSize,
                                Sort.by(Sort.Direction.DESC, "id")
                        )
                ),
                AuditLogResponse::from
        );
    }
}
