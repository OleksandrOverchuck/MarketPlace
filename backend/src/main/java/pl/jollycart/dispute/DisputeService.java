package pl.jollycart.dispute;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pl.jollycart.dispute.DisputeDtos.DisputeDetail;
import pl.jollycart.dispute.DisputeDtos.DisputeImageInfo;
import pl.jollycart.dispute.DisputeDtos.DisputeSummary;
import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.payment.OrderRepository;
import pl.jollycart.payment.OrderResponse;
import pl.jollycart.payment.OrderService;
import pl.jollycart.payment.OrderStatus;
import pl.jollycart.payment.PurchaseOrder;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/*
 * Zgłoszenia problemów z zamówieniami.
 *
 *  - kupujący otwiera zgłoszenie (tytuł + opis + zdjęcia) -> zamówienie DISPUTED,
 *  - administrator / moderator widzi je w panelu ("Aktualne") i je rozstrzyga:
 *      REFUND_BUYER  -> zwrot pieniędzy kupującemu,
 *      PAY_SELLER    -> wypłata środków sprzedawcy,
 *    po czym zgłoszenie trafia do "Rozwiązane".
 *
 * Same pieniądze obsługuje OrderService (Stripe) - tu tylko dokładamy treść
 * zgłoszenia, zdjęcia i historię decyzji.
 */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    static final int MAX_IMAGES = 5;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final int MAX_TITLE = 150;
    static final int MAX_DESCRIPTION = 2000;
    static final int MAX_NOTE = 500;

    private final OrderDisputeRepository disputeRepository;
    private final DisputeImageRepository imageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;

    public DisputeService(
            OrderDisputeRepository disputeRepository,
            DisputeImageRepository imageRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderService orderService,
            FileService fileService,
            ImageFileValidator imageFileValidator
    ) {
        this.disputeRepository = disputeRepository;
        this.imageRepository = imageRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.fileService = fileService;
        this.imageFileValidator = imageFileValidator;
    }

    // =====================================================================
    //  KUPUJĄCY: otwarcie zgłoszenia
    // =====================================================================

    @Transactional
    public OrderResponse openDispute(
            String buyerEmail,
            Long orderId,
            String title,
            String description,
            List<MultipartFile> files
    ) {

        String cleanTitle = title == null ? "" : title.trim();
        String cleanDescription = description == null ? "" : description.trim();

        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("Podaj tytuł zgłoszenia");
        }

        if (cleanTitle.length() > MAX_TITLE) {
            throw new IllegalArgumentException(
                    "Tytuł może mieć najwyżej " + MAX_TITLE + " znaków");
        }

        if (cleanDescription.isEmpty()) {
            throw new IllegalArgumentException("Opisz, jaki jest problem");
        }

        if (cleanDescription.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException(
                    "Opis może mieć najwyżej " + MAX_DESCRIPTION + " znaków");
        }

        List<MultipartFile> images = new ArrayList<>();

        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    images.add(file);
                }
            }
        }

        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "Możesz dodać najwyżej " + MAX_IMAGES + " zdjęć");
        }

        // Walidacja zdjęć przed jakąkolwiek zmianą (typ, rozmiar, sygnatura pliku)
        for (MultipartFile image : images) {
            imageFileValidator.validate(image, MAX_IMAGE_BYTES);
        }

        // Zmiana statusu zamówienia na DISPUTED (sprawdza właściciela i termin).
        // W "powodzie" zamówienia zostaje sam tytuł zgłoszenia.
        OrderResponse response = orderService.openDispute(
                buyerEmail, orderId, cleanTitle);

        User buyer = findUser(buyerEmail);

        OrderDispute dispute = new OrderDispute();

        dispute.setOrderId(orderId);
        dispute.setReporterId(buyer.getId());
        dispute.setTitle(cleanTitle);
        dispute.setDescription(cleanDescription);
        dispute.setStatus(DisputeStatus.OPEN);

        dispute = disputeRepository.save(dispute);

        List<String> storedNames = new ArrayList<>();

        try {
            for (MultipartFile image : images) {
                String storedName = fileService.storeFile(image);

                storedNames.add(storedName);

                DisputeImage entity = new DisputeImage();

                entity.setDisputeId(dispute.getId());
                entity.setFileName(safeFileName(image.getOriginalFilename()));
                entity.setStoredFileName(storedName);
                entity.setContentType(image.getContentType());
                entity.setFileSize(image.getSize());

                imageRepository.save(entity);
            }
        } catch (RuntimeException e) {
            // Transakcja zostanie cofnięta - sprzątamy już zapisane pliki.
            for (String storedName : storedNames) {
                try {
                    fileService.deleteFile(storedName);
                } catch (RuntimeException cleanupError) {
                    log.warn("Nie udało się usunąć pliku {} po błędzie zgłoszenia",
                            storedName, cleanupError);
                }
            }

            throw e;
        }

        return response;
    }

    // =====================================================================
    //  ADMIN / MODERATOR: lista, podgląd, decyzja
    // =====================================================================

    @Transactional
    public List<DisputeSummary> list(DisputeStatus status) {

        DisputeStatus safeStatus = status == null ? DisputeStatus.OPEN : status;

        List<OrderDispute> rows;

        if (safeStatus == DisputeStatus.OPEN) {
            backfillLegacyDisputes();
            rows = disputeRepository.findByStatusOrderByCreatedAtAsc(DisputeStatus.OPEN);
        } else {
            rows = disputeRepository
                    .findTop100ByStatusOrderByResolvedAtDesc(DisputeStatus.RESOLVED);
        }

        return rows.stream().map(this::toSummary).toList();
    }

    @Transactional
    public long countOpen() {
        backfillLegacyDisputes();

        return disputeRepository.countByStatus(DisputeStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public DisputeDetail getDetail(Long id) {

        OrderDispute dispute = findDispute(id);
        PurchaseOrder order = findOrder(dispute.getOrderId());

        List<DisputeImageInfo> images = imageRepository
                .findByDisputeIdOrderByIdAsc(dispute.getId())
                .stream()
                .map(i -> new DisputeImageInfo(
                        i.getId(), i.getFileName(), i.getContentType(), i.getFileSize()))
                .toList();

        return new DisputeDetail(
                dispute.getId(),
                order.getId(),
                order.getOfferId(),
                order.getOfferTitle(),
                dispute.getTitle(),
                dispute.getDescription(),
                dispute.getStatus(),
                dispute.getResolution(),
                dispute.getResolverNote(),
                dispute.getResolverNickname(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt(),
                order.getBuyerId(),
                nickname(order.getBuyerId()),
                order.getSellerId(),
                nickname(order.getSellerId()),
                order.getStatus(),
                order.getTotalAmount(),
                order.getSellerPayout(),
                order.getCurrency(),
                images
        );
    }

    @Transactional(readOnly = true)
    public DisputeImage getImage(Long disputeId, Long imageId) {
        return imageRepository.findByIdAndDisputeId(imageId, disputeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Zdjęcie nie zostało znalezione"));
    }

    public Resource loadImageFile(DisputeImage image) {
        return fileService.loadFile(image.getStoredFileName());
    }

    @Transactional
    public DisputeDetail resolve(
            String staffEmail,
            Long disputeId,
            DisputeResolution resolution,
            String note
    ) {

        if (resolution == null) {
            throw new IllegalArgumentException("Wybierz decyzję");
        }

        User staff = findUser(staffEmail);

        OrderDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Zgłoszenie nie zostało znalezione"));

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new IllegalStateException("To zgłoszenie zostało już rozwiązane");
        }

        PurchaseOrder order = findOrder(dispute.getOrderId());

        // Nie rozstrzygamy sporu, w którym ktoś jest stroną (np. moderator
        // kupił coś od siebie z drugiego konta albo od znajomego).
        if (staff.getId().equals(order.getBuyerId())
                || staff.getId().equals(order.getSellerId())) {
            throw new IllegalStateException(
                    "Nie możesz rozstrzygnąć sporu, w którym jesteś stroną zamówienia");
        }

        String cleanNote = note == null ? "" : note.trim();

        if (cleanNote.length() > MAX_NOTE) {
            throw new IllegalArgumentException(
                    "Uzasadnienie może mieć najwyżej " + MAX_NOTE + " znaków");
        }

        // Ruch pieniędzy (Stripe). Wyjątek cofa całą transakcję,
        // więc zgłoszenie zostaje "Aktualne".
        orderService.resolveDispute(
                order.getId(),
                resolution == DisputeResolution.REFUND_BUYER
        );

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolverId(staff.getId());
        dispute.setResolverNickname(staff.getNickname());
        dispute.setResolverNote(cleanNote.isEmpty() ? null : cleanNote);
        dispute.setResolvedAt(java.time.LocalDateTime.now());

        // Obie strony widzą nowość w "Zamówieniach" (czerwona kropka).
        PurchaseOrder refreshed = findOrder(order.getId());
        refreshed.setBuyerUnread(true);
        refreshed.setSellerUnread(true);

        return getDetail(disputeId);
    }

    // =====================================================================
    //  POMOCNICZE
    // =====================================================================

    /*
     * Zamówienia zgłoszone, zanim pojawiły się zgłoszenia z tytułem i zdjęciami,
     * mają tylko status DISPUTED i krótki powód. Dokładamy dla nich zgłoszenie,
     * żeby nie zginęły z panelu.
     */
    private void backfillLegacyDisputes() {

        for (PurchaseOrder order : orderRepository
                .findByStatusOrderByUpdatedAtAsc(OrderStatus.DISPUTED)) {

            if (disputeRepository.existsByOrderId(order.getId())) {
                continue;
            }

            OrderDispute dispute = new OrderDispute();

            String reason = order.getDisputeReason();

            dispute.setOrderId(order.getId());
            dispute.setReporterId(order.getBuyerId());
            dispute.setTitle("Zgłoszenie problemu");
            dispute.setDescription(
                    reason == null || reason.isBlank() ? "(brak opisu)" : reason);
            dispute.setStatus(DisputeStatus.OPEN);
            dispute.setCreatedAt(order.getUpdatedAt());

            disputeRepository.save(dispute);
        }
    }

    private DisputeSummary toSummary(OrderDispute d) {

        PurchaseOrder order = findOrder(d.getOrderId());

        return new DisputeSummary(
                d.getId(),
                order.getId(),
                order.getOfferId(),
                order.getOfferTitle(),
                d.getTitle(),
                d.getStatus(),
                d.getResolution(),
                nickname(order.getBuyerId()),
                nickname(order.getSellerId()),
                (int) imageRepository.countByDisputeId(d.getId()),
                d.getCreatedAt(),
                d.getResolvedAt(),
                d.getResolverNickname()
        );
    }

    private OrderDispute findDispute(Long id) {
        return disputeRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Zgłoszenie nie zostało znalezione"));
    }

    private PurchaseOrder findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Zamówienie nie zostało znalezione"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Użytkownik nie został znaleziony"));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId)
                .map(User::getNickname)
                .orElse("(usunięty użytkownik)");
    }

    // Nazwa pliku trafia do bazy (255 znaków) i do nagłówka - bez ścieżek.
    private String safeFileName(String original) {

        if (original == null || original.isBlank()) {
            return "zdjecie";
        }

        String name = original.replace('\\', '/');

        name = name.substring(name.lastIndexOf('/') + 1).trim();

        if (name.isEmpty()) {
            return "zdjecie";
        }

        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
