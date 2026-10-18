# FOSStenbuch

[![Android CI](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/actions/workflows/android-ci.yml/badge.svg)](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/actions/workflows/android-ci.yml)

**Free Open Source Software Fahrtenbuch für Android**

FOSStenbuch ist ein quelloffenes, digitales Fahrtenbuch für Android – entwickelt für Privatpersonen, Selbstständige und Unternehmen, die ihre Fahrten einfach, datenschutzfreundlich und ohne Cloud-Zwang dokumentieren möchten.

---

## Features

- **Fahrten starten und beenden** – mit Start-/Zielort, Kilometerstand, Kategorie, Geschäftspartner und Fahrzeug; neue Fahrten werden während der Fahrt erfasst, nicht nachträglich angelegt. Änderungen an abgeschlossenen Fahrten werden protokolliert; löschen lassen sie sich nicht, nur stornieren (mit Grund)
- **GPS-Tracking** – Gefahrene Strecke wird während der Fahrt per GPS mitgeschrieben (als Schätzung; maßgeblich ist der Km-Stand)
- **Automatische Fahrterkennung** – Verbindet sich das Handy mit dem Auto-Bluetooth, wird die Fahrt automatisch aufgezeichnet und kann später geprüft und übernommen werden
- **Fahrzeugverwaltung** – Mehrere Fahrzeuge verwalten
- **Kategorien** – Eigene Kategorien (beruflich, privat usw.) definieren
- **Gespeicherte Orte** – Häufig genutzte Orte speichern und wiederverwenden
- **Kilometerstände** – Km-Stand bei Start und Ende jeder Fahrt; das ausführliche PDF zeigt Lücken zwischen den Fahrten und nicht erfasste Kilometer
- **Statistiken** – Auswertungen und Übersichten über Fahrten und Kilometer, Rechner für die Entfernungspauschale (deutsches Steuerrecht, zur Orientierung)
- **Export** – CSV als Datenexport, PDF und ausführliches PDF je Fahrzeug (mit Km-Ständen, Änderungsprotokoll, Siegeln und Verlauf)
- **Backup & Restore** – Lokale Datensicherung und Wiederherstellung; Einspielen und „Alle Daten löschen“ werden im Verlauf vermerkt
- **Erinnerungen** – Benachrichtigungen zur Fahrtenerfassung
- **Bearbeitungssperre** – je Fahrzeug, nicht umkehrbar: abgeschlossene Fahrten lassen sich nur noch stornieren, die Fahrzeugdaten sind gesperrt; eine Hash-Kette prüft Backups beim Wiederherstellen
- **Siegel, Signatur, Zeitstempel** – jede Fahrt, die beendet, übernommen, geändert oder storniert wird, bekommt ein Siegel (SHA-256), optional signiert mit einem eigenen Schlüssel (ECDSA P-256) und bestätigt durch den Zeitstempel eines unabhängigen Dienstes (RFC 3161, standardmäßig aus); das ausführliche PDF und Backups lassen sich auf der [Prüfseite](https://garske-systems.github.io/FOSStenbuch/pruefen.html) im Browser prüfen
- **Vorlagen** – Wiederkehrende Fahrten als Vorlage speichern
- **Material Design** – Moderne Android-Oberfläche mit Material 3 Komponenten
- **Offline-first** – Alle Daten werden lokal auf dem Gerät gespeichert (Room-Datenbank)
- **Keine Werbung, keine Analyse- oder Tracking-Dienste, keine Cloud** – Volle Datenkontrolle

---

## Für das Finanzamt

FOSStenbuch hilft, ein Fahrtenbuch ordentlich zu führen. Ob das Finanzamt es anerkennt, hängt davon ab, wie Sie es
führen – eine Garantie gibt die App nicht, und dies ist keine Steuerberatung. Im Zweifel fragen Sie Ihre
Steuerberaterin oder Ihren Steuerberater. Bewährt hat sich:

1. **Bearbeitungssperre vor der ersten Fahrt einschalten** (Fahrzeug bearbeiten › „Bearbeitungssperre (nicht umkehrbar)“).
2. **Fahrten live erfassen:** beim Losfahren starten, bei der Ankunft beenden – nicht erst abends oder am Monatsende.
3. **Km-Stand vom Tacho ablesen**, bei Start und Ende. Die GPS-Strecke ist nur eine Schätzung.
4. **Alle Fahrten erfassen**, auch private und die Wege zwischen Wohnung und erster Tätigkeitsstätte (eigene Kategorie, z. B. „Arbeitsweg“).
5. **Konkrete Kategorien und Geschäftspartner:** Bei beruflichen Fahrten Geschäftspartner bzw. Kunde und Anlass angeben, bei Umwegen die Route.
6. **Automatisch erkannte Fahrten zeitnah übernehmen**, spätestens nach 7 Tagen – als Unternehmer am besten am selben Tag.
7. **Nichts löschen:** Fehler stornieren (mit Grund) bzw. bei Fahrzeugen ohne Sperre korrigieren – beides wird protokolliert.
8. **Netzwerkzeit einschalten** (Android: Datum und Uhrzeit automatisch einstellen), denn alle Zeiten stammen von der Uhr des Geräts.
9. **Ausführliches PDF je Fahrzeug und Jahr** exportieren („PDF (ausführlich, mit Änderungsprotokoll)“).
10. **Backups extern aufbewahren**, nicht nur auf dem Handy – in Deutschland 10 Jahre, in Österreich 7 Jahre.
11. **Kostenbelege aufbewahren** (Tanken, Werkstatt, Versicherung).

Optional: Zeitstempel einschalten und einen Signaturschlüssel erzeugen (und die Schlüsseldatei sicher aufbewahren).
Dann zeigt die [Prüfseite](https://garske-systems.github.io/FOSStenbuch/pruefen.html) allein mit dem ausführlichen
PDF, dass die Fahrten seit dem Siegeln unverändert sind und wann es die Siegel schon gab – Privatfahrten mit Notiz
oder Geschäftspartner nur mit dem Backup, denn diese Angaben stehen nicht im PDF. Was diese Funktionen belegen und was
nicht, steht in den [Nutzungsbedingungen](docs/nutzungsbedingungen.md), Ziffer 4.

---

## Screenshots

<p float="left">
  <img src="screenshots/a.png" width="250" />
  <img src="screenshots/b.png" width="250" />
  <img src="screenshots/c.png" width="250" />
</p>

---

## Tech Stack

| Bereich              | Technologie                                      |
|----------------------|--------------------------------------------------|
| Sprache              | Kotlin                                           |
| Min SDK              | 26 (Android 8.0)                                 |
| Target SDK           | 36 (Android 16)                                  |
| Architektur          | Bewusst schlicht (KISS): Daten / Logik / UI      |
| Datenbank            | Room (SQLite), ein DAO                           |
| Navigation           | Jetpack Navigation                               |
| Asynchronität        | Kotlin Coroutines + Flow                         |
| UI                   | View Binding + Material 3                        |
| Standort             | Android LocationManager (AOSP, ohne Google-Dienste) |
| Einstellungen        | DataStore Preferences                            |
| Kryptografie         | Java-Standard-APIs (ECDSA P-256, AES-GCM, PBKDF2) und Android Keystore, keine Zusatzbibliothek |
| Testing              | JUnit 4                                          |
| CI/CD                | GitHub Actions                                   |
| Build System         | Gradle (Kotlin DSL)                              |

---

## Voraussetzungen

- **JDK 17 oder neuer** (getestet bis JDK 25)
- **Android SDK** mit API Level 36
- **Gradle** 9.x (wird über den Gradle Wrapper mitgeliefert)
- Optional: Android Studio (aktuelle Version mit AGP-9-Unterstützung)

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
Debug-Builds haben die App-ID `de.garsys.fosstenbuch.debug` und lassen sich neben der Release-Version installieren.

### Tests ausführen

```bash
./gradlew test
```

---

## Projektstruktur

```
app/src/main/java/de/fosstenbuch/
├── App.kt              # Application: Dunkelmodus, Erinnerung neu planen
├── data/               # Room-Entities, ein DAO, Datenbank + Migrationen, Einstellungen (DataStore)
├── logic/              # Geschäftslogik ohne UI
│   ├── Trips.kt        # Fahrten speichern: Validierung, Sperre, Änderungsprotokoll, Siegel, Hash-Kette
│   ├── Seals.kt        # Siegel abgeschlossener Fahrten (Format muss zu docs/pruefen.js passen)
│   ├── Keys.kt         # Optionaler Signaturschlüssel, Schlüsseldatei, PDF-Signaturzeile
│   ├── Tsa.kt          # Optionale Zeitstempel (RFC 3161)
│   ├── Backup.kt       # Backup & Wiederherstellung (JSON), Verlauf
│   ├── Geo.kt          # Entfernungen, Standort, Adresssuche (OpenStreetMap Nominatim)
│   ├── Mileage.kt      # Kilometer- und Entfernungspauschale
│   ├── Fmt.kt          # Deutsche Zahlen- und Datumsformate
│   └── export/         # CSV, PDF, ausführliches PDF mit Prüfdaten
├── tracking/           # GPS-Aufzeichnung, automatische Fahrterkennung (Bluetooth), Erinnerung
└── ui/                 # MainActivity + ein Paket je Bildschirmgruppe, common/ = gemeinsame Helfer

docs/                   # GitHub Pages: Rechtstexte und Prüfseite (pruefen.html, pruefen.js)
```

---

## Berechtigungen

| Berechtigung                                   | Verwendung                                                        |
|------------------------------------------------|-------------------------------------------------------------------|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS-Strecke und Ortsvorschläge                                 |
| `ACCESS_BACKGROUND_LOCATION`                   | GPS bei automatisch erkannten Fahrten (App nicht geöffnet)       |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Aufzeichnung als Vordergrunddienst mit Benachrichtigung     |
| `BLUETOOTH_CONNECT` (bzw. `BLUETOOTH` bis Android 11) | Erkennen, wann sich das Auto-Bluetooth verbindet/trennt     |
| `POST_NOTIFICATIONS`                           | Erinnerung und Aufzeichnungs-Benachrichtigung                    |
| `RECEIVE_BOOT_COMPLETED`                       | Erinnerung nach Neustart wieder planen; unterbrochene automatische Aufzeichnung abschließen |
| `INTERNET`                                     | Adresssuche und Ortsnamen über OpenStreetMap; optional Zeitstempel (RFC 3161) |

---

## Mitwirken (Contributing)

Beiträge sind herzlich willkommen! So kannst du mitmachen:

1. **Fork** des Repositories erstellen
2. **Feature-Branch** anlegen: `git checkout -b feature/mein-feature`
3. **Änderungen committen**: `git commit -m "feat: Beschreibung der Änderung"`
4. **Branch pushen**: `git push origin feature/mein-feature`
5. **Pull Request** öffnen

### Richtlinien

- Halte es einfach (KISS): so wenig Code wie möglich, keine unnötigen Schichten; Kommentare auf Deutsch
- Schreibe Unit Tests für neue Geschäftslogik
- Verwende [Conventional Commits](https://www.conventionalcommits.org/) für Commit-Messages
- Stelle sicher, dass `./gradlew test` erfolgreich durchläuft

---

## Roadmap

- [ ] F-Droid Veröffentlichung
- [ ] Google Play Veröffentlichung
- [x] PDF-Export im Fahrtenbuch-Format
- [x] Dunkelmodus (Dark Theme)
- [ ] Widget für Schnellerfassung
- [x] Automatische Fahrterkennung (Bluetooth)
- [ ] Multi-Language Support (Englisch)

---

## Datenschutz und Rechtliches

- [Datenschutzerklärung](docs/datenschutz.md) – alle Daten werden nur auf dem Gerät gespeichert; online gehen nur die Adresssuche und Ortsnamen automatisch erkannter Fahrten (OpenStreetMap Nominatim) und – falls eingeschaltet – Zeitstempel-Anfragen, bei denen nur ein SHA-256-Fingerabdruck an den gewählten Dienst geht
- [Nutzungsbedingungen](docs/nutzungsbedingungen.md)
- [Prüfseite](https://garske-systems.github.io/FOSStenbuch/pruefen.html) – prüft das ausführliche PDF und Backups, nur im Browser
- [Impressum](https://garske-systems.de/impressum)

---

## Lizenz

Dieses Projekt ist unter der **MIT-Lizenz** lizenziert – siehe [LICENSE](LICENSE) für Details.

```
MIT License

Copyright (c) 2026 FOSStenbuch Contributors
```

### Drittinhalte

- **Icons:** [Material Icons](https://github.com/google/material-design-icons) von Google, [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- **Adressdaten:** Adresssuche und Ortsnamen kommen von [OpenStreetMap](https://www.openstreetmap.org) über [Nominatim](https://nominatim.org) – Daten © OpenStreetMap-Mitwirkende, [Open Database License (ODbL)](https://www.openstreetmap.org/copyright). Die App zeigt diese Quellenangabe in der Adresssuche und im Seitenmenü, sendet einen eindeutigen User-Agent, stellt höchstens eine Anfrage pro Sekunde und sucht nur auf Knopfdruck (keine Autovervollständigung).
- **Datenschutz:** Für die Ortsnamen automatisch erkannter Fahrten sendet die App die Koordinaten von Fahrtbeginn und -ende (auf ca. 10 m gerundet) an nominatim.openstreetmap.org – außer ein gespeicherter Ort liegt in 500 m Nähe. Es gilt die [Datenschutzerklärung der OSM Foundation](https://osmfoundation.org/wiki/Privacy_Policy).
- **Zeitstempel:** Nur wenn eingeschaltet (standardmäßig aus), holt die App Zeitstempel nach RFC 3161 bei einem wählbaren Dienst – vorgegeben sind [FreeTSA](https://freetsa.org), Sectigo Qualified (eIDAS) und [Sectigo](https://www.sectigo.com), eine eigene Adresse ist möglich. Übertragen werden je Siegel nur ein SHA-256-Fingerabdruck, eine Zufallszahl und die Kennung der App; höchstens eine Anfrage alle 15 Sekunden.
- **Bibliotheken:** AndroidX (u.a. Room, DataStore, Navigation), Material Components, Kotlin-Standardbibliothek und Kotlin Coroutines (Apache License 2.0); DataStore enthält Protocol Buffers von Google (BSD-3-Clause).

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
