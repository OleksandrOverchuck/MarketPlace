const PAYMENT_FORM_API_BASE_URL = "http://localhost:8080";

let paymentFormPayoutsReady = false;

document.addEventListener("DOMContentLoaded", initOfferFormPayment);

async function initOfferFormPayment() {
  const typeSelect = document.getElementById("type");

  if (!document.getElementById("online-payment-field") || !typeSelect) {
    return;
  }

  typeSelect.addEventListener("change", applyOfferFormPaymentState);
  applyOfferFormPaymentState();

  try {
    const response = await fetch(
      `${PAYMENT_FORM_API_BASE_URL}/api/payments/seller/status`,
      { method: "GET", credentials: "include" },
    );

    if (response.ok) {
      const status = await response.json();
      paymentFormPayoutsReady = Boolean(status.payoutsReady);
    }
  } catch (error) {
    console.error("Nie udało się sprawdzić konta wypłat:", error);
  }

  applyOfferFormPaymentState();
}

function applyOfferFormPaymentState() {
  const field = document.getElementById("online-payment-field");
  const checkbox = document.getElementById("online-payment");
  const help = document.getElementById("online-payment-help");
  const typeSelect = document.getElementById("type");

  if (!field || !checkbox || !help || !typeSelect) {
    return;
  }

  const isSale = typeSelect.value === "SALE";

  field.hidden = !isSale;

  if (!isSale) {
    checkbox.checked = false;
    return;
  }

  // Zaznaczone pole zostaje aktywne, żeby dało się je odznaczyć.
  checkbox.disabled = !paymentFormPayoutsReady && !checkbox.checked;

  if (paymentFormPayoutsReady) {
    help.textContent =
      "Kupujący zapłaci kartą. Pieniądze trafią do Ciebie po potwierdzeniu odbioru towaru. Wymagana cena min. 2,00 zł.";
  } else {
    help.innerHTML =
      'Aby przyjmować płatności, <a href="settings.html#payouts">dodaj konto bankowe w ustawieniach</a>.';
  }
}

function getOnlinePaymentValue() {
  const checkbox = document.getElementById("online-payment");
  const typeSelect = document.getElementById("type");

  return Boolean(
    checkbox && checkbox.checked && typeSelect && typeSelect.value === "SALE",
  );
}

function setOnlinePaymentValue(value) {
  const checkbox = document.getElementById("online-payment");

  if (checkbox) {
    checkbox.checked = Boolean(value);
  }

  applyOfferFormPaymentState();
}
