const BUY_API_BASE_URL = "http://localhost:8080";

async function setupBuyButton(offer) {
  const button = document.getElementById("buy-button");
  const note = document.getElementById("buy-note");

  if (!button || !offer) {
    return;
  }

  // Przycisk tylko dla ofert sprzedaży z włączoną płatnością online
  if (offer.type !== "SALE" || !offer.onlinePaymentEnabled) {
    return;
  }

  if (offer.status !== "ACTIVE" && offer.status !== "RESERVED") {
    return;
  }

  let currentUser = null;

  try {
    const response = await fetch(`${BUY_API_BASE_URL}/api/users/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.ok) {
      currentUser = await response.json();
    }
  } catch (error) {
    console.error("Nie udało się pobrać użytkownika:", error);
  }

  // Własnej oferty nie można kupić
  if (currentUser && Number(currentUser.id) === Number(offer.userId)) {
    return;
  }

  button.style.display = "block";

  if (offer.status === "RESERVED") {
    button.disabled = true;
    button.textContent = "Chwilowo niedostępne";

    if (note) {
      note.hidden = false;
      note.textContent =
        "Ktoś właśnie płaci za ten przedmiot. Jeśli płatność się nie powiedzie, oferta znów będzie dostępna.";
    }

    return;
  }

  if (note) {
    note.hidden = false;
    note.textContent =
      "Zapłać kartą - pieniądze trafią do sprzedawcy dopiero, gdy potwierdzisz odbiór towaru.";
  }

  button.addEventListener("click", async () => {
    if (!currentUser) {
      window.location.href = "login.html";
      return;
    }

    button.disabled = true;
    button.textContent = "Przekierowanie do płatności...";

    try {
      const response = await fetch(`${BUY_API_BASE_URL}/api/orders/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ offerId: Number(offer.id) }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok || !data?.checkoutUrl) {
        throw new Error(data?.message || "Nie udało się rozpocząć płatności.");
      }

      window.location.href = data.checkoutUrl;
    } catch (error) {
      alert(error.message);
      window.location.reload();
    }
  });
}
