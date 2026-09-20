package pl.jollycart.job;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.jollycart.job.dto.CreateJobApplicationRequest;
import pl.jollycart.job.dto.JobApplicationResponse;

@RestController
@RequestMapping("/api/job-applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    public JobApplicationController(
            JobApplicationService jobApplicationService
    ) {
        this.jobApplicationService = jobApplicationService;
    }

    @PostMapping("/offers/{offerId}")
    public ResponseEntity<JobApplicationResponse> createApplication(
            @PathVariable Long offerId,
            Authentication authentication,
            @Valid @RequestBody CreateJobApplicationRequest request
    ) {
        String currentEmail = authentication.getName();

        JobApplicationResponse response =
                jobApplicationService.createApplication(
                        offerId,
                        currentEmail,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<JobApplicationResponse>> getMyApplications(
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        return ResponseEntity.ok(
                jobApplicationService.getMyApplications(
                        currentEmail
                )
        );
    }

    @GetMapping("/offers/{offerId}")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForOffer(
            @PathVariable Long offerId,
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        return ResponseEntity.ok(
                jobApplicationService.getApplicationsForOffer(
                        offerId,
                        currentEmail
                )
        );
    }
}