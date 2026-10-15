package pl.jollycart.admin;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Dziennik działań administracji. Celowo BEZ kluczy obcych do users -
 * wpisy muszą przetrwać usunięcie użytkownika (także samego moderatora).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_nickname", length = 30)
    private String actorNickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    public AuditLog(
            Long actorId,
            String actorNickname,
            AuditAction action,
            String targetType,
            Long targetId,
            String details
    ) {
        this.actorId = actorId;
        this.actorNickname = actorNickname;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getActorId() { return actorId; }

    public String getActorNickname() { return actorNickname; }

    public AuditAction getAction() { return action; }

    public String getTargetType() { return targetType; }

    public Long getTargetId() { return targetId; }

    public String getDetails() { return details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
