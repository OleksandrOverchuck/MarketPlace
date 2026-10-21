const PAYOUTS_API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", initPayouts);

async function initPayouts() {
  const button = document.getElementById("payouts-button");

  if (!button) {
    return;
  }

  button.addEventListener("click", startPayoutsOnboarding);

  const returned = new URLSearchParams(window.location.search).get("payouts");

  await refreshPayoutsStatus();

  if (returned === "return") {
    showPayoutsMessage(
      "Wróciłeś ze Stripe. Weryfikacja może potrwać chwilę, odśwież stronę za moment.",
      false,
    );
  }

  if (returned === "refresh") {
    showPayoutsMessage(
      "Link wygasł. Kliknij przycisk poniżej, aby kontynuować konfigurację.",
      true,
    );
  }
}

async function refreshPayoutsStatus() {
  const statusEl = document.getElementById("payouts-status");
  const button = document.getElementById("payouts-button");

  try {
    const response = await fetch(
      `${PAYOUTS_API_BASE_URL}/api/payments/seller/status`,
      { method: "GET", credentials: "include" },
    );

    const data = await response.json().catch(() => null);

    if (!response.ok || !data) {
      throw new Error(data?.message || "Nie udało się sprawdzić statusu.");
    }

    if (data.payoutsReady) {
      statusEl.textContent =
        "✔ Konto do wypłat jest aktywne. Możesz włączać płatność przez JollyCart w ogłoszeniach sprzedaży.";
      button.hidden = true;
    } else if (data.accountCreated && data.detailsSubmitted) {
      statusEl.textContent =
        "Stripe weryfikuje Twoje dane. Zwykle trwa to chwilę, odśwież stronę za moment.";
      button.hidden = true;
    } else if (data.accountCreated) {
      statusEl.textContent = "Konfiguracja konta nie została dokończona.";
      button.textContent = "Dokończ konfigurację";
      button.hidden = false;
    } else {
      statusEl.textContent = "Nie dodano jeszcze konta bankowego.";
      button.textContent = "Dodaj konto bankowe";
      button.hidden = false;
    }
  } catch (error) {
    statusEl.textContent = error.message;
    button.hidden = true;
  }
}

async function startPayoutsOnboarding() {
  const button = document.getElementById("payouts-button");

  button.disabled = true;
  showPayoutsMessage("", false);

  try {
    const response = await fetch(
      `${PAYOUTS_API_BASE_URL}/api/payments/seller/onboarding`,
      { method: "POST", credentials: "include" },
    );

    const data = await response.json().catch(() => null);

    if (!response.ok || !data?.url) {
      throw new Error(data?.message || "Nie udało się rozpocząć konfiguracji.");
    }

    window.location.href = data.url;
  } catch (error) {
    showPayoutsMessage(error.message, true);
    button.disabled = false;
  }
}

function showPayoutsMessage(message, isError) {
  const el = document.getElementById("payouts-message");

  if (!el) {
    return;
  }

  el.textContent = message;
  el.style.color = isError ? "#a83e32" : "#093f38";
}
