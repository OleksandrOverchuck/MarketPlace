package pl.jollycart.job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jollycart.job.dto.CreateJobApplicationRequest;
import pl.jollycart.job.dto.JobApplicationResponse;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;
import pl.jollycart.offer.OfferType;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

import java.util.List;

@Service
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository,
            OfferRepository offerRepository,
            UserRepository userRepository
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
    }

    public JobApplicationResponse createApplication(
            Long offerId,
            String currentEmail,
            CreateJobApplicationRequest request
    ) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        if (offer.getType() != OfferType.JOB) {
            throw new IllegalArgumentException(
                    "Można aplikować tylko na oferty typu JOB"
            );
        }

        if (offer.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "Nie możesz aplikować na własną ofertę"
            );
        }

        if (jobApplicationRepository.existsByOfferIdAndUserId(
                offerId,
                user.getId()
        )) {
            throw new IllegalArgumentException(
                    "Już aplikowałeś na tę ofertę"
            );
        }

        JobApplication application = new JobApplication();

        application.setOffer(offer);
        application.setUser(user);
        application.setStatus(JobApplicationStatus.PENDING);
        application.setMessage(request.message());

        JobApplication savedApplication =
                jobApplicationRepository.save(application);

        return JobApplicationResponse.from(savedApplication);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getMyApplications(
            String currentEmail
    ) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        return jobApplicationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(JobApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsForOffer(
            Long offerId,
            String currentEmail
    ) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        if (!offer.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException(
                    "Nie możesz przeglądać aplikacji do cudzej oferty"
            );
        }

        return jobApplicationRepository
                .findByOfferIdOrderByCreatedAtDesc(offerId)
                .stream()
                .map(JobApplicationResponse::from)
                .toList();
    }
}