const API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const loginForm = document.querySelector(".auth-form");
  const googleButton = document.querySelector(".oauth-btn");

  if (loginForm) {
    loginForm.addEventListener("submit", handleLogin);
  }

  if (googleButton) {
    googleButton.addEventListener("click", handleGoogleLogin);
  }
});

async function handleLogin(event) {
  event.preventDefault();

  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    showMessage("Email i hasło są wymagane.", true);
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
        showMessage("Nieprawidłowy email lub hasło.", true);
        return;
      }

      showMessage("Wystąpił błąd podczas logowania.", true);
      return;
    }

    const user = await response.json();

    showMessage(`Zalogowano jako ${user.nickname}.`, false);

    setTimeout(() => {
      window.location.href = "index.html";
    }, 500);
  } catch (error) {
    console.error("Błąd logowania:", error);

    showMessage("Nie udało się połączyć z serwerem.", true);
  }
}

function handleGoogleLogin() {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
}

function showMessage(message, isError) {
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

  if (isError) {
    messageElement.style.color = "#c0392b";
  } else {
    messageElement.style.color = "#2f6b3a";
  }
}
