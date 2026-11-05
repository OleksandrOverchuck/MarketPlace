package pl.jollycart.dispute;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.jollycart.dispute.DisputeDtos.DisputeDetail;
import pl.jollycart.dispute.DisputeDtos.DisputeSummary;

import java.nio.charset.StandardCharsets;

/*
 * Zakładka "Zgłoszenia" w panelu. Ścieżka /api/moderation/** jest w SecurityConfig
 * dostępna dla MODERATOR i ADMIN.
 */
@RestController
@RequestMapping("/api/moderation/disputes")
public class StaffDisputeController {

    public record ResolveRequest(DisputeResolution resolution, String note) {
    }

    private final DisputeService disputeService;

    public StaffDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    // ?status=OPEN (domyślnie, "Aktualne") albo ?status=RESOLVED ("Rozwiązane")
    @GetMapping
    public ResponseEntity<List<DisputeSummary>> list(
            @RequestParam(defaultValue = "OPEN") DisputeStatus status) {
        return ResponseEntity.ok(disputeService.list(status));
    }

    // Liczba aktualnych zgłoszeń (plakietka przy zakładce)
    @GetMapping("/open-count")
    public ResponseEntity<Map<String, Long>> openCount() {
        return ResponseEntity.ok(Map.of("count", disputeService.countOpen()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeDetail> detail(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getDetail(id));
    }

    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<Resource> image(
            @PathVariable Long id,
            @PathVariable Long imageId) {

        DisputeImage image = disputeService.getImage(id, imageId);

        Resource resource = disputeService.loadImageFile(image);

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(image.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(image.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<DisputeDetail> resolve(
            @PathVariable Long id,
            @RequestBody ResolveRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                disputeService.resolve(
                        authentication.getName(),
                        id,
                        request.resolution(),
                        request.note()));
    }
}
