package pl.jollycart.notification;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.jollycart.notification.dto.NotificationResponse;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>>
    getNotifications(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                notificationService.getUserNotifications(
                        authentication.getName()
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                notificationService.getUnreadCount(
                        authentication.getName()
                )
        );
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {

        notificationService.markAsRead(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            Authentication authentication
    ) {

        notificationService.markAllAsRead(
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }
}