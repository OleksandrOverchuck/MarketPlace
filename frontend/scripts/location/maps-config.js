/*
 * KONFIGURACJA GOOGLE MAPS
 *
 * 1. Wklej tutaj swój klucz API (patrz instrukcja: Google Cloud Console).
 * 2. Klucz MUSI mieć ograniczenie "HTTP referrers" (np. http://localhost:5501/*
 *    oraz adres Twojej strony po wdrożeniu) i dostęp tylko do:
 *      - Maps JavaScript API
 *      - Places API (New)
 *      - Geocoding API
 *
 * Dopóki klucz to "WKLEJ_TUTAJ_KLUCZ_API", pola lokalizacji działają jak
 * zwykłe pola tekstowe (nic się nie psuje).
 */
window.JOLLYCART_MAPS = {
  apiKey: "WKLEJ_TUTAJ_KLUCZ_API",

  language: "pl",

  // Kraje, w których szukamy miejscowości (kody ISO, małymi literami).
  countryCodes: ["pl"],

  // "(cities)" = tylko miejscowości. Pusta lista [] = wszystkie miejsca
  // (ulice, adresy, punkty).
  placeTypes: ["(cities)"],

  // Promień (w metrach) preferowania wyników blisko użytkownika.
  // Maksymalnie 50000 (limit Google).
  biasRadiusMeters: 50000,

  // Ile podpowiedzi pokazać.
  maxSuggestions: 5,
};
