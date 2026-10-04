const OFFER_DETAILS_API_BASE_URL = "http://localhost:8080";
const offerDetailsId = new URLSearchParams(window.location.search).get("id");

document.addEventListener("DOMContentLoaded", loadOfferDetails);

async function loadOfferDetails() {
  const root = document.getElementById("offer-details-root");

  if (!root) {
    return;
  }

  if (!offerDetailsId) {
    root.innerHTML = `
      <div class="offer-details-error">
        <h2>Brakuje identyfikatora ogłoszenia.</h2>
      </div>
    `;
    return;
  }

  try {
    const response = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    const offer = await readDetailsResponse(response);

    if (!response.ok) {
      throw new Error(
        offer?.message || offer?.error || "Nie udało się pobrać ogłoszenia.",
      );
    }

    const imagesResponse = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}/images`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    const images = imagesResponse.ok ? await imagesResponse.json() : [];

    renderOfferDetails(root, offer, Array.isArray(images) ? images : []);
    setupOfferGallery(root);
    setupImageLightbox(root);

    // Przycisk wiadomości musi być podpięty dopiero
    // po wyrenderowaniu HTML-a ogłoszenia.
    await setupMessageButton(offer);
  } catch (error) {
    console.error("Błąd pobierania szczegółów ogłoszenia:", error);

    root.innerHTML = `
      <div class="offer-details-error">
        <h2>Nie udało się pobrać ogłoszenia</h2>
        <p>${escapeDetailsHtml(
          error.message || "Spróbuj ponownie później.",
        )}</p>
      </div>
    `;
  }
}

// Slajder zdjęć: strzałki < >, kropki, klawiatura (← →) i przeciągnięcie palcem.
function setupOfferGallery(root) {
  const gallery = root.querySelector(".offer-gallery");

  if (!gallery) {
    return;
  }

  const track = gallery.querySelector(".offer-gallery-track");
  const slides = gallery.querySelectorAll(".offer-gallery-slide");
  const total = slides.length;

  if (total < 2) {
    return;
  }

  const counter = gallery.querySelector(".offer-gallery-counter");
  const dots = gallery.querySelectorAll(".offer-gallery-dot");
  let current = 0;

  function show(index) {
    current = (index + total) % total;

    track.style.transform = `translateX(-${current * 100}%)`;

    counter.textContent = `${current + 1} / ${total}`;

    dots.forEach((dot, dotIndex) => {
      dot.classList.toggle("is-active", dotIndex === current);
    });
  }

  gallery
    .querySelector(".offer-gallery-prev")
    .addEventListener("click", () => show(current - 1));

  gallery
    .querySelector(".offer-gallery-next")
    .addEventListener("click", () => show(current + 1));

  dots.forEach((dot) => {
    dot.addEventListener("click", () => show(Number(dot.dataset.index)));
  });

  gallery.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      show(current - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      show(current + 1);
    }
  });

  let startX = null;

  gallery.addEventListener(
    "touchstart",
    (event) => {
      startX = event.touches[0].clientX;
    },
    { passive: true },
  );

  gallery.addEventListener("touchend", (event) => {
    if (startX === null) {
      return;
    }

    const diff = event.changedTouches[0].clientX - startX;
    startX = null;

    if (Math.abs(diff) > 40) {
      show(diff > 0 ? current - 1 : current + 1);
    }
  });

  // Pozwala podglądowi pełnoekranowemu zsynchronizować slajder.
  gallery.goTo = show;

  show(0);
}

// Pełnoekranowy podgląd zdjęć: powiększanie / pomniejszanie (przyciski +/−,
// kółko myszy, dwuklik, szczypanie palcami), przesuwanie powiększonego
// zdjęcia, strzałki < >, klawisze (Esc, ←, →, +, −, 0).
function setupImageLightbox(root) {
  document.querySelector(".image-lightbox")?.remove();

  const gallery = root.querySelector(".offer-gallery");

  if (!gallery) {
    return;
  }

  const slides = Array.from(gallery.querySelectorAll(".offer-gallery-slide"));

  const sources = slides.map((slide) => {
    const image = slide.querySelector("img");

    return { src: image.src, alt: image.alt };
  });

  const total = sources.length;

  if (!total) {
    return;
  }

  const MIN_SCALE = 1;
  const MAX_SCALE = 5;
  const BUTTON_FACTOR = 1.4;
  const WHEEL_FACTOR = 1.15;

  const overlay = document.createElement("div");

  overlay.className = "image-lightbox";
  overlay.hidden = true;
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");
  overlay.setAttribute("aria-label", "Podgląd zdjęcia");

  overlay.innerHTML = `
    <div class="image-lightbox-stage">
      <img class="image-lightbox-img" alt="" draggable="false" />
    </div>

    <button type="button" class="image-lightbox-close" aria-label="Zamknij podgląd">&times;</button>

    <div class="image-lightbox-counter" aria-live="polite"></div>

    ${
      total > 1
        ? `
          <button type="button" class="image-lightbox-arrow image-lightbox-prev" aria-label="Poprzednie zdjęcie">&#8249;</button>
          <button type="button" class="image-lightbox-arrow image-lightbox-next" aria-label="Następne zdjęcie">&#8250;</button>
        `
        : ""
    }

    <div class="image-lightbox-toolbar">
      <button type="button" data-zoom="out" aria-label="Pomniejsz">&minus;</button>
      <span class="image-lightbox-zoom" aria-live="polite">100%</span>
      <button type="button" data-zoom="in" aria-label="Powiększ">+</button>
      <button type="button" data-zoom="reset" class="image-lightbox-reset">Resetuj</button>
    </div>
  `;

  document.body.appendChild(overlay);

  const stage = overlay.querySelector(".image-lightbox-stage");
  const img = overlay.querySelector(".image-lightbox-img");
  const counter = overlay.querySelector(".image-lightbox-counter");
  const zoomLabel = overlay.querySelector(".image-lightbox-zoom");
  const closeButton = overlay.querySelector(".image-lightbox-close");
  const zoomOutButton = overlay.querySelector('[data-zoom="out"]');
  const zoomInButton = overlay.querySelector('[data-zoom="in"]');

  let index = 0;
  let scale = 1;
  let tx = 0;
  let ty = 0;
  let lastFocused = null;

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function clampPan() {
    const maxX = Math.max(0, (img.offsetWidth * scale - stage.clientWidth) / 2);
    const maxY = Math.max(
      0,
      (img.offsetHeight * scale - stage.clientHeight) / 2,
    );

    tx = clamp(tx, -maxX, maxX);
    ty = clamp(ty, -maxY, maxY);
  }

  function applyTransform() {
    img.style.transform = `translate(${tx}px, ${ty}px) scale(${scale})`;

    zoomLabel.textContent = `${Math.round(scale * 100)}%`;

    stage.classList.toggle("is-zoomed", scale > 1);

    zoomOutButton.disabled = scale <= MIN_SCALE;
    zoomInButton.disabled = scale >= MAX_SCALE;
  }

  // Zmiana powiększenia względem punktu (domyślnie środek ekranu).
  function setScale(newScale, clientX, clientY) {
    newScale = clamp(newScale, MIN_SCALE, MAX_SCALE);

    const rect = stage.getBoundingClientRect();

    const px =
      clientX === undefined ? 0 : clientX - (rect.left + rect.width / 2);
    const py =
      clientY === undefined ? 0 : clientY - (rect.top + rect.height / 2);

    const ratio = newScale / scale;

    tx = px - (px - tx) * ratio;
    ty = py - (py - ty) * ratio;

    scale = newScale;

    if (scale === 1) {
      tx = 0;
      ty = 0;
    }

    clampPan();
    applyTransform();
  }

  function showImage(newIndex) {
    index = (newIndex + total) % total;

    img.src = sources[index].src;
    img.alt = sources[index].alt;

    counter.textContent = total > 1 ? `${index + 1} / ${total}` : "";

    scale = 1;
    tx = 0;
    ty = 0;

    applyTransform();
  }

  function openLightbox(startIndex) {
    lastFocused = document.activeElement;

    overlay.hidden = false;
    document.body.style.overflow = "hidden";

    showImage(startIndex);

    document.addEventListener("keydown", onKeyDown);

    closeButton.focus();
  }

  function closeLightbox() {
    overlay.hidden = true;
    document.body.style.overflow = "";

    document.removeEventListener("keydown", onKeyDown);

    // Slajder na stronie ustawiamy na zdjęcie, które było oglądane.
    if (typeof gallery.goTo === "function") {
      gallery.goTo(index);
    }

    if (lastFocused && typeof lastFocused.focus === "function") {
      lastFocused.focus();
    }
  }

  function onKeyDown(event) {
    switch (event.key) {
      case "Escape":
        closeLightbox();
        break;

      case "ArrowLeft":
        if (total > 1) {
          showImage(index - 1);
        }
        break;

      case "ArrowRight":
        if (total > 1) {
          showImage(index + 1);
        }
        break;

      case "+":
      case "=":
        setScale(scale * BUTTON_FACTOR);
        break;

      case "-":
      case "_":
        setScale(scale / BUTTON_FACTOR);
        break;

      case "0":
        setScale(1);
        break;

      case "Tab": {
        const focusable = Array.from(
          overlay.querySelectorAll("button:not([disabled])"),
        );

        if (!focusable.length) {
          break;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }

        break;
      }

      default:
        return;
    }

    if (event.key !== "Tab") {
      event.preventDefault();
    }
  }

  // Kliknięcie w zdjęcie w slajderze otwiera podgląd.
  gallery.addEventListener("click", (event) => {
    const slide = event.target.closest(".offer-gallery-slide");

    if (!slide) {
      return;
    }

    openLightbox(slides.indexOf(slide));
  });

  closeButton.addEventListener("click", closeLightbox);

  overlay
    .querySelector(".image-lightbox-prev")
    ?.addEventListener("click", () => showImage(index - 1));

  overlay
    .querySelector(".image-lightbox-next")
    ?.addEventListener("click", () => showImage(index + 1));

  overlay.querySelectorAll("[data-zoom]").forEach((button) => {
    button.addEventListener("click", () => {
      const action = button.dataset.zoom;

      if (action === "in") {
        setScale(scale * BUTTON_FACTOR);
      } else if (action === "out") {
        setScale(scale / BUTTON_FACTOR);
      } else {
        setScale(1);
      }
    });
  });

  // Kółko myszy = zoom w miejscu wskaźnika.
  stage.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();

      setScale(
        event.deltaY < 0 ? scale * WHEEL_FACTOR : scale / WHEEL_FACTOR,
        event.clientX,
        event.clientY,
      );
    },
    { passive: false },
  );

  // Dwuklik: powiększ / wróć do 100%.
  stage.addEventListener("dblclick", (event) => {
    if (scale > 1) {
      setScale(1);
    } else {
      setScale(2.5, event.clientX, event.clientY);
    }
  });

  // Przesuwanie powiększonego zdjęcia, szczypanie (2 palce),
  // przeciągnięcie w bok = następne/poprzednie zdjęcie (przy 100%),
  // kliknięcie w tło = zamknięcie.
  const pointers = new Map();

  let dragStart = null;
  let pinchStart = null;
  let moved = false;
  let downOnImage = false;

  stage.addEventListener("pointerdown", (event) => {
    stage.setPointerCapture(event.pointerId);

    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.size === 1) {
      moved = false;
      downOnImage = event.target === img;

      dragStart = { x: event.clientX, y: event.clientY, tx, ty };
    } else if (pointers.size === 2) {
      const [a, b] = Array.from(pointers.values());

      pinchStart = {
        distance: Math.hypot(a.x - b.x, a.y - b.y) || 1,
        scale,
      };

      dragStart = null;
      moved = true;
    }

    stage.classList.add("is-dragging");
  });

  stage.addEventListener("pointermove", (event) => {
    if (!pointers.has(event.pointerId)) {
      return;
    }

    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.size === 2 && pinchStart) {
      const [a, b] = Array.from(pointers.values());

      const distance = Math.hypot(a.x - b.x, a.y - b.y);

      setScale(
        pinchStart.scale * (distance / pinchStart.distance),
        (a.x + b.x) / 2,
        (a.y + b.y) / 2,
      );

      return;
    }

    if (pointers.size === 1 && dragStart) {
      const dx = event.clientX - dragStart.x;
      const dy = event.clientY - dragStart.y;

      if (Math.abs(dx) + Math.abs(dy) > 5) {
        moved = true;
      }

      if (scale > 1) {
        tx = dragStart.tx + dx;
        ty = dragStart.ty + dy;

        clampPan();
        applyTransform();
      }
    }
  });

  function endPointer(event) {
    if (!pointers.has(event.pointerId)) {
      return;
    }

    const wasSingle = pointers.size === 1;

    pointers.delete(event.pointerId);

    if (wasSingle && dragStart && event.type === "pointerup") {
      const dx = event.clientX - dragStart.x;
      const dy = event.clientY - dragStart.y;

      if (!moved && !downOnImage) {
        closeLightbox();
      } else if (
        scale === 1 &&
        total > 1 &&
        Math.abs(dx) > 60 &&
        Math.abs(dx) > Math.abs(dy) * 1.5
      ) {
        showImage(dx > 0 ? index - 1 : index + 1);
      }
    }

    if (pointers.size < 2) {
      pinchStart = null;
    }

    if (pointers.size === 1) {
      const [remaining] = Array.from(pointers.values());

      dragStart = { x: remaining.x, y: remaining.y, tx, ty };
    } else {
      dragStart = null;
    }

    if (pointers.size === 0) {
      stage.classList.remove("is-dragging");
    }
  }

  stage.addEventListener("pointerup", endPointer);
  stage.addEventListener("pointercancel", endPointer);

  window.addEventListener("resize", () => {
    if (!overlay.hidden) {
      clampPan();
      applyTransform();
    }
  });
}

function renderOfferDetails(root, offer, images) {
  const price =
    offer.price == null
      ? "Cena do uzgodnienia"
      : `${formatDetailsPrice(offer.price)} zł`;

  const imageHtml = images.length
    ? `
      <div
        class="offer-gallery"
        tabindex="0"
        role="region"
        aria-roledescription="karuzela"
        aria-label="Zdjęcia ogłoszenia"
      >
        <div class="offer-gallery-track">
          ${images
            .map(
              (image, index) => `
                <div class="offer-gallery-slide">
                  <img
                    src="${OFFER_DETAILS_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}"
                    alt="${escapeDetailsHtml(offer.title)} – zdjęcie ${index + 1} z ${images.length}"
                    draggable="false"
                  />
                </div>
              `,
            )
            .join("")}
        </div>

        ${
          images.length > 1
            ? `
              <button
                type="button"
                class="offer-gallery-arrow offer-gallery-prev"
                aria-label="Poprzednie zdjęcie"
              >&#8249;</button>

              <button
                type="button"
                class="offer-gallery-arrow offer-gallery-next"
                aria-label="Następne zdjęcie"
              >&#8250;</button>

              <div class="offer-gallery-counter" aria-live="polite">
                1 / ${images.length}
              </div>

              <div class="offer-gallery-dots">
                ${images
                  .map(
                    (_, index) => `
                      <button
                        type="button"
                        class="offer-gallery-dot"
                        data-index="${index}"
                        aria-label="Zdjęcie ${index + 1}"
                      ></button>
                    `,
                  )
                  .join("")}
              </div>
            `
            : ""
        }
      </div>
    `
    : `
      <div class="offer-details-no-image">
        <span>${getDetailsIcon(offer.type)}</span>
        <p>Brak zdjęcia</p>
      </div>
    `;

  root.innerHTML = `
    <article class="offer-details-card">
      <div class="offer-details-media">
        ${imageHtml}
      </div>

      <div class="offer-details-content">
        <div class="offer-details-topline">
          <span class="offer-details-category">
            ${escapeDetailsHtml(offer.categoryName || "Bez kategorii")}
          </span>

          <span class="offer-details-type">
            ${escapeDetailsHtml(getDetailsTypeLabel(offer.type))}
          </span>
        </div>

        <h1>${escapeDetailsHtml(offer.title)}</h1>

        <div class="offer-details-price">
          ${escapeDetailsHtml(price)}
        </div>

        <button
          id="send-message-button"
          class="send-message-button"
          type="button"
          style="display: none;"
        >
          Wyślij wiadomość
        </button>

        <div class="offer-details-info-grid">
          <div>
            <span>Lokalizacja</span>
            <strong>
              ${escapeDetailsHtml(offer.location || "Nie podano")}
            </strong>
          </div>

          <div>
            <span>Sprzedający</span>
            <strong>
              ${escapeDetailsHtml(offer.nickname || "Użytkownik")}
            </strong>
          </div>

          <div>
            <span>Dodano</span>
            <strong>
              ${escapeDetailsHtml(formatDetailsDate(offer.createdAt))}
            </strong>
          </div>
        </div>

        <section class="offer-details-description">
          <h2>Opis</h2>

          <p>
            ${escapeDetailsHtml(offer.description || "Brak opisu.")}
          </p>
        </section>
      </div>
    </article>
  `;
}

async function setupMessageButton(offer) {
  const button = document.getElementById("send-message-button");

  if (!button || !offer) {
    return;
  }

  try {
    const response = await fetch(`${OFFER_DETAILS_API_BASE_URL}/api/users/me`, {
      method: "GET",
      credentials: "include",
    });

    // Niezalogowany użytkownik nie dostaje przycisku.
    if (!response.ok) {
      button.style.display = "none";
      return;
    }

    const currentUser = await response.json();

    if (!currentUser) {
      button.style.display = "none";
      return;
    }

    /*
     * WŁAŚCICIEL OGŁOSZENIA
     *
     * Jeżeli użytkownik ogląda własne ogłoszenie,
     * pokazujemy "Chat".
     */
    if (Number(currentUser.id) === Number(offer.userId)) {
      button.style.display = "block";
      button.textContent = "Chat";

      button.addEventListener("click", async () => {
        button.disabled = true;
        button.textContent = "Otwieranie rozmowy...";

        try {
          const conversationsResponse = await fetch(
            `${OFFER_DETAILS_API_BASE_URL}/api/conversations`,
            {
              method: "GET",
              credentials: "include",
            },
          );

          if (!conversationsResponse.ok) {
            throw new Error(
              `Nie udało się pobrać rozmów. HTTP ${conversationsResponse.status}`,
            );
          }

          const conversations = await conversationsResponse.json();

          /*
           * Szukamy rozmowy powiązanej z konkretnym
           * ogłoszeniem.
           */
          const offerConversations = Array.isArray(conversations)
            ? conversations.filter(
                (item) => Number(item.offerId) === Number(offer.id),
              )
            : [];

          // Jeżeli właściciel ma kilka rozmów dotyczących tego ogłoszenia,
          // otwieramy tę ostatnio aktualizowaną.
          const conversation = offerConversations
            .slice()
            .sort((first, second) => {
              const firstDate = new Date(
                first.updatedAt || first.createdAt || 0,
              ).getTime();

              const secondDate = new Date(
                second.updatedAt || second.createdAt || 0,
              ).getTime();

              return secondDate - firstDate;
            })[0];

          if (!conversation || !conversation.id) {
            alert("Nie ma jeszcze żadnej rozmowy dotyczącej tego ogłoszenia.");

            button.disabled = false;
            button.textContent = "Chat";
            return;
          }

          window.location.href =
            `chat.html?conversationId=${encodeURIComponent(conversation.id)}` +
            `&offerId=${encodeURIComponent(offer.id)}`;
        } catch (error) {
          console.error("Błąd otwierania rozmowy:", error);

          alert(error.message || "Nie udało się otworzyć rozmowy.");

          button.disabled = false;
          button.textContent = "Chat";
        }
      });

      return;
    }

    /*
     * CUDZE OGŁOSZENIE
     *
     * Pokazujemy "Wyślij wiadomość".
     */
    button.style.display = "block";
    button.textContent = "Wyślij wiadomość";

    button.addEventListener("click", async () => {
      button.disabled = true;
      button.textContent = "Otwieranie rozmowy...";

      try {
        const conversationResponse = await fetch(
          `${OFFER_DETAILS_API_BASE_URL}/api/conversations`,
          {
            method: "POST",
            credentials: "include",
            headers: {
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              otherUserId: offer.userId,
              offerId: offer.id,
            }),
          },
        );

        if (!conversationResponse.ok) {
          let errorMessage = `HTTP ${conversationResponse.status}`;

          try {
            const errorData = await conversationResponse.json();

            if (errorData.message) {
              errorMessage = errorData.message;
            }
          } catch (error) {
            // Backend nie zwrócił JSON-a.
          }

          throw new Error(errorMessage);
        }

        const conversation = await conversationResponse.json();

        if (!conversation || !conversation.id) {
          throw new Error("Backend nie zwrócił ID rozmowy.");
        }

        window.location.href =
          `chat.html?conversationId=${encodeURIComponent(conversation.id)}` +
          `&offerId=${encodeURIComponent(offer.id)}`;
      } catch (error) {
        console.error("Błąd otwierania rozmowy:", error);

        alert(error.message || "Nie udało się otworzyć rozmowy.");

        button.disabled = false;
        button.textContent = "Wyślij wiadomość";
      }
    });
  } catch (error) {
    console.error("Błąd sprawdzania zalogowanego użytkownika:", error);

    button.style.display = "none";
  }
}

function getDetailsIcon(type) {
  return (
    {
      SALE: "🛍️",
      SERVICE: "🛠️",
      JOB: "💼",
    }[type] || "📦"
  );
}

function getDetailsTypeLabel(type) {
  return (
    {
      SALE: "Sprzedaż",
      SERVICE: "Usługa",
      JOB: "Oferta pracy",
    }[type] ||
    type ||
    "Ogłoszenie"
  );
}

function formatDetailsPrice(value) {
  return new Intl.NumberFormat("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function formatDetailsDate(value) {
  if (!value) {
    return "brak daty";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? "brak daty"
    : date.toLocaleDateString("pl-PL");
}

async function readDetailsResponse(response) {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return {
      message: text,
    };
  }
}

function escapeDetailsHtml(value) {
  const div = document.createElement("div");

  div.textContent = value ?? "";

  return div.innerHTML;
}

window.addEventListener("pageshow", (event) => {
  if (!event.persisted) {
    return;
  }

  window.location.reload();
});
