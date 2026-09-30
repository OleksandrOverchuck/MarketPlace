package pl.jollycart.user;



import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import pl.jollycart.file.FileService;
import pl.jollycart.user.dto.UpdateUserRequest;
import pl.jollycart.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;

    private final UserService userService;
    private final FileService fileService;
    private final UserRepository userRepository;

    public UserController(
            UserService userService,
            FileService fileService,
            UserRepository userRepository
    ) {
        this.userService = userService;
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication
    ) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        String email = authentication.getName();
        UserResponse updatedUser = userService.updateUser(email, request);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping(
            value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UserResponse> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        validateAvatar(file);

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        String oldAvatarUrl = user.getAvatarUrl();
        String storedFileName = fileService.storeFile(file);

        user.setAvatarUrl("/api/users/avatar/" + storedFileName);
        User savedUser = userRepository.save(user);

        deleteOldLocalAvatar(oldAvatarUrl);

        return ResponseEntity.ok(UserResponse.from(savedUser));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(
            Authentication authentication
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        String oldAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        User savedUser = userRepository.save(user);

        deleteOldLocalAvatar(oldAvatarUrl);

        return ResponseEntity.ok(UserResponse.from(savedUser));
    }

    @GetMapping("/avatar/{storedFileName:.+}")
    public ResponseEntity<Resource> getAvatar(
            @PathVariable String storedFileName
    ) {

        Resource resource = fileService.loadFile(storedFileName);

        MediaType mediaType = MediaTypeFactory
                .getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\""
                )
                .body(resource);
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Zdjęcie nie może być puste");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException(
                    "Zdjęcie profilowe nie może być większe niż 5 MB"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Dozwolone są tylko pliki graficzne"
            );
        }
    }

    private void deleteOldLocalAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("/api/users/avatar/")) {
            return;
        }

        String storedFileName = avatarUrl.substring(
                "/api/users/avatar/".length()
        );

        if (!storedFileName.isBlank()) {
            fileService.deleteFile(storedFileName);
        }
    }
}
