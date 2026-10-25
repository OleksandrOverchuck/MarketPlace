const PAYOUTS_API_BASE_URL = "http://localhost:8080";

// "onboarding" = dodaj / dokończ konto, "dashboard" = zmień konto
let payoutsMode = "onboarding";

document.addEventListener("DOMContentLoaded", initPayouts);

async function initPayouts() {
  const button = document.getElementById("payouts-button");

  if (!button) {
    return;
  }

  button.addEventListener("click", openPayoutsLink);

  // Powrót "wstecz" ze Stripe przywraca stronę z bfcache (z zablokowanym
  // przyciskiem), więc po takim powrocie odświeżamy stan.
  window.addEventListener("pageshow", async (event) => {
    if (event.persisted) {
      button.disabled = false;
      showPayoutsMessage("", false);
      await refreshPayoutsStatus();
    }
  });

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
    button.disabled = false;

    const response = await fetch(
      `${PAYOUTS_API_BASE_URL}/api/payments/seller/status`,
      { method: "GET", credentials: "include" },
    );

    const data = await response.json().catch(() => null);

    if (!response.ok || !data) {
      throw new Error(data?.message || "Nie udało się sprawdzić statusu.");
    }

    if (data.payoutsReady) {
      payoutsMode = "dashboard";
      statusEl.textContent =
        "✔ Konto do wypłat jest aktywne. Możesz włączać płatność przez JollyCart w ogłoszeniach sprzedaży.";
      button.textContent = "Moje konto";
      button.hidden = false;
    } else if (data.accountCreated && data.detailsSubmitted) {
      payoutsMode = "dashboard";
      statusEl.textContent =
        "Stripe weryfikuje Twoje dane. Zwykle trwa to chwilę, odśwież stronę za moment.";
      button.hidden = true;
    } else if (data.accountCreated) {
      payoutsMode = "onboarding";
      statusEl.textContent = "Konfiguracja konta nie została dokończona.";
      button.textContent = "Dokończ konfigurację";
      button.hidden = false;
    } else {
      payoutsMode = "onboarding";
      statusEl.textContent = "Nie dodano jeszcze konta bankowego.";
      button.textContent = "Dodaj konto bankowe";
      button.hidden = false;
    }
  } catch (error) {
    statusEl.textContent = error.message;
    button.hidden = true;
  }
}

async function openPayoutsLink() {
  const button = document.getElementById("payouts-button");

  button.disabled = true;
  showPayoutsMessage("", false);

  const path =
    payoutsMode === "dashboard"
      ? "/api/payments/seller/dashboard"
      : "/api/payments/seller/onboarding";

  try {
    const response = await fetch(`${PAYOUTS_API_BASE_URL}${path}`, {
      method: "POST",
      credentials: "include",
    });

    const data = await response.json().catch(() => null);

    if (!response.ok || !data?.url) {
      throw new Error(data?.message || "Nie udało się otworzyć konfiguracji.");
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
