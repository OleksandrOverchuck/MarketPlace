package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.jollycart.category.Category;
import pl.jollycart.category.CategoryRepository;
import pl.jollycart.offer.dto.CreateOfferRequest;
import pl.jollycart.offer.dto.OfferResponse;
import pl.jollycart.offer.dto.UpdateOfferRequest;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private OfferService offerService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService(
                offerRepository,
                userRepository,
                categoryRepository
        );
    }

    @Test
    void shouldCreateOffer() {

        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setNickname("TestUser");

        Category category = new Category();
        category.setId(1L);
        category.setName("Elektronika");

        CreateOfferRequest request = new CreateOfferRequest(
                "Telefon Samsung",
                "Telefon w bardzo dobrym stanie",
                new BigDecimal("2500.00"),
                OfferType.SALE,
                1L
        );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(offerRepository.save(any(Offer.class)))
                .thenAnswer(invocation -> {
                    Offer offer = invocation.getArgument(0);
                    offer.setId(1L);
                    return offer;
                });

        OfferResponse response =
                offerService.createOffer(
                        "test@example.com",
                        request
                );

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Telefon Samsung", response.title());
        assertEquals(
                new BigDecimal("2500.00"),
                response.price()
        );
        assertEquals(OfferType.SALE, response.type());
        assertEquals(OfferStatus.ACTIVE, response.status());
        assertEquals(1L, response.userId());
        assertEquals("TestUser", response.nickname());
        assertEquals(1L, response.categoryId());
        assertEquals("Elektronika", response.categoryName());

        verify(offerRepository).save(any(Offer.class));
    }

    @Test
    void shouldRejectCreatingOfferWhenUserDoesNotExist() {

        CreateOfferRequest request = new CreateOfferRequest(
                "Telefon Samsung",
                "Telefon w bardzo dobrym stanie",
                new BigDecimal("2500.00"),
                OfferType.SALE,
                1L
        );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> offerService.createOffer(
                                "test@example.com",
                                request
                        )
                );

        assertEquals(
                "Użytkownik nie został znaleziony",
                exception.getMessage()
        );

        verify(offerRepository, never())
                .save(any(Offer.class));
    }

    @Test
    void shouldRejectUpdatingOfferOwnedByAnotherUser() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setNickname("Owner");

        Offer offer = new Offer();
        offer.setId(1L);
        offer.setUser(owner);

        UpdateOfferRequest request = new UpdateOfferRequest(
                "Zmieniony tytuł",
                "Zmieniony opis",
                new BigDecimal("2000.00"),
                OfferType.SALE,
                OfferStatus.ACTIVE,
                1L
        );

        when(offerRepository.findById(1L))
                .thenReturn(Optional.of(offer));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> offerService.updateOffer(
                                1L,
                                "other@example.com",
                                request
                        )
                );

        assertEquals(
                "Nie możesz edytować cudzej oferty",
                exception.getMessage()
        );

        verify(offerRepository, never())
                .save(any(Offer.class));

        verify(categoryRepository, never())
                .findById(anyLong());
    }

    @Test
    void shouldUpdateOwnOffer() {

        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setNickname("Owner");

        Category oldCategory = new Category();
        oldCategory.setId(1L);
        oldCategory.setName("Elektronika");

        Category newCategory = new Category();
        newCategory.setId(2L);
        newCategory.setName("Motoryzacja");

        Offer offer = new Offer();
        offer.setId(1L);
        offer.setTitle("Stary tytuł");
        offer.setDescription("Stary opis");
        offer.setPrice(new BigDecimal("1000.00"));
        offer.setType(OfferType.SALE);
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setUser(owner);
        offer.setCategory(oldCategory);

        UpdateOfferRequest request = new UpdateOfferRequest(
                "Nowy tytuł",
                "Nowy opis",
                new BigDecimal("1500.00"),
                OfferType.SERVICE,
                OfferStatus.ACTIVE,
                2L
        );

        when(offerRepository.findById(1L))
                .thenReturn(Optional.of(offer));

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(newCategory));

        when(offerRepository.save(any(Offer.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        OfferResponse response =
                offerService.updateOffer(
                        1L,
                        "owner@example.com",
                        request
                );

        assertNotNull(response);
        assertEquals("Nowy tytuł", response.title());
        assertEquals("Nowy opis", response.description());
        assertEquals(
                new BigDecimal("1500.00"),
                response.price()
        );
        assertEquals(OfferType.SERVICE, response.type());
        assertEquals(OfferStatus.ACTIVE, response.status());
        assertEquals(2L, response.categoryId());
        assertEquals("Motoryzacja", response.categoryName());

        verify(offerRepository).save(offer);
    }
}