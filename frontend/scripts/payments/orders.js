const ORDERS_API_BASE_URL = "http://localhost:8080";

const ORDER_STATUS_LABELS = {
  PENDING_PAYMENT: "Oczekuje na płatność",
  PAID: "Opłacone - oczekuje na wysyłkę",
  SHIPPED: "Wysłane",
  DELIVERED: "Odebrane - czeka na potwierdzenie",
  COMPLETED: "Zakończone",
  DISPUTED: "Zgłoszony problem",
  CANCELLED: "Anulowane",
  REFUNDED: "Zwrócono pieniądze",
};

let ordersTab = "purchases";
let ordersRefreshTimer = null;

document.addEventListener("DOMContentLoaded", async () => {
  document.querySelectorAll("[data-orders-tab]").forEach((tab) => {
    tab.addEventListener("click", () => {
      ordersTab = tab.dataset.ordersTab;
      document
        .querySelectorAll("[data-orders-tab]")
        .forEach((t) => t.classList.toggle("is-active", t === tab));
      loadOrders();
    });
  });

  document
    .getElementById("orders-list")
    .addEventListener("click", handleOrderAction);

  await handleCheckoutReturn();
  await loadOrders();
});

/* Powrót ze strony płatności Stripe: ?checkout=success|cancel&order=ID */
/* Powrót ze strony płatności Stripe: ?checkout=success|cancel&order=ID */
async function handleCheckoutReturn() {
  const params = new URLSearchParams(window.location.search);
  const checkout = params.get("checkout");
  const orderId = params.get("order");

  if (!checkout) {
    return;
  }

  // Czyścimy parametry, żeby odświeżenie strony nie powtórzyło akcji
  window.history.replaceState({}, "", window.location.pathname);

  if (checkout === "cancel" && orderId) {
    try {
      await ordersApi(
        `/api/orders/${encodeURIComponent(orderId)}/cancel`,
        "POST",
      );
      showOrdersBanner("Płatność anulowana. Oferta znów jest dostępna.", false);
    } catch (error) {
      showOrdersBanner(error.message, true);
    }
    return;
  }

  if (checkout === "success" && orderId) {
    showOrdersBanner("Sprawdzamy płatność...", false);

    // Backend pyta Stripe o stan sesji; zwykle wystarcza pierwsza próba.
    for (let attempt = 0; attempt < 8; attempt += 1) {
      try {
        const order = await ordersApi(
          `/api/orders/${encodeURIComponent(orderId)}/sync`,
          "POST",
        );

        if (order.status !== "PENDING_PAYMENT") {
          showOrdersBanner(
            `✔ Zamówienie zostało złożone! Płatność za „${order.offerTitle}" została przyjęta. ` +
              `Sprzedawca ma czas na wysyłkę do ${formatOrderDate(order.shipDeadline)}.`,
            false,
          );
          return;
        }
      } catch (error) {
        console.error("Synchronizacja płatności nie powiodła się:", error);
      }

      await new Promise((resolve) => setTimeout(resolve, 2000));
    }

    showOrdersBanner(
      "Płatność jest jeszcze przetwarzana. Odśwież stronę za chwilę.",
      false,
    );
  }
}

async function loadOrders() {
  const list = document.getElementById("orders-list");

  try {
    const orders = await ordersApi(`/api/orders/${ordersTab}`, "GET");

    if (!orders.length) {
      list.innerHTML = `<p class="orders-empty">${
        ordersTab === "purchases"
          ? "Nie masz jeszcze żadnych zakupów."
          : "Nie masz jeszcze żadnej sprzedaży przez JollyCart."
      }</p>`;
      return;
    }

    list.innerHTML = orders.map(renderOrderCard).join("");
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      window.location.href = "login.html";
      return;
    }
    list.innerHTML = `<p class="orders-empty">${escapeOrdersHtml(error.message)}</p>`;
  }
}

function renderOrderCard(order) {
  const isBuyer = ordersTab === "purchases";
  const counterpart = isBuyer
    ? `Sprzedający: ${order.sellerNickname}`
    : `Kupujący: ${order.buyerNickname}`;

  const lines = [];

  if (order.status === "PAID") {
    lines.push(
      isBuyer
        ? `Sprzedawca ma czas na wysyłkę do ${formatOrderDate(order.shipDeadline)}. Jeśli nie wyśle - zwrócimy Ci pieniądze.`
        : `Wyślij towar do ${formatOrderDate(order.shipDeadline)}. Po tym terminie kupujący dostanie zwrot.`,
    );
  }

  if (order.status === "SHIPPED") {
    lines.push(`Wysłano ${formatOrderDate(order.shippedAt)}.`);
  }

  if (order.status === "DELIVERED") {
    lines.push(
      isBuyer
        ? `Potwierdź lub zgłoś problem do ${formatOrderDate(order.confirmDeadline)} - inaczej środki zostaną wypłacone sprzedawcy.`
        : `Kupujący odebrał towar. Wypłata nastąpi po jego potwierdzeniu (najpóźniej ${formatOrderDate(order.confirmDeadline)}).`,
    );
  }

  if (order.status === "DISPUTED") {
    lines.push(
      `Środki są wstrzymane do decyzji administratora. Powód zgłoszenia: ${order.disputeReason || "-"}`,
    );
  }

  if (order.status === "COMPLETED") {
    lines.push(`Zakończono ${formatOrderDate(order.completedAt)}.`);
  }

  return `
    <article class="order-card" data-order-id="${order.id}">
      <div class="order-card-top">
        <a class="order-title" href="offer-details.html?id=${encodeURIComponent(order.offerId)}">
          ${escapeOrdersHtml(order.offerTitle)}
        </a>
        <strong class="order-amount">${formatOrderAmount(order.amount)} zł</strong>
      </div>

      <div class="order-meta">
        <span class="order-status is-${order.status.toLowerCase()}">
          ${escapeOrdersHtml(ORDER_STATUS_LABELS[order.status] || order.status)}
        </span>
        <span>${escapeOrdersHtml(counterpart)}</span>
        <span>Zamówiono ${formatOrderDate(order.createdAt)}</span>
      </div>

      ${lines.map((l) => `<p class="order-note">${escapeOrdersHtml(l)}</p>`).join("")}

      <div class="order-actions">${renderOrderActions(order, isBuyer)}</div>
    </article>
  `;
}

function renderOrderActions(order, isBuyer) {
  const btn = (action, label, cls = "") =>
    `<button type="button" class="order-button ${cls}" data-order-action="${action}">${label}</button>`;

  if (isBuyer) {
    switch (order.status) {
      case "PENDING_PAYMENT":
        return (
          (order.checkoutUrl
            ? `<a class="order-button" href="${escapeOrdersHtml(order.checkoutUrl)}">Dokończ płatność</a>`
            : "") + btn("cancel", "Anuluj", "is-secondary")
        );
      case "SHIPPED":
        return (
          btn("received", "Otrzymałem towar") +
          btn("dispute", "Zgłoś problem", "is-danger")
        );
      case "DELIVERED":
        return (
          btn("confirm", "Wszystko OK - zwolnij środki") +
          btn("dispute", "Zgłoś problem", "is-danger")
        );
      default:
        return "";
    }
  }

  if (order.status === "PAID") {
    return btn("ship", "Oznacz jako wysłane");
  }

  return "";
}

async function handleOrderAction(event) {
  const button = event.target.closest("[data-order-action]");

  if (!button) {
    return;
  }

  const card = button.closest("[data-order-id]");
  const orderId = card.dataset.orderId;
  const action = button.dataset.orderAction;

  let body = null;

  if (action === "dispute") {
    const reason = window.prompt("Opisz, jaki jest problem z zamówieniem:");

    if (reason === null) {
      return;
    }

    if (!reason.trim()) {
      showOrdersBanner("Opis problemu jest wymagany.", true);
      return;
    }

    body = { reason: reason.trim() };
  }

  if (
    action === "confirm" &&
    !window.confirm(
      "Potwierdzasz, że towar jest w porządku? Środki trafią do sprzedawcy.",
    )
  ) {
    return;
  }

  button.disabled = true;

  try {
    await ordersApi(
      `/api/orders/${encodeURIComponent(orderId)}/${action}`,
      "POST",
      body,
    );
    showOrdersBanner("Zapisano.", false);
  } catch (error) {
    showOrdersBanner(error.message, true);
  }

  await loadOrders();
}

async function ordersApi(path, method, body) {
  const response = await fetch(`${ORDERS_API_BASE_URL}${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    credentials: "include",
    body: body ? JSON.stringify(body) : undefined,
  });

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(data?.message || "Operacja nie powiodła się.");
    error.status = response.status;
    throw error;
  }

  return data;
}

function showOrdersBanner(message, isError) {
  const banner = document.getElementById("orders-banner");

  banner.textContent = message;
  banner.hidden = !message;
  banner.classList.toggle("is-error", Boolean(isError));
}

function formatOrderAmount(value) {
  return Number(value).toLocaleString("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatOrderDate(value) {
  if (!value) {
    return "-";
  }

  return new Date(value).toLocaleString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function escapeOrdersHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
