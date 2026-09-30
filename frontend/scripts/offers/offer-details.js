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
        offer?.message ||
          offer?.error ||
          "Nie udało się pobrać ogłoszenia.",
      );
    }

    const imagesResponse = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}/images`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    const images = imagesResponse.ok
      ? await imagesResponse.json()
      : [];

    renderOfferDetails(
      root,
      offer,
      Array.isArray(images) ? images : [],
    );

    // Przycisk wiadomości musi być podpięty dopiero
    // po wyrenderowaniu HTML-a ogłoszenia.
    await setupMessageButton(offer);
  } catch (error) {
    console.error(
      "Błąd pobierania szczegółów ogłoszenia:",
      error,
    );

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

function renderOfferDetails(root, offer, images) {
  const price =
    offer.price == null
      ? "Cena do uzgodnienia"
      : `${formatDetailsPrice(offer.price)} zł`;

  const imageHtml = images.length
    ? `
      <div class="offer-details-gallery">
        ${images
          .map(
            (image) => `
              <img
                src="${OFFER_DETAILS_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}"
                alt="${escapeDetailsHtml(offer.title)}"
              />
            `,
          )
          .join("")}
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
            ${escapeDetailsHtml(
              offer.categoryName || "Bez kategorii",
            )}
          </span>

          <span class="offer-details-type">
            ${escapeDetailsHtml(
              getDetailsTypeLabel(offer.type),
            )}
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
              ${escapeDetailsHtml(
                offer.location || "Nie podano",
              )}
            </strong>
          </div>

          <div>
            <span>Sprzedający</span>
            <strong>
              ${escapeDetailsHtml(
                offer.nickname || "Użytkownik",
              )}
            </strong>
          </div>

          <div>
            <span>Dodano</span>
            <strong>
              ${escapeDetailsHtml(
                formatDetailsDate(offer.createdAt),
              )}
            </strong>
          </div>
        </div>

        <section class="offer-details-description">
          <h2>Opis</h2>

          <p>
            ${escapeDetailsHtml(
              offer.description || "Brak opisu.",
            )}
          </p>
        </section>
      </div>
    </article>
  `;
}

async function setupMessageButton(offer) {
  const button = document.getElementById(
    "send-message-button",
  );

  if (!button || !offer) {
    return;
  }

  try {
    const response = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/users/me`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    // Niezalogowany użytkownik nie dostaje przycisku.
    if (!response.ok) {
      button.style.display = "none";
      return;
    }

    const currentUser = await response.json();

    // Nie można wysłać wiadomości do samego siebie.
    if (
      !currentUser ||
      Number(currentUser.id) === Number(offer.userId)
    ) {
      button.style.display = "none";
      return;
    }

    button.style.display = "block";

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
            }),
          },
        );

        if (!conversationResponse.ok) {
          let errorMessage = `HTTP ${conversationResponse.status}`;

          try {
            const errorData =
              await conversationResponse.json();

            if (errorData.message) {
              errorMessage = errorData.message;
            }
          } catch (error) {
            // Backend nie zwrócił JSON-a.
          }

          throw new Error(errorMessage);
        }

        const conversation =
          await conversationResponse.json();

        if (!conversation || !conversation.id) {
          throw new Error(
            "Backend nie zwrócił ID rozmowy.",
          );
        }

        window.location.href =
          `chat.html?conversationId=${encodeURIComponent(
            conversation.id,
          )}&offerId=${encodeURIComponent(offer.id)}`;
      } catch (error) {
        console.error(
          "Błąd otwierania rozmowy:",
          error,
        );

        alert(
          error.message ||
            "Nie udało się otworzyć rozmowy.",
        );

        button.disabled = false;
        button.textContent = "Wyślij wiadomość";
      }
    });
  } catch (error) {
    console.error(
      "Błąd sprawdzania zalogowanego użytkownika:",
      error,
    );

    button.style.display = "none";
  }
}

function getDetailsIcon(type) {
  return {
    SALE: "🛍️",
    SERVICE: "🛠️",
    JOB: "💼",
  }[type] || "📦";
}

function getDetailsTypeLabel(type) {
  return {
    SALE: "Sprzedaż",
    SERVICE: "Usługa",
    JOB: "Oferta pracy",
  }[type] || type || "Ogłoszenie";
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
