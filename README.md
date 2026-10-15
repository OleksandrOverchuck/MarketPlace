# MarketPlace projekt

The first version 1.1

## Konta administracji (role: USER / MODERATOR / ADMIN)

Przy pierwszym uruchomieniu, jeśli w bazie nie ma żadnego ADMINA, aplikacja tworzy go automatycznie.

| Zmienna środowiskowa | Domyślnie | Opis |
|---|---|---|
| `ADMIN_EMAIL` | `admin@jollycart.local` | login admina |
| `ADMIN_NICKNAME` | `admin` | nick |
| `ADMIN_PASSWORD` | `Admin12345` (tymczasowe) | brak = hasło startowe `Admin12345`; panel wymusza jego zmianę po pierwszym logowaniu |

Panel: `frontend/pages/admin.html` (link w menu „Twoje konto” dla ADMIN / MODERATOR).

| Akcja | USER | MODERATOR | ADMIN |
|---|---|---|---|
| Wyszukiwanie użytkowników (ID / nick / e-mail) | | ✔ | ✔ |
| Ban / odblokowanie (z powodem) | | ✔ (tylko zwykłych użytkowników) | ✔ (także moderatorów) |
| Usuwanie ogłoszeń | własne | ✔ | ✔ |
| Trwałe usunięcie konta z bazy | | | ✔ |
| Nadawanie / odbieranie roli moderatora | | | ✔ |
| Dziennik zdarzeń (audit log) | | | ✔ |
| Zmiana własnego hasła | ✔ | ✔ | ✔ |

Ban ≠ usunięcie: zbanowany użytkownik nie może się zalogować (aktywna sesja jest przerywana od razu), a jego ogłoszenia znikają z publicznej listy, ale dane zostają w bazie i można go odblokować.

