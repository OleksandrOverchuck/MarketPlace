package pl.jollycart.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.jollycart.file.CvFileService;
import pl.jollycart.job.dto.CreateJobApplicationRequest;
import pl.jollycart.job.dto.JobApplicationResponse;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;
import pl.jollycart.offer.OfferType;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CvFileService cvFileService;

    private JobApplicationService jobApplicationService;

    @BeforeEach
    void setUp() {
        jobApplicationService = new JobApplicationService(
                jobApplicationRepository,
                offerRepository,
                userRepository,
                cvFileService
        );
    }

    @Test
    void shouldCreateJobApplication() {
        User applicant = new User();
        applicant.setId(5L);
        applicant.setEmail("applicant@example.com");
        applicant.setNickname("Applicant");

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(3L);
        offer.setTitle("Programista Java");
        offer.setType(OfferType.JOB);
        offer.setUser(owner);

        CreateJobApplicationRequest request =
                new CreateJobApplicationRequest("Jestem zainteresowany.");

        when(userRepository.findByEmail("applicant@example.com"))
                .thenReturn(Optional.of(applicant));
        when(offerRepository.findById(3L))
                .thenReturn(Optional.of(offer));
        when(jobApplicationRepository.existsByOfferIdAndUserId(3L, 5L))
                .thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class)))
                .thenAnswer(invocation -> {
                    JobApplication application =
                            invocation.getArgument(0);
                    application.setId(1L);
                    return application;
                });

        JobApplicationResponse response =
                jobApplicationService.createApplication(
                        3L,
                        "applicant@example.com",
                        request
                );

        assertEquals(1L, response.id());
        assertEquals(3L, response.offerId());
        assertEquals("Programista Java", response.offerTitle());
        assertEquals(5L, response.userId());
        assertEquals(JobApplicationStatus.PENDING, response.status());
        assertEquals(
                "Jestem zainteresowany.",
                response.message()
        );
    }

    @Test
    void shouldRejectApplicationForNonJobOffer() {
        User applicant = new User();
        applicant.setId(5L);
        applicant.setEmail("applicant@example.com");

        Offer offer = new Offer();
        offer.setId(3L);
        offer.setType(OfferType.SALE);

        when(userRepository.findByEmail("applicant@example.com"))
                .thenReturn(Optional.of(applicant));
        when(offerRepository.findById(3L))
                .thenReturn(Optional.of(offer));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> jobApplicationService.createApplication(
                                3L,
                                "applicant@example.com",
                                new CreateJobApplicationRequest(null)
                        )
                );

        assertEquals(
                "Można aplikować tylko na oferty typu JOB",
                exception.getMessage()
        );
        verify(jobApplicationRepository, never())
                .save(any(JobApplication.class));
    }

    @Test
    void shouldRejectApplicationToOwnOffer() {
        User owner = new User();
        owner.setId(5L);
        owner.setEmail("owner@example.com");

        Offer offer = new Offer();
        offer.setId(3L);
        offer.setType(OfferType.JOB);
        offer.setUser(owner);

        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(owner));
        when(offerRepository.findById(3L))
                .thenReturn(Optional.of(offer));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> jobApplicationService.createApplication(
                                3L,
                                "owner@example.com",
                                new CreateJobApplicationRequest(null)
                        )
                );

        assertEquals(
                "Nie możesz aplikować na własną ofertę",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateApplication() {
        User applicant = new User();
        applicant.setId(5L);
        applicant.setEmail("applicant@example.com");

        User owner = new User();
        owner.setId(1L);

        Offer offer = new Offer();
        offer.setId(3L);
        offer.setType(OfferType.JOB);
        offer.setUser(owner);

        when(userRepository.findByEmail("applicant@example.com"))
                .thenReturn(Optional.of(applicant));
        when(offerRepository.findById(3L))
                .thenReturn(Optional.of(offer));
        when(jobApplicationRepository.existsByOfferIdAndUserId(3L, 5L))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> jobApplicationService.createApplication(
                                3L,
                                "applicant@example.com",
                                new CreateJobApplicationRequest(null)
                        )
                );

        assertEquals(
                "Już aplikowałeś na tę ofertę",
                exception.getMessage()
        );
        verify(jobApplicationRepository, never())
                .save(any(JobApplication.class));
    }
}
