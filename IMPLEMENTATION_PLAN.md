# FOSStenbuch – Implementationsplan

## Projektstand (Ist-Zustand)

Das Grundgerüst steht:
- Clean Architecture Grundstruktur (data/di/ui/utils)
- Room-Datenbank mit `Trip` und `Vehicle` Entities, DAOs, TypeConverters
- Repository-Pattern (Interface + Impl) für Trips und Vehicles
- Hilt Dependency Injection
- Navigation Component mit Bottom Navigation (3 Tabs)
- CI/CD Workflows (GitHub Actions)
- Timber Logging

**Was fehlt:**
- Keine ViewModels
- Keine Use Cases / Domain Layer (nur erwähnt, nicht implementiert)
- Alle Fragments sind leere Platzhalter
- Keine CRUD-UI für Fahrten oder Fahrzeuge
- Keine Statistik-Logik
- Kein Export, kein Backup, keine Validierung

---

## Architektur-Prinzipien

```
┌─────────────────────────────────────────────┐
│  UI Layer (Fragments, Adapters, ViewModels) │
├─────────────────────────────────────────────┤
│  Domain Layer (Use Cases, Interfaces)       │
├─────────────────────────────────────────────┤
│  Data Layer (Repositories, DAOs, Database)  │
└─────────────────────────────────────────────┘
```

- **Jede Schicht kennt nur die darunter liegende** (UI → Domain → Data)
- **Interfaces zwischen Schichten** für Testbarkeit und Austauschbarkeit
- **Use Cases** kapseln einzelne Business-Operationen
- **ViewModels** halten UI-State, keine Business-Logik
- **Repositories** abstrahieren Datenquellen

---

## Phase 1: Domain Layer & Use Cases ✅

**Status: Implementiert**

Alle Use Cases, Validatoren und die gemeinsame `ValidationResult`-Klasse sind implementiert.

### 1.1 Trip Use Cases ✅
```
domain/usecase/trip/
├── GetAllTripsUseCase.kt         ✅
├── GetTripByIdUseCase.kt         ✅
├── InsertTripUseCase.kt          ✅ (mit Validierung, sealed Result)
├── UpdateTripUseCase.kt          ✅ (mit Validierung, sealed Result)
├── DeleteTripUseCase.kt          ✅
├── GetTripsByDateRangeUseCase.kt ✅
├── GetBusinessTripsUseCase.kt    ✅
└── GetPrivateTripsUseCase.kt     ✅
```

### 1.2 Vehicle Use Cases ✅
```
domain/usecase/vehicle/
├── GetAllVehiclesUseCase.kt      ✅
├── GetVehicleByIdUseCase.kt      ✅
├── InsertVehicleUseCase.kt       ✅ (mit Validierung, Primary-Handling)
├── UpdateVehicleUseCase.kt       ✅ (mit Validierung, Primary-Handling)
├── DeleteVehicleUseCase.kt       ✅
├── GetPrimaryVehicleUseCase.kt   ✅
└── SetPrimaryVehicleUseCase.kt   ✅
```

### 1.3 Statistics Use Cases ✅
```
domain/usecase/stats/
├── GetTotalDistanceUseCase.kt          ✅
├── GetDistanceByTypeUseCase.kt         ✅ (combined Flow)
├── GetTripCountByPeriodUseCase.kt      ✅
└── GetMonthlyDistanceSummaryUseCase.kt ✅
```

Neue DAO-Methoden hinzugefügt: `getTotalDistance()`, `getTripCountByDateRange()`, `getMonthlyDistanceSummary()` + `MonthlyDistance` data class.

### 1.4 Validierung ✅
```
domain/validation/
├── ValidationResult.kt    ✅ (generisch, Map<field, error>)
├── TripValidator.kt       ✅ (Pflichtfelder, Distanz, Datum, Kilometerstand-Konsistenz)
└── VehicleValidator.kt    ✅ (Pflichtfelder, deutsches Kennzeichen-Format)
```

### 1.5 Tests ✅
```
test/domain/validation/
├── TripValidatorTest.kt       ✅ (12 Tests)
└── VehicleValidatorTest.kt    ✅ (9 Tests)
test/domain/usecase/trip/
└── InsertTripUseCaseTest.kt   ✅ (3 Tests: Success, Validation, Error)
```

**Hinweis:** Kein separates `UseCaseModule` nötig – alle Use Cases nutzen `@Inject constructor` und werden von Hilt automatisch über die bestehenden Repository-Bindings aufgelöst.

---

## Phase 2: ViewModels & UI-State ✅

**Status: Implementiert**

Alle ViewModels sind mit `@HiltViewModel` annotiert, nutzen `StateFlow` für reaktiven UI-State und delegieren an Use Cases. Fragments sind verkabelt und beobachten den State via `repeatOnLifecycle`.

### 2.1 Trips ViewModel ✅
```
ui/trips/
├── TripsViewModel.kt         ✅ (Filter: All/Business/Private, Sort: Date/Distance)
├── TripsUiState.kt           ✅ (data class mit TripFilter, TripSort enums)
├── TripDetailViewModel.kt    ✅ (Add/Edit via SavedStateHandle, Insert/Update mit Result-Handling)
└── TripDetailUiState.kt      ✅ (ValidationResult-Integration, save/edit state)
```

### 2.2 Stats ViewModel ✅
```
ui/stats/
├── StatsViewModel.kt         ✅ (combine 5 Flows, Jahresauswahl, Monats-/Jahresbereich)
└── StatsUiState.kt           ✅ (totalDistance, distanceByType, tripCounts, monthlyDistances)
```

### 2.3 Settings ViewModel ✅
```
ui/settings/
├── SettingsViewModel.kt      ✅ (Datenzusammenfassung, erweiterbar in Phase 7)
└── SettingsUiState.kt        ✅
```

### 2.4 Vehicle ViewModel ✅
```
ui/vehicles/
├── VehiclesViewModel.kt       ✅ (Liste, Löschen, Primary setzen)
├── VehiclesUiState.kt         ✅
├── VehicleDetailViewModel.kt  ✅ (Add/Edit via SavedStateHandle)
└── VehicleDetailUiState.kt    ✅
```

### 2.5 Common ✅
```
ui/common/
└── UiState.kt                ✅ (Generic sealed interface: Loading, Success, Empty, Error)
```

### 2.6 Fragment-Verdrahtung ✅
- `TripsFragment` → `TripsViewModel` (via `by viewModels()`, `repeatOnLifecycle`)
- `StatsFragment` → `StatsViewModel`
- `SettingsFragment` → `SettingsViewModel`

### 2.7 Ergänzungen
- `lifecycle-runtime-ktx:2.7.0` als Dependency hinzugefügt (für `repeatOnLifecycle`)
```

---

## Phase 3: Trip-CRUD UI ✅

**Status: Implementiert**

**Ziel:** Vollständige Fahrtenverwaltung

### 3.1 Trips-Liste
- RecyclerView mit TripAdapter
- Swipe-to-Delete
- Pull-to-Refresh (optional)
- FAB zum Hinzufügen
- Filter-Chips (Alle / Geschäftlich / Privat)
- Sortierung (Datum, Distanz)

### 3.2 Trip-Formular (Add/Edit)
- Neues Fragment `AddEditTripFragment`
- Datum-Picker
- Start-/Zielort Eingabe
- Distanz (manuell oder über Kilometerstand)
- Zweck / Beschreibung
- Geschäftlich/Privat Toggle
- Fahrzeug-Auswahl (Dropdown)
- Validierung mit Fehlermeldungen
- Navigation: nav_graph erweitern

### 3.3 Trip-Detail
- Anzeige aller Informationen
- Bearbeiten/Löschen Aktionen

### Neue Layouts
```
res/layout/
├── fragment_add_edit_trip.xml
├── fragment_trip_detail.xml
├── item_trip.xml              (RecyclerView Item)
└── dialog_confirm_delete.xml
```

---

## Phase 4: Fahrzeugverwaltung ✅

**Status: Implementiert**

**Ziel:** Fahrzeuge anlegen, bearbeiten, löschen

### 4.1 Fahrzeugliste
- RecyclerView in Settings oder eigener Tab
- Primärfahrzeug markieren

### 4.2 Fahrzeug-Formular
- Hersteller, Modell, Kennzeichen, Kraftstoffart
- Primärfahrzeug setzen
- **Änderungssichere Protokollierung aktivieren** (Toggle, Hinweis auf steuerliche Bedeutung)

### 4.3 Änderungssichere Protokollierung (Finanzamt-Modus)

Wenn ein Fahrzeug mit aktivierter Protokollierung angelegt wird, gelten für alle zugehörigen Fahrten verschärfte Regeln:

- **Fahrten können nach dem Speichern nicht mehr gelöscht werden** (nur Stornierung durch Gegeneintrag)
- **Bearbeitungen werden versioniert** – jede Änderung erzeugt einen `TripAuditLog`-Eintrag mit Zeitstempel, Felddiff und Nutzerangabe
- **Fahrzeug selbst ist nach Aktivierung nicht mehr löschbar**, solange Fahrten existieren
- Die Protokollierung ist **irreversibel** – der Modus kann nachträglich nicht deaktiviert werden
- Ein visueller Indikator (Schloss-Icon) kennzeichnet das Fahrzeug in der Liste

```
data/model/
└── TripAuditLog.kt           (Entity: tripId, fieldName, oldValue, newValue, changedAt)
data/local/
└── TripAuditLogDao.kt
domain/usecase/audit/
├── GetAuditLogForTripUseCase.kt
└── IsVehicleAuditProtectedUseCase.kt
```

**Datenbankschema-Erweiterung `Vehicle`:**
```kotlin
val auditProtected: Boolean = false   // einmalig setzbar, nie rücksetzbar
```

**Datenbankschema `Trip`:**
```kotlin
val isCancelled: Boolean = false      // Storno-Flag statt Löschung
val cancellationReason: String? = null
```

### Neue Dateien
```
ui/vehicles/
├── VehiclesFragment.kt
├── AddEditVehicleFragment.kt
├── VehicleAdapter.kt
res/layout/
├── fragment_vehicles.xml
├── fragment_add_edit_vehicle.xml
├── item_vehicle.xml
├── dialog_audit_protection_warning.xml  (Hinweis vor Aktivierung)
```

---

## Phase 4b: Gespeicherte Orte & GPS-Vorschlag ✅

**Status: Implementiert**

**Ziel:** Häufig genutzte Orte speichern; beim Starten/Beenden einer Fahrt wird automatisch der nächste gespeicherte Ort (≤ 1 km) vorgeschlagen.

### Datenmodell
```
data/model/
└── SavedLocation.kt
    // id, name, latitude, longitude, address?, usageCount
```

### DAO & Repository
```
data/local/
└── SavedLocationDao.kt
data/repository/
├── SavedLocationRepository.kt
└── SavedLocationRepositoryImpl.kt
```

### Use Cases
```
domain/usecase/location/
├── GetAllSavedLocationsUseCase.kt
├── InsertSavedLocationUseCase.kt
├── UpdateSavedLocationUseCase.kt
├── DeleteSavedLocationUseCase.kt
└── FindNearestSavedLocationUseCase.kt
    // Parameter: latitude, longitude, radiusMeters = 1000
    // Gibt SavedLocation? zurück (Haversine-Distanzberechnung, kein API-Call)
```

### GPS-Vorschlagslogik (kein externer Dienst)
- `FindNearestSavedLocationUseCase` berechnet Distanz per **Haversine-Formel** rein lokal
- Wird aufgerufen, sobald der Nutzer ein Start- oder Zielort-Feld fokussiert
- Ist ein Ort ≤ 1 km entfernt: Vorschlag als auswählbaren Chip anzeigen
- Kein Vorschlag → normales Freitextfeld
- Android-Berechtigung: `ACCESS_FINE_LOCATION` (nur zur Laufzeit, kein Background-Zugriff)
- `usageCount` wird bei jeder Auswahl erhöht → Top-Orte priorisiert

### Ortsverwaltung UI
```
ui/locations/
├── SavedLocationsFragment.kt      (Liste aller Orte)
├── SavedLocationsViewModel.kt
├── AddEditLocationFragment.kt     (Name, optional: Adresse)
├── LocationAdapter.kt
res/layout/
├── fragment_saved_locations.xml
├── fragment_add_edit_location.xml
├── item_saved_location.xml
└── view_location_suggestion_chip.xml
```

**AndroidManifest.xml Ergänzung:**
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

---

## Phase 4c: Benutzerdefinierte Fahrt-Zwecke ✅

**Status: Implementiert**

**Ziel:** Statt fest codierter Kategorien (Privat / Beruflich) kann der Nutzer beliebige Zwecke anlegen und verwalten.

### Datenmodell
```
data/model/
└── TripPurpose.kt
    // id, name, isBusinessRelevant: Boolean, color: String (hex), isDefault: Boolean
```

Die bisherige `businessTrip: Boolean` in `Trip` wird durch eine **Fremdschlüssel-Referenz** ersetzt:
```kotlin
// Alt:
val businessTrip: Boolean
// Neu:
val purposeId: Long
```

**Datenbankmigrierung:** Room-Migration `version 1 → 2` mit `ALTER TABLE` + Befüllung von Standard-Zwecken.

### Standard-Zwecke (werden beim ersten App-Start angelegt)
| Name | Geschäftlich |
|------|-------------|
| Beruflich | ✅ |
| Privat | ❌ |

### DAO & Repository
```
data/local/
└── TripPurposeDao.kt
data/repository/
├── TripPurposeRepository.kt
└── TripPurposeRepositoryImpl.kt
```

### Use Cases
```
domain/usecase/purpose/
├── GetAllPurposesUseCase.kt
├── InsertPurposeUseCase.kt     (Name darf nicht leer/doppelt sein)
├── UpdatePurposeUseCase.kt
└── DeletePurposeUseCase.kt     (nur wenn kein Trip diesen Zweck nutzt)
```

### UI
```
ui/purposes/
├── PurposesFragment.kt         (Verwaltung in Einstellungen)
├── PurposesViewModel.kt
├── AddEditPurposeFragment.kt
└── PurposeAdapter.kt
res/layout/
├── fragment_purposes.xml
├── fragment_add_edit_purpose.xml
└── item_purpose.xml
```

---

## Phase 5: Statistiken ✅

**Status: Implementiert**

**Ziel:** Übersichtliche Auswertungen

### 5.1 Dashboard-Übersicht
- Gesamtkilometer (Geschäftlich / Privat)
- Anzahl Fahrten im aktuellen Monat/Jahr
- Durchschnittliche Fahrtdistanz

### 5.2 Zeitraum-Statistiken
- Monat/Quartal/Jahr Auswertung
- Balkendiagramm (optional: MPAndroidChart Bibliothek)

### 5.3 Export-Vorbereitung
- Daten aggregiert bereitstellen (für spätere CSV/PDF-Ausgabe)

### Neue Dateien
```
ui/stats/
├── StatsAdapter.kt
├── StatItem.kt               (UI Model für eine Statistik-Karte)
res/layout/
├── item_stat_card.xml
```

---

## Phase 6: Datenexport

**Ziel:** Fahrtenbuch in exportierbarem Format

### 6.1 CSV-Export
```
domain/export/
├── ExportFormat.kt           (enum: CSV, PDF)
├── TripExporter.kt           (Interface)
├── CsvTripExporter.kt
```

### 6.2 PDF-Export mit Zweck-Filter
```
domain/export/
└── PdfTripExporter.kt
```

**Export-Konfiguration:**
```kotlin
data class ExportConfig(
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
    val selectedPurposeIds: Set<Long>,  // Welche Zwecke sollen im PDF erscheinen
    val vehicleId: Long?,               // null = alle Fahrzeuge
    val includeAuditLog: Boolean = false
)
```

Der Nutzer wählt vor dem Export in einem Dialog:
- Zeitraum (Von / Bis)
- **Checkboxen für jeden vorhandenen Zweck** (z. B. nur „Beruflich" für das Finanzamt)
- Fahrzeugfilter

Bei **änderungssicheren Fahrzeugen** enthält das PDF zusätzlich:
- Integritätshinweis (z. B. „Unverändertes Fahrtenbuch gemäß §22 UStG")
- Audit-Log-Anhang (alle nachträglichen Korrekturen mit Zeitstempel)

**Export-UI:**
```
ui/export/
├── ExportFragment.kt
├── ExportViewModel.kt
├── PurposeFilterAdapter.kt     (Checkboxen für Zwecke)
res/layout/
├── fragment_export.xml
└── item_export_purpose_filter.xml
```

### 6.3 Teilen-Funktion
- Android ShareSheet Integration
- Datei im Downloads-Ordner speichern

---

## Phase 7: Einstellungen

**Ziel:** App-Konfiguration

### 7.1 Allgemeine Einstellungen
- Distanzeinheit (km / Meilen)
- Standard-Fahrttyp (Geschäftlich / Privat)
- Standard-Fahrzeug

### 7.2 Datenmanagement
- Alle Daten löschen (mit Bestätigung)
- Datenbank-Backup (JSON-Export)
- Datenbank-Restore (JSON-Import)

### 7.3 Darstellung
- Dark Mode (System / Hell / Dunkel)
- Sprache (Deutsch / Englisch)

### Neue Dateien
```
data/local/
├── PreferencesManager.kt     (DataStore)
di/
├── PreferencesModule.kt
```

---

## Phase 8: Polish & Qualität

### 8.1 Error Handling
```
utils/
├── Result.kt                 (sealed class: Success, Error, Loading)
├── ErrorMapper.kt            (DB-Fehler → User-Meldung)
```

### 8.2 Testing
- Unit Tests für alle Use Cases
- Unit Tests für alle ViewModels
- Repository Tests erweitern
- UI Tests (Espresso) für kritische Flows

### 8.3 Accessibility
- Content Descriptions für alle interaktiven Elemente
- Mindestgröße für Touch-Targets

### 8.4 Performance
- Room Queries optimieren (Indices)
- Pagination für große Fahrtenlisten (Paging 3)

---

## Phase 9: Erweiterte Features (Roadmap)

| Feature | Priorität | Abhängigkeit |
|---------|-----------|-------------|
| Home-Screen Widget (Quick-Entry) | Mittel | Phase 3 |
| Kilometerpauschale-Rechner | Mittel | Phase 5 |
| Wiederkehrende Fahrten (Templates) | Niedrig | Phase 3 |
| Kategorien/Tags für Fahrten | Niedrig | Phase 3 |
| Mehrere Fahrtenbücher | Niedrig | Phase 4 |
| Auto-Vervollständigung Orte | Niedrig | Phase 3 |
| Benachrichtigungen (Fahrt eintragen) | Niedrig | Phase 3 |

---

## Empfohlene Reihenfolge

```
Phase 1  (Domain Layer)          → Fundament für alles
  ↓
Phase 2  (ViewModels)            → Verbindung UI ↔ Domain
  ↓
Phase 3  (Trip CRUD)             → Kernfunktionalität
  ↓
Phase 4  (Fahrzeuge + Audit)     → Fahrzeugverwaltung & Finanzamt-Modus
  ↓
Phase 4b (Gespeicherte Orte)     → GPS-Vorschlag beim Erfassen
  ↓
Phase 4c (Zwecke)                → Flexible Kategorisierung
  ↓
Phase 5  (Statistiken)           → Auswertungen
  ↓
Phase 6  (Export + Zweck-Filter) → Datenausgabe für Steuer/Abrechnung
  ↓
Phase 7  (Einstellungen)         → Konfiguration
  ↓
Phase 8  (Qualität)              → Stabilität & Tests
  ↓
Phase 9  (Extras)                → Nice-to-have Features
```

## Neue Abhängigkeiten (bei Bedarf)

```kotlin
// DataStore für Einstellungen (ersetzt SharedPreferences)
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Paging 3 für große Listen
implementation("androidx.paging:paging-runtime-ktx:3.2.1")

// Charts (optional)
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

// PDF-Generierung
implementation("com.tom-roush:pdfbox-android:2.0.27.0")

// Location Services (GPS-Vorschlag)
implementation("com.google.android.gms:play-services-location:21.2.0")
```

---

## Dateistruktur-Ziel

```
java/de/fosstenbuch/
├── MainApplication.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── DateConverters.kt
│   │   ├── PreferencesManager.kt
│   │   ├── SavedLocationDao.kt
│   │   ├── TripAuditLogDao.kt
│   │   ├── TripDao.kt
│   │   ├── TripPurposeDao.kt
│   │   └── VehicleDao.kt
│   ├── model/
│   │   ├── SavedLocation.kt
│   │   ├── Trip.kt             (+ purposeId, isCancelled, cancellationReason)
│   │   ├── TripAuditLog.kt
│   │   ├── TripPurpose.kt
│   │   └── Vehicle.kt          (+ auditProtected)
│   └── repository/
│       ├── SavedLocationRepository.kt
│       ├── SavedLocationRepositoryImpl.kt
│       ├── TripPurposeRepository.kt
│       ├── TripPurposeRepositoryImpl.kt
│       ├── TripRepository.kt
│       ├── TripRepositoryImpl.kt
│       ├── VehicleRepository.kt
│       └── VehicleRepositoryImpl.kt
├── di/
│   ├── AppModule.kt
│   ├── PreferencesModule.kt
│   └── UseCaseModule.kt
├── domain/
│   ├── export/
│   │   ├── CsvTripExporter.kt
│   │   ├── ExportConfig.kt
│   │   ├── ExportFormat.kt
│   │   ├── PdfTripExporter.kt
│   │   └── TripExporter.kt
│   ├── usecase/
│   │   ├── audit/
│   │   │   ├── GetAuditLogForTripUseCase.kt
│   │   │   └── IsVehicleAuditProtectedUseCase.kt
│   │   ├── location/
│   │   │   ├── DeleteSavedLocationUseCase.kt
│   │   │   ├── FindNearestSavedLocationUseCase.kt
│   │   │   ├── GetAllSavedLocationsUseCase.kt
│   │   │   ├── InsertSavedLocationUseCase.kt
│   │   │   └── UpdateSavedLocationUseCase.kt
│   │   ├── purpose/
│   │   │   ├── DeletePurposeUseCase.kt
│   │   │   ├── GetAllPurposesUseCase.kt
│   │   │   ├── InsertPurposeUseCase.kt
│   │   │   └── UpdatePurposeUseCase.kt
│   │   ├── stats/
│   │   ├── trip/
│   │   └── vehicle/
│   └── validation/
│       ├── TripValidator.kt
│       └── VehicleValidator.kt
├── ui/
│   ├── common/
│   │   └── UiState.kt
│   ├── export/
│   │   ├── ExportFragment.kt
│   │   ├── ExportViewModel.kt
│   │   └── PurposeFilterAdapter.kt
│   ├── locations/
│   │   ├── AddEditLocationFragment.kt
│   │   ├── LocationAdapter.kt
│   │   ├── SavedLocationsFragment.kt
│   │   └── SavedLocationsViewModel.kt
│   ├── main/
│   │   └── MainActivity.kt
│   ├── purposes/
│   │   ├── AddEditPurposeFragment.kt
│   │   ├── PurposeAdapter.kt
│   │   ├── PurposesFragment.kt
│   │   └── PurposesViewModel.kt
│   ├── settings/
│   │   ├── SettingsFragment.kt
│   │   └── SettingsViewModel.kt
│   ├── stats/
│   │   ├── StatsFragment.kt
│   │   ├── StatsUiState.kt
│   │   └── StatsViewModel.kt
│   ├── trips/
│   │   ├── AddEditTripFragment.kt
│   │   ├── TripAdapter.kt
│   │   ├── TripDetailFragment.kt
│   │   ├── TripDetailViewModel.kt
│   │   ├── TripsFragment.kt
│   │   ├── TripsUiState.kt
│   │   └── TripsViewModel.kt
│   └── vehicles/
│       ├── AddEditVehicleFragment.kt
│       ├── VehicleAdapter.kt
│       ├── VehicleDetailViewModel.kt
│       ├── VehiclesFragment.kt
│       ├── VehiclesUiState.kt
│       └── VehiclesViewModel.kt
└── utils/
    ├── ErrorMapper.kt
    ├── HaversineUtils.kt
    ├── Result.kt
    └── TimberDebugTree.kt
```
