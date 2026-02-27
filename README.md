# FOSStenbuch

**Free Open Source Software Fahrtenbuch für Android**

FOSStenbuch ist ein quelloffenes, digitales Fahrtenbuch für Android – entwickelt für Privatpersonen, Selbstständige und Unternehmen, die ihre Fahrten einfach, datenschutzfreundlich und ohne Cloud-Zwang dokumentieren möchten.

---

## Features

- **Fahrtenerfassung** – Fahrten mit Start-/Zielort, Kilometerstand, Zweck und Fahrzeug anlegen
- **GPS-Tracking** – Standorterfassung im Hintergrund via Foreground Service
- **Fahrzeugverwaltung** – Mehrere Fahrzeuge verwalten
- **Fahrzwecke** – Individuelle Fahrzwecke (dienstlich, privat, etc.) definieren
- **Gespeicherte Orte** – Häufig genutzte Orte speichern und wiederverwenden
- **Kilometerstand-Tracking** – Lückenlose Dokumentation der Kilometerstände
- **Statistiken** – Auswertungen und Übersichten über Fahrten und Kilometer
- **Export** – Fahrten als PDF oder CSV exportieren und teilen
- **Backup & Restore** – Lokale Datensicherung und Wiederherstellung
- **Erinnerungen** – Benachrichtigungen zur Fahrtenerfassung
- **Audit-Log** – Nachvollziehbare Änderungshistorie der Fahrten
- **Vorlagen** – Wiederkehrende Fahrten als Vorlage speichern
- **Material Design** – Moderne Android-Oberfläche mit Material 3 Komponenten
- **Offline-first** – Alle Daten werden lokal auf dem Gerät gespeichert (Room-Datenbank)
- **Kein Tracking, keine Werbung, keine Cloud** – Volle Datenkontrolle

---

## Screenshots

<!-- Screenshots hier einfügen, z.B.:
<p float="left">
  <img src="docs/screenshots/trips.png" width="200" />
  <img src="docs/screenshots/stats.png" width="200" />
  <img src="docs/screenshots/export.png" width="200" />
</p>
-->

*Screenshots folgen in Kürze.*

---

## Tech Stack

| Bereich              | Technologie                                      |
|----------------------|--------------------------------------------------|
| Sprache              | Kotlin                                           |
| Min SDK              | 26 (Android 8.0)                                 |
| Target SDK           | 34 (Android 14)                                  |
| Architektur          | MVVM + Clean Architecture (Data / Domain / UI)   |
| Dependency Injection | Hilt (Dagger)                                    |
| Datenbank            | Room (SQLite)                                    |
| Navigation           | Jetpack Navigation + SafeArgs                    |
| Asynchronität        | Kotlin Coroutines + Flow                         |
| UI                   | View Binding + Material Design Components        |
| Paginierung          | Paging 3                                         |
| Standort             | Google Play Services Location                    |
| Einstellungen        | DataStore Preferences                            |
| Logging              | Timber                                           |
| Testing              | JUnit 4, MockK, Coroutines Test                  |
| Code Coverage        | Kover                                            |
| CI/CD                | GitHub Actions                                   |
| Build System         | Gradle (Kotlin DSL)                              |

---

## Voraussetzungen

- **Android Studio** Hedgehog (2023.1.1) oder neuer
- **JDK 17**
- **Android SDK** mit API Level 34
- **Gradle** 8.x (wird über den Gradle Wrapper mitgeliefert)

---

## Installation & Build

### Projekt klonen

```bash
git clone https://github.com/garske-systems/FOSStenbuch.git
cd FOSStenbuch
```

### Debug-Build erstellen

```bash
./gradlew assembleDebug
```

Die APK liegt anschließend unter `app/build/outputs/apk/debug/app-debug.apk`.

### Tests ausführen

```bash
./gradlew test
```

### Code Coverage Report

```bash
./gradlew koverHtmlReport
```

---

## Projektstruktur

```
app/src/main/java/de/fosstenbuch/
├── data/                   # Datenschicht
│   ├── local/              # Room-Datenbank, DAOs
│   ├── model/              # Datenmodelle (Trip, Vehicle, TripPurpose, ...)
│   └── repository/         # Repository-Implementierungen
├── di/                     # Hilt Dependency Injection Module
├── domain/                 # Geschäftslogik
│   ├── backup/             # Backup & Restore
│   ├── export/             # PDF/CSV-Export
│   ├── notification/       # Erinnerungen
│   ├── service/            # Location Tracking Service
│   ├── usecase/            # Use Cases (Trip, Vehicle, Stats, ...)
│   └── validation/         # Eingabevalidierung
├── ui/                     # Präsentationsschicht
│   ├── common/             # Gemeinsame UI-Komponenten
│   ├── export/             # Export-Screen
│   ├── locations/          # Gespeicherte Orte
│   ├── main/               # MainActivity
│   ├── mileage/            # Kilometerstand
│   ├── purposes/           # Fahrzwecke
│   ├── settings/           # Einstellungen
│   ├── stats/              # Statistiken
│   ├── trips/              # Fahrten
│   └── vehicles/           # Fahrzeuge
├── utils/                  # Hilfsfunktionen
└── MainApplication.kt      # Application-Klasse
```

---

## Berechtigungen

| Berechtigung                    | Verwendung                                             |
|---------------------------------|--------------------------------------------------------|
| `ACCESS_FINE_LOCATION`          | GPS-Standorterfassung für Fahrten                      |
| `ACCESS_COARSE_LOCATION`        | Ungefähre Standortbestimmung als Fallback              |
| `FOREGROUND_SERVICE`            | GPS-Tracking im Hintergrund                            |
| `FOREGROUND_SERVICE_LOCATION`   | Standort-Foreground-Service (ab Android 14)            |
| `POST_NOTIFICATIONS`            | Fahrt-Erinnerungen anzeigen                            |
| `RECEIVE_BOOT_COMPLETED`        | Erinnerungen nach Geräteneustart wiederherstellen      |
| `SCHEDULE_EXACT_ALARM`          | Exakte Erinnerungszeitpunkte planen                    |

---

## Mitwirken (Contributing)

Beiträge sind herzlich willkommen! So kannst du mitmachen:

1. **Fork** des Repositories erstellen
2. **Feature-Branch** anlegen: `git checkout -b feature/mein-feature`
3. **Änderungen committen**: `git commit -m "feat: Beschreibung der Änderung"`
4. **Branch pushen**: `git push origin feature/mein-feature`
5. **Pull Request** öffnen

### Richtlinien

- Halte dich an die bestehende Projektstruktur (MVVM + Clean Architecture)
- Schreibe Unit Tests für neue Geschäftslogik
- Verwende [Conventional Commits](https://www.conventionalcommits.org/) für Commit-Messages
- Stelle sicher, dass `./gradlew test` erfolgreich durchläuft

---

## Roadmap

- [ ] F-Droid Veröffentlichung
- [x] Finanzamt-konformer PDF-Export
- [ ] Dunkelmodus (Dark Theme)
- [ ] Widget für Schnellerfassung
- [ ] Automatische Fahrterkennung
- [ ] Multi-Language Support (Englisch)

---

## Lizenz

Dieses Projekt ist unter der **MIT-Lizenz** lizenziert – siehe [LICENSE](LICENSE) für Details.

```
MIT License

Copyright (c) 2023 FOSStenbuch Contributors
```

---

## Maintainer

Dieses Projekt wird betreut und hauptsächlich entwickelt von:

**[Garske Systems](https://garske-systems.de)**

---

## Kontakt

- **Website:** [garske-systems.de](https://garske-systems.de)
- **Issues:** [GitHub Issues](https://github.com/garske-systems/FOSStenbuch/issues)

---

<p align="center">
  Made with ❤️ as Free Open Source Software.
  Dieses Projekt wurde mit KI-unterstützung entwickelt.
</p>
