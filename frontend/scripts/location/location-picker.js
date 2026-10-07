/*
 * WYBÓR LOKALIZACJI (Google Maps)
 *
 * Każde pole <input data-location-picker> dostaje:
 *   - podpowiedzi miejscowości w trakcie pisania (Places API (New)),
 *   - przycisk "Użyj mojej lokalizacji" (geolokalizacja przeglądarki
 *     + Geocoding API, żeby zamienić współrzędne na nazwę miasta).
 *
 * Jeśli nie ma klucza API albo Google się nie załaduje, pole zostaje
 * zwykłym polem tekstowym - formularze dalej działają.
 */
(function () {
  "use strict";

  const config = window.JOLLYCART_MAPS || {};

  const API_KEY = String(config.apiKey || "");
  const LANGUAGE = config.language || "pl";
  const COUNTRY_CODES = config.countryCodes || ["pl"];
  const PLACE_TYPES = Array.isArray(config.placeTypes)
    ? config.placeTypes
    : ["(cities)"];
  const BIAS_RADIUS = Math.min(Number(config.biasRadiusMeters) || 50000, 50000);
  const MAX_SUGGESTIONS = Number(config.maxSuggestions) || 5;

  const MIN_CHARS = 2;
  const DEBOUNCE_MS = 250;

  const hasApiKey = API_KEY.length > 10 && !API_KEY.startsWith("WKLEJ");

  const ICONS = {
    locate:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.5"/><circle cx="12" cy="12" r="7.5"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/></svg>',
    pin: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>',
  };

  let userCoords = null;
  let mapsPromise = null;
  let librariesPromise = null;
  let authFailed = false;

  /* =========================
     ŁADOWANIE GOOGLE MAPS
     ========================= */

  // Skrypt Google ładujemy dopiero przy pierwszym użyciu pola,
  // żeby nie obciążać stron i nie generować zbędnych zapytań.
  function loadGoogleMaps() {
    if (mapsPromise) {
      return mapsPromise;
    }

    mapsPromise = new Promise((resolve, reject) => {
      if (window.google && window.google.maps && window.google.maps.importLibrary) {
        resolve(window.google.maps);
        return;
      }

      const callbackName = "__jollycartMapsReady";

      window[callbackName] = () => {
        delete window[callbackName];
        resolve(window.google.maps);
      };

      // Google wywołuje tę funkcję, gdy klucz jest błędny / zablokowany.
      window.gm_authFailure = () => {
        authFailed = true;
      };

      const script = document.createElement("script");

      script.src =
        "https://maps.googleapis.com/maps/api/js" +
        `?key=${encodeURIComponent(API_KEY)}` +
        "&v=weekly&loading=async" +
        `&language=${encodeURIComponent(LANGUAGE)}` +
        `&callback=${callbackName}`;

      script.async = true;

      script.onerror = () => {
        reject(new Error("Nie udało się załadować Google Maps."));
      };

      document.head.appendChild(script);
    }).catch((error) => {
      mapsPromise = null;

      throw error;
    });

    return mapsPromise;
  }

  function loadLibraries() {
    if (!librariesPromise) {
      librariesPromise = loadGoogleMaps()
        .then(async (maps) => {
          const [places, geocoding] = await Promise.all([
            maps.importLibrary("places"),
            maps.importLibrary("geocoding"),
          ]);

          return { places, geocoding };
        })
        .catch((error) => {
          librariesPromise = null;

          throw error;
        });
    }

    return librariesPromise;
  }

  /* =========================
     GEOLOKALIZACJA
     ========================= */

  function getBrowserPosition() {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error("Ta przeglądarka nie obsługuje geolokalizacji."));
        return;
      }

      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 5 * 60 * 1000,
      });
    });
  }

  function geolocationErrorMessage(error) {
    if (error && error.code === 1) {
      return "Brak zgody na lokalizację. Zezwól na nią w ustawieniach przeglądarki albo wpisz miasto ręcznie.";
    }

    if (error && error.code === 3) {
      return "Ustalanie lokalizacji trwa zbyt długo. Spróbuj ponownie.";
    }

    if (error && error.code === 2) {
      return "Nie udało się ustalić Twojej lokalizacji.";
    }

    return (error && error.message) || "Nie udało się ustalić Twojej lokalizacji.";
  }

  // Zamienia współrzędne na nazwę miejscowości (Geocoding API).
  async function reverseGeocodeCity(coords) {
    const { geocoding } = await loadLibraries();

    const geocoder = new geocoding.Geocoder();

    const { results } = await geocoder.geocode({
      location: coords,
      language: LANGUAGE,
    });

    const preferredTypes = [
      "locality",
      "postal_town",
      "administrative_area_level_3",
      "administrative_area_level_2",
    ];

    for (const type of preferredTypes) {
      for (const result of results || []) {
        const component = (result.address_components || []).find((item) =>
          item.types.includes(type),
        );

        if (component) {
          return component.long_name;
        }
      }
    }

    return null;
  }

  /* =========================
     PODPOWIEDZI MIEJSCOWOŚCI
     ========================= */

  async function fetchCitySuggestions(text, state) {
    const { places } = await loadLibraries();

    if (!state.sessionToken) {
      state.sessionToken = new places.AutocompleteSessionToken();
    }

    const request = {
      input: text,
      sessionToken: state.sessionToken,
      language: LANGUAGE,
      includedRegionCodes: COUNTRY_CODES,
    };

    if (PLACE_TYPES.length > 0) {
      request.includedPrimaryTypes = PLACE_TYPES;
    }

    // Jeśli znamy lokalizację użytkownika - wyniki w pobliżu są wyżej.
    if (userCoords) {
      request.locationBias = {
        center: userCoords,
        radius: BIAS_RADIUS,
      };
    }

    const { suggestions } =
      await places.AutocompleteSuggestion.fetchAutocompleteSuggestions(
        request,
      );

    return (suggestions || [])
      .map((suggestion) => suggestion.placePrediction)
      .filter(Boolean)
      .slice(0, MAX_SUGGESTIONS)
      .map((prediction) => ({
        main:
          (prediction.mainText && prediction.mainText.text) ||
          prediction.text.text,
        secondary:
          (prediction.secondaryText && prediction.secondaryText.text) || "",
      }));
  }

  /* =========================
     POLE Z PODPOWIEDZIAMI
     ========================= */

  let pickerCounter = 0;

  function enhanceInput(input) {
    pickerCounter += 1;

    const listId = `location-picker-list-${pickerCounter}`;

    const state = {
      rows: [],
      activeIndex: -1,
      sessionToken: null,
      debounceTimer: null,
      requestId: 0,
      busy: false,
    };

    const wrapper = document.createElement("div");

    wrapper.className = "location-picker";

    input.parentNode.insertBefore(wrapper, input);

    wrapper.appendChild(input);

    input.classList.add("location-picker-input");

    input.setAttribute("autocomplete", "off");
    input.setAttribute("role", "combobox");
    input.setAttribute("aria-autocomplete", "list");
    input.setAttribute("aria-expanded", "false");
    input.setAttribute("aria-controls", listId);

    const locateButton = document.createElement("button");

    locateButton.type = "button";
    locateButton.className = "location-picker-locate";
    locateButton.setAttribute("aria-label", "Użyj mojej lokalizacji");
    locateButton.title = "Użyj mojej lokalizacji";
    locateButton.innerHTML = ICONS.locate;

    const list = document.createElement("div");

    list.className = "location-picker-list";
    list.id = listId;
    list.setAttribute("role", "listbox");
    list.hidden = true;

    wrapper.append(locateButton, list);

    /* ---------- pomocnicze ---------- */

    function setValue(value) {
      input.value = value;

      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function closeList() {
      list.hidden = true;
      list.innerHTML = "";

      state.rows = [];
      state.activeIndex = -1;

      input.setAttribute("aria-expanded", "false");
      input.removeAttribute("aria-activedescendant");
    }

    function setActive(index) {
      const options = list.querySelectorAll('[role="option"]');

      state.activeIndex = index;

      options.forEach((option, optionIndex) => {
        const isActive = optionIndex === index;

        option.classList.toggle("is-active", isActive);
        option.setAttribute("aria-selected", isActive ? "true" : "false");

        if (isActive) {
          input.setAttribute("aria-activedescendant", option.id);

          option.scrollIntoView({ block: "nearest" });
        }
      });
    }

    function renderList(rows, statusText, showAttribution) {
      state.rows = rows;
      state.activeIndex = -1;

      list.innerHTML = "";

      rows.forEach((row, index) => {
        const option = document.createElement("div");

        option.className = `location-picker-option is-${row.type}`;
        option.id = `${listId}-option-${index}`;
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", "false");

        if (row.type === "locate") {
          option.innerHTML = `${ICONS.locate}<span class="location-picker-main">Użyj mojej lokalizacji</span>`;
        } else {
          const text = document.createElement("span");

          text.className = "location-picker-text";

          const main = document.createElement("span");

          main.className = "location-picker-main";
          main.textContent = row.main;

          text.appendChild(main);

          if (row.secondary) {
            const secondary = document.createElement("span");

            secondary.className = "location-picker-secondary";
            secondary.textContent = row.secondary;

            text.appendChild(secondary);
          }

          option.innerHTML = ICONS.pin;

          option.appendChild(text);
        }

        // mousedown + preventDefault: pole nie traci fokusu przed kliknięciem.
        option.addEventListener("mousedown", (event) => {
          event.preventDefault();
        });

        option.addEventListener("click", () => selectRow(index));

        option.addEventListener("mousemove", () => {
          if (state.activeIndex !== index) {
            setActive(index);
          }
        });

        list.appendChild(option);
      });

      if (statusText) {
        const status = document.createElement("div");

        status.className = "location-picker-status";
        status.setAttribute("role", "status");
        status.textContent = statusText;

        list.appendChild(status);
      }

      if (showAttribution) {
        const attribution = document.createElement("div");

        attribution.className = "location-picker-attribution";
        attribution.textContent = "Powered by Google";

        list.appendChild(attribution);
      }

      list.hidden = false;

      input.setAttribute("aria-expanded", "true");
      input.removeAttribute("aria-activedescendant");
    }

    function selectRow(index) {
      const row = state.rows[index];

      if (!row) {
        return;
      }

      if (row.type === "locate") {
        useMyLocation();

        return;
      }

      setValue(row.main);

      // Po wybraniu miejsca kończy się "sesja" Google - następna dostanie nowy token.
      state.sessionToken = null;

      closeList();
    }

    function emptyRows() {
      return navigator.geolocation ? [{ type: "locate" }] : [];
    }

    /* ---------- wyszukiwanie ---------- */

    async function search(text) {
      const requestId = ++state.requestId;

      try {
        const rows = (await fetchCitySuggestions(text, state)).map((item) => ({
          type: "place",
          ...item,
        }));

        // Odpowiedź przyszła spóźniona (użytkownik już wpisał coś innego).
        if (requestId !== state.requestId || document.activeElement !== input) {
          return;
        }

        if (rows.length === 0) {
          renderList([], "Nie znaleziono pasujących miejscowości.", true);
        } else {
          renderList(rows, "", true);
        }
      } catch (error) {
        console.error("Błąd podpowiedzi lokalizacji:", error);

        if (requestId === state.requestId) {
          renderList(
            [],
            authFailed
              ? "Klucz Google Maps jest nieprawidłowy lub zablokowany. Wpisz miasto ręcznie."
              : "Podpowiedzi są chwilowo niedostępne. Wpisz miasto ręcznie.",
            false,
          );
        }
      }
    }

    function handleInput() {
      clearTimeout(state.debounceTimer);

      const text = input.value.trim();

      if (text.length < MIN_CHARS) {
        state.requestId += 1;

        const rows = emptyRows();

        if (rows.length && document.activeElement === input) {
          renderList(rows, "", false);
        } else {
          closeList();
        }

        return;
      }

      state.debounceTimer = setTimeout(() => search(text), DEBOUNCE_MS);
    }

    /* ---------- "użyj mojej lokalizacji" ---------- */

    async function useMyLocation() {
      if (state.busy) {
        return;
      }

      state.busy = true;

      locateButton.classList.add("is-loading");
      locateButton.disabled = true;

      state.requestId += 1;

      renderList([], "Ustalam Twoją lokalizację…", false);

      try {
        const position = await getBrowserPosition();

        userCoords = {
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        };

        const city = await reverseGeocodeCity(userCoords);

        if (!city) {
          renderList(
            [],
            "Nie udało się rozpoznać miejscowości. Wpisz ją ręcznie.",
            false,
          );

          return;
        }

        setValue(city);

        state.sessionToken = null;

        closeList();
      } catch (error) {
        console.error("Błąd ustalania lokalizacji:", error);

        let message;

        if (error && typeof error.code === "number") {
          // Błąd geolokalizacji przeglądarki (zgoda, timeout, brak pozycji).
          message = geolocationErrorMessage(error);
        } else if (authFailed) {
          message = "Klucz Google Maps jest nieprawidłowy lub zablokowany.";
        } else {
          message =
            "Nie udało się ustalić miejscowości. Wpisz ją ręcznie.";
        }

        renderList([], message, false);
      } finally {
        state.busy = false;

        locateButton.classList.remove("is-loading");
        locateButton.disabled = false;
      }
    }

    /* ---------- zdarzenia ---------- */

    input.addEventListener("input", handleInput);

    // Wczytujemy Google dopiero, gdy ktoś zacznie używać pola.
    input.addEventListener("focus", () => {
      loadLibraries().catch(() => {});

      if (input.value.trim().length < MIN_CHARS) {
        const rows = emptyRows();

        if (rows.length) {
          renderList(rows, "", false);
        }
      }
    });

    input.addEventListener("click", () => {
      if (list.hidden && input.value.trim().length < MIN_CHARS) {
        const rows = emptyRows();

        if (rows.length) {
          renderList(rows, "", false);
        }
      }
    });

    input.addEventListener("keydown", (event) => {
      const optionCount = state.rows.length;

      if (event.key === "ArrowDown" && !list.hidden && optionCount) {
        event.preventDefault();

        setActive((state.activeIndex + 1) % optionCount);
      } else if (event.key === "ArrowUp" && !list.hidden && optionCount) {
        event.preventDefault();

        setActive((state.activeIndex - 1 + optionCount) % optionCount);
      } else if (
        event.key === "Enter" &&
        !list.hidden &&
        state.activeIndex >= 0
      ) {
        // Enter wybiera podpowiedź, a nie wysyła formularza.
        event.preventDefault();

        selectRow(state.activeIndex);
      } else if (event.key === "Escape" && !list.hidden) {
        event.preventDefault();

        closeList();
      }
    });

    locateButton.addEventListener("click", () => {
      input.focus();

      useMyLocation();
    });

    wrapper.addEventListener("focusout", (event) => {
      if (!wrapper.contains(event.relatedTarget)) {
        clearTimeout(state.debounceTimer);

        state.requestId += 1;

        closeList();
      }
    });
  }

  function init() {
    const inputs = document.querySelectorAll("input[data-location-picker]");

    if (!inputs.length) {
      return;
    }

    if (!hasApiKey) {
      console.warn(
        "[JollyCart] Brak klucza Google Maps w scripts/location/maps-config.js - pola lokalizacji działają jako zwykłe pola tekstowe.",
      );

      return;
    }

    inputs.forEach(enhanceInput);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
