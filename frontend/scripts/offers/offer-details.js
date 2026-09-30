const OFFER_DETAILS_API_BASE_URL = "http://localhost:8080";
const offerDetailsId = new URLSearchParams(window.location.search).get("id");

document.addEventListener("DOMContentLoaded", loadOfferDetails);

async function loadOfferDetails() {
  const root = document.getElementById("offer-details-root");
  if (!root) return;

  if (!offerDetailsId) {
    root.innerHTML = `<div class="offer-details-error"><h2>Brakuje identyfikatora ogłoszenia.</h2></div>`;
    return;
  }

  try {
    const response = await fetch(`${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}`);
    const offer = await readDetailsResponse(response);

    if (!response.ok) {
      throw new Error(offer?.message || offer?.error || "Nie udało się pobrać ogłoszenia.");
    }

    const imagesResponse = await fetch(`${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}/images`);
    const images = imagesResponse.ok ? await imagesResponse.json() : [];

    renderOfferDetails(root, offer, Array.isArray(images) ? images : []);
  } catch (error) {
    console.error("Błąd pobierania szczegółów ogłoszenia:", error);
    root.innerHTML = `
      <div class="offer-details-error">
        <h2>Nie udało się pobrać ogłoszenia</h2>
        <p>${escapeDetailsHtml(error.message || "Spróbuj ponownie później.")}</p>
      </div>
    `;
  }
}

function renderOfferDetails(root, offer, images) {
  const price = offer.price == null ? "Cena do uzgodnienia" : `${formatDetailsPrice(offer.price)} zł`;
  const imageHtml = images.length
    ? `<div class="offer-details-gallery">${images.map((image) => `
        <img src="${OFFER_DETAILS_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}" alt="${escapeDetailsHtml(offer.title)}" />
      `).join("")}</div>`
    : `<div class="offer-details-no-image"><span>${getDetailsIcon(offer.type)}</span><p>Brak zdjęcia</p></div>`;

  root.innerHTML = `
    <article class="offer-details-card">
      <div class="offer-details-media">${imageHtml}</div>
      <div class="offer-details-content">
        <div class="offer-details-topline">
          <span class="offer-details-category">${escapeDetailsHtml(offer.categoryName || "Bez kategorii")}</span>
          <span class="offer-details-type">${escapeDetailsHtml(getDetailsTypeLabel(offer.type))}</span>
        </div>
        <h1>${escapeDetailsHtml(offer.title)}</h1>
        <div class="offer-details-price">${escapeDetailsHtml(price)}</div>
        <div class="offer-details-info-grid">
          <div><span>Lokalizacja</span><strong>${escapeDetailsHtml(offer.location || "Nie podano")}</strong></div>
          <div><span>Sprzedający</span><strong>${escapeDetailsHtml(offer.nickname || "Użytkownik")}</strong></div>
          <div><span>Dodano</span><strong>${escapeDetailsHtml(formatDetailsDate(offer.createdAt))}</strong></div>
        </div>
        <section class="offer-details-description">
          <h2>Opis</h2>
          <p>${escapeDetailsHtml(offer.description || "Brak opisu.")}</p>
        </section>
      </div>
    </article>
  `;
}

function getDetailsIcon(type) {
  return { SALE: "🛍️", SERVICE: "🛠️", JOB: "💼" }[type] || "📦";
}

function getDetailsTypeLabel(type) {
  return { SALE: "Sprzedaż", SERVICE: "Usługa", JOB: "Oferta pracy" }[type] || type || "Ogłoszenie";
}

function formatDetailsPrice(value) {
  return new Intl.NumberFormat("pl-PL", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value));
}

function formatDetailsDate(value) {
  if (!value) return "brak daty";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "brak daty" : date.toLocaleDateString("pl-PL");
}

async function readDetailsResponse(response) {
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function escapeDetailsHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}
