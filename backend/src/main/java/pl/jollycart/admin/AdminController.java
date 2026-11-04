package pl.jollycart.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Dostępne tylko dla ADMIN (patrz SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;

    public AdminController(
            AdminService adminService,
            AuditService auditService
    ) {
        this.adminService = adminService;
        this.auditService = auditService;
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        adminService.deleteUser(authentication.getName(), id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<StaffUserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                adminService.changeRole(
                        authentication.getName(),
                        id,
                        request.role()
                )
        );
    }

    @GetMapping("/audit-log")
    public ResponseEntity<PageResponse<AuditLogResponse>> auditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok(auditService.list(page, size));
    }
}
