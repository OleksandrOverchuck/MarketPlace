const API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  setupLoginPage();
  setupRegisterPage();
  setupHomePage();
  setupSearchShortcut();
});

function setupSearchShortcut() {
  const searchLink = document.querySelector("[data-focus-search]");
  const searchInput = document.querySelector(".search-box input");

  if (!searchLink || !searchInput) {
    return;
  }

  searchLink.addEventListener("click", () => {
    setTimeout(() => {
      searchInput.focus({ preventScroll: true });
    }, 600);
  });
}

/* =========================
   LOGOWANIE
   ========================= */

function setupLoginPage() {
  const loginForm = document.querySelector(".auth-form");
  const googleButton = document.querySelector(".oauth-btn");

  /*
   * Jeżeli na stronie jest formularz logowania,
   * podpinamy obsługę logowania.
   */
  if (
    loginForm &&
    document.getElementById("email") &&
    document.getElementById("password") &&
    !document.getElementById("username")
  ) {
    loginForm.addEventListener("submit", handleLogin);
  }

  /*
   * Google działa zarówno na login.html,
   * jak i na register.html.
   */
  if (googleButton) {
    googleButton.addEventListener("click", handleGoogleLogin);
  }
}

async function handleLogin(event) {
  event.preventDefault();

  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");

  if (!emailInput || !passwordInput) {
    return;
  }

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    showAuthMessage("Email i hasło są wymagane.", true);
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        email: email,
        password: password,
      }),
    });

    if (!response.ok) {
      if (response.status === 401) {
        showAuthMessage("Nieprawidłowy email lub hasło.", true);
        return;
      }

      showAuthMessage("Wystąpił błąd podczas logowania.", true);

      return;
    }

    const user = await response.json();

    console.log("Zalogowano użytkownika:", user);

    window.location.href = "index.html";
  } catch (error) {
    console.error("Błąd logowania:", error);

    showAuthMessage("Nie udało się połączyć z serwerem.", true);
  }
}

/* =========================
   GOOGLE
   ========================= */

function handleGoogleLogin() {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
}

/* =========================
   REJESTRACJA
   ========================= */

function setupRegisterPage() {
  const registerForm = document.querySelector(".auth-form");

  /*
   * Rozróżniamy rejestrację od logowania
   * po obecności pola username.
   */
  const usernameInput = document.getElementById("username");
  const confirmPasswordInput = document.getElementById("confirmPassword");

  if (registerForm && usernameInput && confirmPasswordInput) {
    registerForm.addEventListener("submit", handleRegister);
  }
}

async function handleRegister(event) {
  event.preventDefault();

  const nickname = document.getElementById("username").value.trim();
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value;
  const confirmPassword = document.getElementById("confirmPassword").value;

  if (!nickname || !email || !password || !confirmPassword) {
    showAuthMessage("Wypełnij wszystkie pola.", "error");
    return;
  }

  if (password !== confirmPassword) {
    showAuthMessage("Hasła nie są takie same.", "error");
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        nickname: nickname,
        email: email,
        password: password,
        confirmPassword: confirmPassword,
      }),
    });

    const text = await response.text();

    let data = null;

    if (text) {
      try {
        data = JSON.parse(text);
      } catch (error) {
        data = null;
      }
    }

    if (!response.ok) {
      const message =
        data?.message ||
        data?.error ||
        text ||
        `Rejestracja nie powiodła się. Kod HTTP: ${response.status}`;

      showAuthMessage(message, "error");
      return;
    }

    showAuthMessage(
      "Konto zostało utworzone. Za chwilę przejdziesz do logowania.",
      "success",
    );

    setTimeout(() => {
      window.location.href = "login.html";
    }, 1000);
  } catch (error) {
    console.error("BŁĄD REJESTRACJI - pełny błąd:", error);
    console.error("BŁĄD REJESTRACJI - message:", error.message);
    console.error("BŁĄD REJESTRACJI - stack:", error.stack);

    showAuthMessage(`Błąd: ${error.message}`, true);
  }
}

/* =========================
   POBIERANIE BŁĘDU Z BACKENDU
   ========================= */

async function readErrorMessage(response) {
  try {
    const data = await response.json();

    if (typeof data === "string") {
      return data;
    }

    if (data.message) {
      return data.message;
    }

    if (data.error) {
      return data.error;
    }

    return "";
  } catch (error) {
    return "";
  }
}

/* =========================
   AKTUALNY UŻYTKOWNIK
   ========================= */

function setupHomePage() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  loadCurrentUser();
}

async function loadCurrentUser() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    const authUserElement = document.getElementById("auth-user");

    if (!authUserElement) {
      return;
    }

    if (response.ok) {
      const user = await response.json();

      displayLoggedUser(user);

      return;
    }

    displayGuestUser();
  } catch (error) {
    console.error("Błąd pobierania użytkownika:", error);

    displayGuestUser();
  }
}

function displayLoggedUser(user) {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  document.documentElement.classList.add("logged-in");

  localStorage.setItem("loggedIn", "1");

  authUserElement.innerHTML = `
    <button
      type="button"
      class="account-button"
      aria-label="Twoje konto"
    >
      <svg
        class="account-icon"
        viewBox="0 0 24 24"
      >
        <circle
          cx="12"
          cy="8"
          r="4"
        ></circle>

        <path
          d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"
        ></path>
      </svg>

      <span>Twoje konto</span>

      <span class="account-arrow"></span>
    </button>

    <div class="account-dropdown">
      <div class="account-user">
        <div class="account-avatar">
          ${renderAvatar(user)}
        </div>

        <div class="account-user-info">
          <p class="account-nickname">
            ${escapeHtml(user.nickname)}
          </p>

          <p class="account-id">
            id: ${escapeHtml(String(user.id))}
          </p>
        </div>
      </div>

      <div class="account-menu-title">
        Twoje konto
      </div>

      <div class="account-menu">

       <a
          href="settings.html"
          class="account-menu-item"
        >
          Profil
        </a>

        <a
          href="post-ad.html"
          class="account-menu-item account-menu-cta"
        >
          Dodaj ogłoszenie
        </a>

        <a
          href="my-offers.html"
          class="account-menu-item"
        >
          Ogłoszenia
        </a>

        <a
          href="chat.html"
          class="account-menu-item"
        >
          Czat
        </a>

        <a
          href="payments.html"
          class="account-menu-item"
        >
          Płatności
        </a>

        <a
          href="reviews.html"
          class="account-menu-item"
        >
          Oceny
        </a>

        <a
          href="jobs.html"
          class="account-menu-item"
        >
          Szukam pracy
        </a>
      
      </div>

      <div class="account-logout-wrapper">
        <button
          type="button"
          class="account-menu-item account-logout"
          id="logout-button"
        >
          Wyloguj
        </button>
      </div>
    </div>
  `;

  const logoutButton = document.getElementById("logout-button");

  if (logoutButton) {
    logoutButton.addEventListener("click", handleLogout);
  }
}

function displayGuestUser() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  document.documentElement.classList.remove("logged-in");

  localStorage.removeItem("loggedIn");

  authUserElement.innerHTML = "";

  const loginLink = document.createElement("a");

  loginLink.href = "login.html";
  loginLink.textContent = "Logowanie";
  loginLink.className = "auth-login-link";

  authUserElement.appendChild(loginLink);
}

/* =========================
   WYLOGOWANIE
   ========================= */

async function handleLogout() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });

    if (response.ok || response.status === 204) {
      localStorage.removeItem("loggedIn");

      window.location.href = "index.html";

      return;
    }

    alert("Nie udało się wylogować.");
  } catch (error) {
    console.error("Błąd wylogowania:", error);

    alert("Nie udało się połączyć z serwerem.");
  }
}

/* =========================
   POMOCNICZE
   ========================= */

function getAvatarSrc(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  return `${API_BASE_URL}${avatarUrl}`;
}

function renderAvatar(user) {
  const avatarSrc = getAvatarSrc(user.avatarUrl);

  if (avatarSrc) {
    return `
      <img
        src="${escapeHtml(avatarSrc)}"
        alt="Zdjęcie profilowe"
        class="account-avatar-image"
      />
    `;
  }

  return `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4"></circle>
      <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
    </svg>
  `;
}

function escapeHtml(value) {
  const div = document.createElement("div");

  div.textContent = value;

  return div.innerHTML;
}

function showAuthMessage(message, isError) {
  let messageElement = document.querySelector(".auth-message");

  if (!messageElement) {
    messageElement = document.createElement("p");

    messageElement.className = "auth-message";

    const form = document.querySelector(".auth-form");

    if (form) {
      form.insertAdjacentElement("beforebegin", messageElement);
    }
  }

  messageElement.textContent = message;

  messageElement.style.color = isError ? "#c0392b" : "#2f6b3a";
}
