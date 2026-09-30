const SETTINGS_API_BASE_URL = "http://localhost:8080";

let currentSettingsUser = null;

const defaultAvatarSvg = `
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="8" r="4"></circle>
    <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
  </svg>
`;

document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("profile-settings-form");

  if (!form) {
    return;
  }

  loadSettingsUser();

  form.addEventListener("submit", handleProfileSave);

  document
    .getElementById("avatar-input")
    ?.addEventListener("change", handleAvatarUpload);

  document
    .getElementById("remove-avatar-button")
    ?.addEventListener("click", handleAvatarDelete);
});

async function loadSettingsUser() {
  try {
    const response = await fetch(`${SETTINGS_API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Nie udało się pobrać danych użytkownika.");
    }

    const user = await response.json();
    currentSettingsUser = user;

    fillSettingsForm(user);
    renderSettingsAvatar(user.avatarUrl);
  } catch (error) {
    console.error("Błąd pobierania ustawień:", error);
    showSettingsMessage("Nie udało się pobrać danych profilu.", true);
  }
}

function fillSettingsForm(user) {
  document.getElementById("nickname").value = user.nickname ?? "";
  document.getElementById("email").value = user.email ?? "";
  document.getElementById("phone").value = user.phone ?? "";
  document.getElementById("location").value = user.location ?? "";
}

async function handleProfileSave(event) {
  event.preventDefault();

  const nickname = document.getElementById("nickname").value.trim();
  const phone = document.getElementById("phone").value.trim();
  const location = document.getElementById("location").value.trim();
  const button = document.getElementById("save-profile-button");

  if (nickname.length < 3 || nickname.length > 30) {
    showSettingsMessage("Nazwa użytkownika musi mieć od 3 do 30 znaków.", true);
    return;
  }

  button.disabled = true;
  showSettingsMessage("Zapisywanie zmian...", false);

  try {
    const response = await fetch(`${SETTINGS_API_BASE_URL}/api/users/me`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        nickname,
        avatarUrl: currentSettingsUser?.avatarUrl ?? null,
        phone: phone || null,
        location: location || null,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message ||
          data?.error ||
          "Nie udało się zapisać zmian."
      );
    }

    currentSettingsUser = data;
    fillSettingsForm(data);
    renderSettingsAvatar(data.avatarUrl);
    showSettingsMessage("Zmiany zostały zapisane.", false);
  } catch (error) {
    console.error("Błąd zapisywania profilu:", error);
    showSettingsMessage(error.message, true);
  } finally {
    button.disabled = false;
  }
}

async function handleAvatarUpload(event) {
  const file = event.target.files?.[0];

  if (!file) {
    return;
  }

  if (!file.type.startsWith("image/")) {
    showSettingsMessage("Wybierz plik graficzny.", true);
    event.target.value = "";
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    showSettingsMessage("Zdjęcie profilowe nie może być większe niż 5 MB.", true);
    event.target.value = "";
    return;
  }

  showSettingsMessage("Przesyłanie zdjęcia...", false);

  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await fetch(
      `${SETTINGS_API_BASE_URL}/api/users/me/avatar`,
      {
        method: "POST",
        credentials: "include",
        body: formData,
      }
    );

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message ||
          data?.error ||
          "Nie udało się przesłać zdjęcia."
      );
    }

    currentSettingsUser = data;
    renderSettingsAvatar(data.avatarUrl);
    showSettingsMessage("Zdjęcie profilowe zostało zmienione.", false);
  } catch (error) {
    console.error("Błąd przesyłania zdjęcia:", error);
    showSettingsMessage(error.message, true);
  } finally {
    event.target.value = "";
  }
}

async function handleAvatarDelete() {
  if (!currentSettingsUser?.avatarUrl) {
    showSettingsMessage("Nie masz ustawionego zdjęcia profilowego.", true);
    return;
  }

  if (!window.confirm("Czy na pewno chcesz usunąć zdjęcie profilowe?")) {
    return;
  }

  try {
    const response = await fetch(
      `${SETTINGS_API_BASE_URL}/api/users/me/avatar`,
      {
        method: "DELETE",
        credentials: "include",
      }
    );

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message ||
          data?.error ||
          "Nie udało się usunąć zdjęcia."
      );
    }

    currentSettingsUser = data;
    renderSettingsAvatar(data.avatarUrl);
    showSettingsMessage("Zdjęcie profilowe zostało usunięte.", false);
  } catch (error) {
    console.error("Błąd usuwania zdjęcia:", error);
    showSettingsMessage(error.message, true);
  }
}

function renderSettingsAvatar(avatarUrl) {
  const previewElements = [
    document.getElementById("settings-avatar-preview"),
    document.getElementById("avatar-large-preview"),
  ];

  const src = getSettingsAvatarSrc(avatarUrl);

  previewElements.forEach((element) => {
    if (!element) {
      return;
    }

    if (src) {
      element.innerHTML = `
        <img src="${escapeSettingsHtml(src)}" alt="Zdjęcie profilowe" />
      `;
    } else {
      element.innerHTML = defaultAvatarSvg;
    }
  });
}

function getSettingsAvatarSrc(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  return `${SETTINGS_API_BASE_URL}${avatarUrl}`;
}

async function readJsonResponse(response) {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function showSettingsMessage(message, isError) {
  const element = document.getElementById("settings-message");

  if (!element) {
    return;
  }

  element.textContent = message;
  element.classList.toggle("is-error", Boolean(isError));
  element.classList.toggle("is-success", !isError);
}

function escapeSettingsHtml(value) {
  const div = document.createElement("div");
  div.textContent = value;
  return div.innerHTML;
}
