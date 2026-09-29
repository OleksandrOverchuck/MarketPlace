const API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  setupLoginPage();
  setupHomePage();
});

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

  authUserElement.innerHTML = "";

  const nickname = document.createElement("span");

  nickname.textContent = `Witaj, ${user.nickname}`;

  const profileLink = document.createElement("a");

  profileLink.href = "#";

  profileLink.textContent = "Mój profil";

  profileLink.addEventListener("click", (event) => {
    event.preventDefault();

    alert(
      `Zalogowany użytkownik:\n\n` +
        `Nick: ${user.nickname}\n` +
        `Email: ${user.email}\n` +
        `Provider: ${user.authProvider}`,
    );
  });

  const logoutButton = document.createElement("button");

  logoutButton.type = "button";

  logoutButton.textContent = "Wyloguj";

  logoutButton.addEventListener("click", handleLogout);

  authUserElement.appendChild(nickname);

  authUserElement.appendChild(profileLink);

  authUserElement.appendChild(logoutButton);
}

function displayGuestUser() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  authUserElement.innerHTML = "";

  const loginLink = document.createElement("a");

  loginLink.href = "login.html";

  loginLink.textContent = "Zaloguj się";

  authUserElement.appendChild(loginLink);
}

async function handleLogout() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });

    if (response.ok || response.status === 204) {
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
