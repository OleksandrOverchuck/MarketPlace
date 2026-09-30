const MY_OFFERS_API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadMyOffers();
});

async function loadMyOffers() {
  const grid = document.getElementById("my-offers-grid");
  const message = document.getElementById("my-offers-message");

  if (!grid) return;

  try {
    const response = await fetch(`${MY_OFFERS_API_BASE_URL}/api/offers/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    const data = await readMyOffersResponse(response);

    if (!response.ok) {
      throw new Error(data?.message || data?.error || "Nie udało się pobrać ogłoszeń.");
    }

    if (!Array.isArray(data) || data.length === 0) {
      grid.innerHTML = `
        <div class="my-offers-empty">
          <h2>Nie masz jeszcze żadnych ogłoszeń</h2>
          <p>Dodaj pierwsze ogłoszenie, aby pojawiło się tutaj oraz na ogólnej liście.</p>
          <a href="post-ad.html" class="my-offers-empty-button">Dodaj ogłoszenie</a>
        </div>
      `;
      return;
    }

    grid.innerHTML = data.map(renderMyOfferCard).join("");

    grid.querySelectorAll("[data-delete-offer]").forEach((button) => {
      button.addEventListener("click", () => deleteOffer(button.dataset.deleteOffer));
    });
  } catch (error) {
    console.error("Błąd pobierania własnych ogłoszeń:", error);
    if (message) {
      message.textContent = error.message || "Nie udało się pobrać ogłoszeń.";
      message.className = "my-offers-message is-error";
    }
  }
}

function renderMyOfferCard(offer) {
  const status = getStatusLabel(offer.status);
  const type = getTypeLabel(offer.type);
  const price = offer.price == null ? "Cena do uzgodnienia" : `${formatPrice(offer.price)} zł`;

  return `
    <article class="my-offer-card">
      <div class="my-offer-card-top">
        <span class="my-offer-status status-${String(offer.status).toLowerCase()}">${status}</span>
        <span class="my-offer-type">${escapeHtml(type)}</span>
      </div>
      <div class="my-offer-card-content">
        <span class="my-offer-category">${escapeHtml(offer.categoryName || "Bez kategorii")}</span>
        <h2>${escapeHtml(offer.title)}</h2>
        <p class="my-offer-description">${escapeHtml(offer.description)}</p>
        <div class="my-offer-meta">
          <strong>${escapeHtml(price)}</strong>
          <span>${escapeHtml(offer.location || "Lokalizacja nie podana")}</span>
        </div>
        <div class="my-offer-date">Dodano: ${formatDate(offer.createdAt)}</div>
      </div>
      <div class="my-offer-actions">
        <a class="my-offer-view" href="offer-details.html?id=${encodeURIComponent(offer.id)}">Podgląd</a>
        <a class="my-offer-edit" href="edit-offer.html?id=${encodeURIComponent(offer.id)}">Edytuj</a>
        <button class="my-offer-delete" type="button" data-delete-offer="${escapeHtml(String(offer.id))}">Usuń</button>
      </div>
    </article>
  `;
}

async function deleteOffer(id) {
  if (!confirm("Czy na pewno chcesz usunąć to ogłoszenie?")) return;

  try {
    const response = await fetch(`${MY_OFFERS_API_BASE_URL}/api/offers/${encodeURIComponent(id)}`, {
      method: "DELETE",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      const data = await readMyOffersResponse(response);
      throw new Error(data?.message || data?.error || "Nie udało się usunąć ogłoszenia.");
    }

    await loadMyOffers();
  } catch (error) {
    alert(error.message || "Nie udało się usunąć ogłoszenia.");
  }
}

function getStatusLabel(status) {
  return { ACTIVE: "Aktywne", INACTIVE: "Nieaktywne", SOLD: "Sprzedane" }[status] || status || "Nieznany";
}

function getTypeLabel(type) {
  return { SALE: "Sprzedaż", SERVICE: "Usługa", JOB: "Oferta pracy" }[type] || type || "";
}

function formatPrice(value) {
  return new Intl.NumberFormat("pl-PL", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value));
}

function formatDate(value) {
  if (!value) return "brak daty";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "brak daty";
  return date.toLocaleDateString("pl-PL");
}

async function readMyOffersResponse(response) {
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}
