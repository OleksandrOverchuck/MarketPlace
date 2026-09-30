const CHAT_API_BASE_URL =
  typeof API_BASE_URL !== "undefined" ? API_BASE_URL : "http://localhost:8080";

const chatParams = new URLSearchParams(window.location.search);

const conversationId = chatParams.get("conversationId");

const offerId = chatParams.get("offerId");

let currentUser = null;
let conversation = null;
let messages = [];
let refreshInterval = null;

/* =====================================================
   START
   ===================================================== */

document.addEventListener("DOMContentLoaded", () => {
  setupChat();
});

/* =====================================================
   SETUP
   ===================================================== */

async function setupChat() {
  if (!conversationId) {
    showError("Nie znaleziono identyfikatora rozmowy.");

    return;
  }

  setupBackButton();

  try {
    await loadCurrentUser();

    await loadConversation();

    await loadMessages(true);

    setupMessageForm();

    startAutoRefresh();
  } catch (error) {
    console.error("Błąd uruchamiania czatu:", error);

    showError("Nie udało się załadować rozmowy.");
  }
}

/* =====================================================
   CURRENT USER
   ===================================================== */

async function loadCurrentUser() {
  const response = await fetch(`${CHAT_API_BASE_URL}/api/users/me`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(
      `Nie udało się pobrać użytkownika. HTTP ${response.status}`,
    );
  }

  currentUser = await response.json();

  if (!currentUser || !currentUser.id) {
    throw new Error("Backend nie zwrócił danych użytkownika.");
  }
}

/* =====================================================
   CONVERSATION
   ===================================================== */

async function loadConversation() {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    let errorMessage = `Nie udało się pobrać rozmowy. HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  conversation = await response.json();

  if (!conversation) {
    throw new Error("Backend nie zwrócił danych rozmowy.");
  }

  renderConversationHeader();
}

/* =====================================================
   OTHER PARTICIPANT
   ===================================================== */

function getOtherParticipant() {
  if (!conversation || !conversation.participants || !currentUser) {
    return null;
  }

  return conversation.participants.find(
    (participant) => Number(participant.userId) !== Number(currentUser.id),
  );
}

/* =====================================================
   AVATAR URL
   ===================================================== */

function getAvatarUrl(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  if (avatarUrl.startsWith("/")) {
    return CHAT_API_BASE_URL + avatarUrl;
  }

  return CHAT_API_BASE_URL + "/" + avatarUrl;
}

/* =====================================================
   CHAT HEADER
   ===================================================== */

function renderConversationHeader() {
  const otherParticipant = getOtherParticipant();

  const nameElement = document.getElementById("chat-user-name");

  const avatarElement = document.getElementById("chat-user-avatar");

  if (!otherParticipant) {
    if (nameElement) {
      nameElement.textContent = "Rozmowa";
    }

    return;
  }

  if (nameElement) {
    nameElement.textContent = otherParticipant.nickname || "Użytkownik";
  }

  if (!avatarElement) {
    return;
  }

  avatarElement.innerHTML = "";

  const avatarUrl = getAvatarUrl(otherParticipant.avatarUrl);

  if (avatarUrl) {
    const image = document.createElement("img");

    image.src = avatarUrl;

    image.alt = otherParticipant.nickname || "Użytkownik";

    image.onerror = () => {
      avatarElement.innerHTML = "";

      avatarElement.textContent = getInitial(otherParticipant.nickname);
    };

    avatarElement.appendChild(image);
  } else {
    avatarElement.textContent = getInitial(otherParticipant.nickname);
  }
}

/* =====================================================
   INITIAL
   ===================================================== */

function getInitial(name) {
  if (!name) {
    return "?";
  }

  return name.trim().charAt(0).toUpperCase();
}

/* =====================================================
   LOAD MESSAGES
   ===================================================== */

async function loadMessages(shouldScroll = true) {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    let errorMessage = `Nie udało się pobrać wiadomości. HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  messages = await response.json();

  if (!Array.isArray(messages)) {
    messages = [];
  }

  renderMessages(shouldScroll);
}

/* =====================================================
   RENDER MESSAGES
   ===================================================== */

function renderMessages(shouldScroll = true) {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  container.innerHTML = "";

  if (!messages.length) {
    const empty = document.createElement("div");

    empty.className = "empty-chat";

    empty.textContent = "Napisz pierwszą wiadomość.";

    container.appendChild(empty);

    return;
  }

  messages.forEach((message) => {
    const isMine = Number(message.senderId) === Number(currentUser.id);

    const messageElement = document.createElement("div");

    messageElement.className = isMine ? "message mine" : "message theirs";

    const content = document.createElement("div");

    content.className = "message-content";

    const bubble = document.createElement("div");

    bubble.className = "message-bubble";

    bubble.textContent = message.content;

    const time = document.createElement("div");

    time.className = "message-time";

    time.textContent = formatMessageTime(message.createdAt);

    content.appendChild(bubble);

    content.appendChild(time);

    messageElement.appendChild(content);

    container.appendChild(messageElement);
  });

  if (shouldScroll) {
    scrollMessagesToBottom();
  }
}

/* =====================================================
   TIME
   ===================================================== */

function formatMessageTime(dateString) {
  if (!dateString) {
    return "";
  }

  const date = new Date(dateString);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleTimeString("pl-PL", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

/* =====================================================
   MESSAGE FORM
   ===================================================== */

function setupMessageForm() {
  const form = document.getElementById("message-form");

  const input = document.getElementById("message-input");

  const button = document.getElementById("send-message-button");

  if (!form || !input || !button) {
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const content = input.value.trim();

    if (!content) {
      input.focus();

      return;
    }

    button.disabled = true;

    try {
      await sendMessage(content);

      input.value = "";

      await loadMessages(true);

      input.focus();
    } catch (error) {
      console.error("Błąd wysyłania wiadomości:", error);

      alert(error.message || "Nie udało się wysłać wiadomości.");
    } finally {
      button.disabled = false;
    }
  });
}

/* =====================================================
   SEND MESSAGE
   ===================================================== */

async function sendMessage(content) {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages`,
    {
      method: "POST",

      credentials: "include",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({
        content: content,
      }),
    },
  );

  if (!response.ok) {
    let errorMessage = `HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  return await response.json();
}

/* =====================================================
   AUTO REFRESH
   ===================================================== */

function startAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }

  refreshInterval = setInterval(async () => {
    try {
      await loadMessages(false);
    } catch (error) {
      console.error("Błąd odświeżania wiadomości:", error);
    }
  }, 3000);
}

/* =====================================================
   SCROLL
   ===================================================== */

function scrollMessagesToBottom() {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  container.scrollTop = container.scrollHeight;
}

/* =====================================================
   WRÓĆ Z CZATU
   ===================================================== */

function setupBackButton() {
  const button = document.getElementById("back-button");

  if (!button) {
    return;
  }

  button.addEventListener("click", () => {
    /*
     * Jeżeli czat został otwarty
     * z konkretnego ogłoszenia,
     * wracamy bezpośrednio do niego.
     */

    if (offerId) {
      window.location.href = `offer-details.html?id=${encodeURIComponent(
        offerId,
      )}`;

      return;
    }

    /*
     * Awaryjnie, gdyby czat został
     * otwarty bez offerId.
     */

    window.location.href = "index.html#offers";
  });
}

/* =====================================================
   ERROR
   ===================================================== */

function showError(message) {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  container.innerHTML = "";

  const error = document.createElement("div");

  error.className = "empty-chat";

  error.textContent = message;

  container.appendChild(error);
}

/* =====================================================
   CLEANUP
   ===================================================== */

window.addEventListener("beforeunload", () => {
  if (refreshInterval) {
    clearInterval(refreshInterval);

    refreshInterval = null;
  }
});
