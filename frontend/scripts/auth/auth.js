const API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  setupLoginPage();
  setupHomePage();
  setupSearchShortcut();
});

// Po kliknięciu "Szukaj" w menu ustawia kursor w polu wyszukiwania
function setupSearchShortcut() {
  const searchLink = document.querySelector("[data-focus-search]");
  const searchInput = document.querySelector(".search-box input");

  if (!searchLink || !searchInput) {
    return;
  }

  searchLink.addEventListener("click", () => {
    // Czekamy, aż zakończy się płynne przewijanie
    setTimeout(() => searchInput.focus({ preventScroll: true }), 600);
  });
}

function setupLoginPage() {
  const loginForm = document.querySelector(".auth-form");
  const googleButton = document.querySelector(".oauth-btn");

  if (loginForm) {
    loginForm.addEventListener("submit", handleLogin);
  }

  if (googleButton) {
    googleButton.addEventListener("click", handleGoogleLogin);
  }
}

function setupHomePage() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  loadCurrentUser();
}

async function handleLogin(event) {
  event.preventDefault();

  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    showLoginMessage("Email i hasło są wymagane.", true);
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
        showLoginMessage("Nieprawidłowy email lub hasło.", true);
        return;
      }

      showLoginMessage("Wystąpił błąd podczas logowania.", true);

      return;
    }

    const user = await response.json();

    console.log("Zalogowano użytkownika:", user);

    window.location.href = "index.html";
  } catch (error) {
    console.error("Błąd logowania:", error);

    showLoginMessage("Nie udało się połączyć z serwerem.", true);
  }
}

function handleGoogleLogin() {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
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

  // Ukrywa linki widoczne tylko dla gości (Strona główna, Kategorie, Ogłoszenia)
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
        <circle cx="12" cy="8" r="4"></circle>
        <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
      </svg>

      <span>Twoje konto</span>

      <span class="account-arrow"></span>
    </button>

    <div class="account-dropdown">

      <div class="account-user">

        <div class="account-avatar">
          <svg
            viewBox="0 0 24 24"
          >
            <circle cx="12" cy="8" r="4"></circle>
            <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
          </svg>
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

        <a href="post-ad.html"
           class="account-menu-item account-menu-cta">
          Dodaj ogłoszenie
        </a>

        <a href="my-offers.html"
           class="account-menu-item">
          Ogłoszenia
        </a>

        <a href="chat.html"
           class="account-menu-item">
          Czat
        </a>

        <a href="payments.html"
           class="account-menu-item">
          Płatności
        </a>

        <a href="reviews.html"
           class="account-menu-item">
          Oceny
        </a>

        <a href="jobs.html"
           class="account-menu-item">
          Szukam pracy
        </a>

        <a href="profile.html"
           class="account-menu-item">
          Profil
        </a>

        <a href="settings.html"
           class="account-menu-item">
          Ustawienia
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

  // Przywraca linki dla gości
  document.documentElement.classList.remove("logged-in");
  localStorage.removeItem("loggedIn");

  authUserElement.innerHTML = "";

  const loginLink = document.createElement("a");

  loginLink.href = "login.html";
  loginLink.textContent = "Logowanie";
  loginLink.className = "auth-login-link";

  authUserElement.appendChild(loginLink);
}

function escapeHtml(value) {
  const div = document.createElement("div");

  div.textContent = value;

  return div.innerHTML;
}

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

function showLoginMessage(message, isError) {
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
