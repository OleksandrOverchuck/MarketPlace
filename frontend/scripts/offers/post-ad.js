const POST_AD_API_BASE_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("post-ad-form");

  if (!form) {
    return;
  }

  checkAuthentication();
  loadCategories();

  document.getElementById("title")?.addEventListener("input", updateCounters);

  document
    .getElementById("description")
    ?.addEventListener("input", updateCounters);

  form.addEventListener("submit", handleCreateOffer);

  updateCounters();
});

async function checkAuthentication() {
  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Nie udało się sprawdzić sesji.");
    }
  } catch (error) {
    console.error("Błąd sprawdzania zalogowania:", error);
    window.location.href = "login.html";
  }
}

async function loadCategories() {
  const categorySelect = document.getElementById("category");

  if (!categorySelect) {
    return;
  }

  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/categories`, {
      method: "GET",
      credentials: "include",
    });

    if (!response.ok) {
      throw new Error("Nie udało się pobrać kategorii.");
    }

    const categories = await response.json();

    categorySelect.innerHTML =
      '<option value="" selected disabled>Wybierz kategorię</option>';

    categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = category.id;
      option.textContent = category.name;
      categorySelect.appendChild(option);
    });
  } catch (error) {
    console.error("Błąd pobierania kategorii:", error);

    categorySelect.innerHTML =
      '<option value="" selected disabled>Nie udało się pobrać kategorii</option>';

    showPostAdMessage(
      "Nie udało się pobrać kategorii. Odśwież stronę i spróbuj ponownie.",
      true,
    );
  }
}

async function handleCreateOffer(event) {
  event.preventDefault();

  const title = document.getElementById("title").value.trim();
  const description = document.getElementById("description").value.trim();
  const type = document.getElementById("type").value;
  const categoryId = document.getElementById("category").value;
  const priceInput = document.getElementById("price").value.trim();
  const location = document.getElementById("location")?.value.trim() || null;
  const imageFile = document.getElementById("image")?.files?.[0] || null;
  if (imageFile) {
    const allowedTypes = ["image/png", "image/jpeg"];

    if (!allowedTypes.includes(imageFile.type)) {
      showPostAdMessage("Dozwolone są tylko zdjęcia PNG i JPG.", true);
      return;
    }

    if (imageFile.size > 10 * 1024 * 1024) {
      showPostAdMessage("Zdjęcie nie może być większe niż 10 MB.", true);
      return;
    }
  }
  const submitButton = document.getElementById("post-ad-submit");

  if (!title) {
    showPostAdMessage("Tytuł ogłoszenia jest wymagany.", true);
    return;
  }

  if (!description) {
    showPostAdMessage("Opis ogłoszenia jest wymagany.", true);
    return;
  }

  if (!type) {
    showPostAdMessage("Wybierz typ ogłoszenia.", true);
    return;
  }

  if (!categoryId) {
    showPostAdMessage("Wybierz kategorię.", true);
    return;
  }

  let price = null;

  if (priceInput !== "") {
    const normalizedPrice = priceInput.replace(",", ".");
    const parsedPrice = Number(normalizedPrice);

    if (!Number.isFinite(parsedPrice) || parsedPrice < 0) {
      showPostAdMessage(
        "Cena musi być poprawną liczbą większą lub równą 0.",
        true,
      );
      return;
    }

    price = parsedPrice;
  }

  submitButton.disabled = true;
  showPostAdMessage("Dodawanie ogłoszenia...", false);

  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/offers`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        title,
        description,
        price,
        location,
        type,
        categoryId: Number(categoryId),
      }),
    });

    const data = await readJsonResponse(response);

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się dodać ogłoszenia.",
      );
    }

    if (imageFile) {
      const formData = new FormData();
      formData.append("file", imageFile);

      const imageResponse = await fetch(
        `${POST_AD_API_BASE_URL}/api/offers/${encodeURIComponent(data.id)}/images`,
        {
          method: "POST",
          credentials: "include",
          body: formData,
        },
      );

      if (!imageResponse.ok) {
        const imageData = await readJsonResponse(imageResponse);
        showPostAdMessage(
          `Ogłoszenie zostało dodane, ale zdjęcia nie udało się przesłać. ${imageData?.message || imageData?.error || ""}`.trim(),
          true,
        );
      } else {
        showPostAdMessage(
          "Ogłoszenie i zdjęcie zostały dodane. Za chwilę przejdziesz do listy ogłoszeń.",
          false,
        );
      }
    } else {
      showPostAdMessage(
        "Ogłoszenie zostało dodane. Za chwilę przejdziesz do listy ogłoszeń.",
        false,
      );
    }

    setTimeout(() => {
      window.location.href = "index.html#offers";
    }, 1000);
  } catch (error) {
    console.error("Błąd dodawania ogłoszenia:", error);
    showPostAdMessage(error.message, true);
    submitButton.disabled = false;
  }
}

function updateCounters() {
  const title = document.getElementById("title");
  const description = document.getElementById("description");
  const titleCounter = document.getElementById("title-counter");
  const descriptionCounter = document.getElementById("description-counter");

  if (title && titleCounter) {
    titleCounter.textContent = `${title.value.length}/150`;
  }

  if (description && descriptionCounter) {
    descriptionCounter.textContent = `${description.value.length}/5000`;
  }
}

async function readJsonResponse(response) {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return {
      message: text,
    };
  }
}

function showPostAdMessage(message, isError) {
  const element = document.getElementById("post-ad-message");

  if (!element) {
    return;
  }

  element.textContent = message;
  element.classList.toggle("is-error", Boolean(isError));
  element.classList.toggle("is-success", !isError);
}
