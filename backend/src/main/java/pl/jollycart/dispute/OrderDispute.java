package pl.jollycart.dispute;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/*
 * Zgłoszenie problemu z zamówieniem (tytuł, opis, zdjęcia - te w DisputeImage).
 *
 * Jedno zamówienie ma najwyżej jedno zgłoszenie. Zamówienie przechodzi w status
 * DISPUTED (środki wstrzymane), a zgłoszenie czeka w statusie OPEN, aż
 * administrator lub moderator je rozstrzygnie.
 *
 * Powiązanie z zamówieniem to sam identyfikator (jak w PurchaseOrder) -
 * historia sporów nie blokuje usuwania ogłoszeń i kont.
 */
@Entity
@Table(
        name = "order_disputes",
        indexes = {
                @Index(name = "idx_order_disputes_status", columnList = "status")
        }
)
public class OrderDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    // Kto zgłosił (kupujący)
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DisputeResolution resolution;

    @Column(name = "resolver_id")
    private Long resolverId;

    @Column(name = "resolver_nickname", length = 30)
    private String resolverNickname;

    // Uzasadnienie decyzji (opcjonalne)
    @Column(name = "resolver_note", length = 500)
    private String resolverNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public OrderDispute() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DisputeStatus getStatus() { return status; }
    public void setStatus(DisputeStatus status) { this.status = status; }
    public DisputeResolution getResolution() { return resolution; }
    public void setResolution(DisputeResolution resolution) { this.resolution = resolution; }
    public Long getResolverId() { return resolverId; }
    public void setResolverId(Long resolverId) { this.resolverId = resolverId; }
    public String getResolverNickname() { return resolverNickname; }
    public void setResolverNickname(String v) { this.resolverNickname = v; }
    public String getResolverNote() { return resolverNote; }
    public void setResolverNote(String resolverNote) { this.resolverNote = resolverNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
