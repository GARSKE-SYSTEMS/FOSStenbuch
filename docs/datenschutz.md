# Datenschutzerklärung für die App „FOSStenbuch“

Stand: 16. Oktober 2026 · [English version below](#privacy-policy-for-the-fosstenbuch-app)

## Das Wichtigste in Kürze

- FOSStenbuch speichert alle Ihre Fahrten, Fahrzeuge, Standorte und Einstellungen auf Ihrem Gerät. Wir erhalten
  diese Daten nicht. Ausnahme: Ist die Android-Sicherung in Ihr Google-Konto eingeschaltet, sichert Android die
  App-Daten dort mit (Abschnitt 6).
- Kein Benutzerkonto, keine Werbung, keine Analyse- oder Tracking-Dienste, keine Absturzberichte aus der App.
- Die App hat genau eine Online-Verbindung: die Adresssuche und Ortsnamen über **OpenStreetMap Nominatim**
  (Abschnitt 4).

## 1. Verantwortlicher

Garske Systems, Inh. Patrick M. Garske<br>
An der Bausenheide 55, 40474 Düsseldorf, Deutschland<br>
E-Mail: info@garske-systems.de · [Impressum](https://garske-systems.de/impressum)

Diese Erklärung gilt für die Android-App „FOSStenbuch“ (Paketname `de.garsys.fosstenbuch`), egal ob Sie sie über
Google Play, F-Droid oder GitHub beziehen.

## 2. Daten auf Ihrem Gerät

Die App speichert, was Sie eingeben oder aufzeichnen, in ihrem privaten Speicher auf dem Gerät. Andere Apps haben
darauf keinen Zugriff, wir auch nicht:

- **Fahrten:** Datum und Uhrzeit, Start- und Zielort, Kilometerstände, Strecke, Kategorie (z. B. beruflich/privat),
  Geschäftspartner, Route, Notizen, Stornogrund. Bei GPS-Aufzeichnung zusätzlich die per GPS gemessene Strecke; bei
  automatisch erkannten Fahrten die Koordinaten von Start- und Zielpunkt.
- **Änderungsprotokoll:** Welche Angaben einer Fahrt beim Beenden, beim Übernehmen einer automatisch erkannten Fahrt
  oder später wann geändert wurden (alter und neuer Wert).
- **Fahrzeuge:** Hersteller, Modell, Kennzeichen, Kraftstoff, Notizen sowie Name und Hardware-Adresse (MAC) des
  Bluetooth-Geräts, das Sie für die automatische Fahrterkennung auswählen.
- **Gespeicherte Standorte und Vorlagen:** Name, Koordinaten, Adresse, Geschäftspartner, Fahrtdaten der Vorlage.
- **Einstellungen:** z. B. Fahrername (erscheint in Exporten), Erinnerungszeit, Design.

Eine Wegstrecke (Folge von Positionen) speichert die App nicht. Während einer Aufzeichnung merkt sie sich nur die
jeweils letzte Position, um die Strecke weiterzuzählen.

Rechtsgrundlage für diese Verarbeitung auf Ihrem Gerät ist Art. 6 Abs. 1 lit. b DSGVO (Bereitstellung der App gemäß
den [Nutzungsbedingungen](nutzungsbedingungen.md)). Erfassen Sie Daten anderer Personen (z. B. Geschäftspartner),
sind Sie dafür selbst verantwortlich.

## 3. Berechtigungen

Alle Berechtigungen sind freiwillig. Die App fragt sie erst, wenn Sie die zugehörige Funktion nutzen. Ohne sie
können Sie Fahrten weiterhin starten und beenden, nur ohne GPS-Strecke.

| Berechtigung | Wofür |
|---|---|
| Standort (genau/ungefähr) | Strecke einer Fahrt per GPS messen; Vorschlag eines gespeicherten Standorts in Ihrer Nähe (Abgleich nur auf dem Gerät); aktuelle Position als Standort speichern |
| Standort im Hintergrund („Immer zulassen“) | Für die automatische Fahrterkennung: Aufzeichnung, während Ihr Handy mit dem Bluetooth des Fahrzeugs verbunden ist, auch wenn die App geschlossen ist. Außerdem kann eine laufende Aufzeichnung nach einem App-Update im Hintergrund weiterlaufen. |
| Geräte in der Nähe (Bluetooth) | Gekoppeltes Bluetooth-Gerät des Fahrzeugs auswählen und erkennen, wann es sich verbindet oder trennt. Die App sucht nicht nach fremden Geräten. |
| Benachrichtigungen | Hinweis während einer Aufzeichnung; tägliche Erinnerung, falls eingeschaltet |
| Vordergrunddienst (Standort) | GPS-Aufzeichnung mit Benachrichtigung |
| Start nach dem Hochfahren | Erinnerung neu planen; eine durch Ausschalten unterbrochene automatische Aufzeichnung abschließen |
| Internet | Adresssuche und Ortsnamen (Abschnitt 4) |

Während einer Aufzeichnung zeigt die App eine Benachrichtigung an. Ab Android 13 erscheint sie nur, wenn Sie
Benachrichtigungen erlauben; sonst führt Android die laufende Aufzeichnung nur in der Übersicht der aktiven Apps. Die
Position selbst ermittelt Android (GPS oder, falls GPS aus ist, der Standortdienst des Geräts). Je nach
Geräteeinstellung kann dabei der Standortdienst von Google oder des Herstellers Daten verarbeiten; darauf hat die App
keinen Einfluss.

## 4. OpenStreetMap Nominatim (Adresssuche und Ortsnamen)

Für Adressen nutzt die App den Dienst Nominatim unter `nominatim.openstreetmap.org`. Betreiber ist die
**OpenStreetMap Foundation (OSMF)**, St John’s Innovation Centre, Cowley Road, Cambridge, CB4 0WS, Vereinigtes
Königreich. Die App baut die Verbindung direkt und verschlüsselt (HTTPS) von Ihrem Gerät auf. Wir erhalten dabei
keine Daten.

In zwei Fällen wird eine Anfrage gesendet:

1. **Adresssuche:** Wenn Sie beim Anlegen oder Bearbeiten eines gespeicherten Standorts unter „Adresse suchen
   (OpenStreetMap)“ auf „Suchen“ tippen, wird Ihr Suchbegriff übertragen.
2. **Ortsnamen automatisch erkannter Fahrten:** Nach dem Ende einer automatisch erkannten Fahrt werden die
   Koordinaten von Start- und Zielpunkt (auf etwa 10 m gerundet) übertragen, um daraus eine Adresse zu machen. Das
   entfällt für Punkte, in deren Nähe (500 m) ein gespeicherter Standort liegt. Die App fragt dafür nicht bei jeder
   Fahrt nach, sondern weist Sie darauf hin, bevor Sie ein Bluetooth-Gerät für die automatische Erkennung auswählen
   und bevor Sie den Standort „Immer zulassen“ erlauben.

Technisch bedingt erhält die OSMF dabei außerdem Ihre IP-Adresse und die Kennung der App
(„FOSStenbuch-Android/1.0“), aber keine Geräte- oder Nutzerkennung. Die OSMF verarbeitet diese Daten in eigener
Verantwortung, nach eigenen Angaben auf Servern im Vereinigten Königreich und in den Niederlanden. Für das Vereinigte
Königreich besteht ein Angemessenheitsbeschluss der EU-Kommission (Art. 45 DSGVO). Einzelheiten:
[Datenschutzerklärung der OSMF](https://osmfoundation.org/wiki/Privacy_Policy), Kontakt privacy@osmfoundation.org.

Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO, weil die Anfrage die von Ihnen genutzte Funktion erst ermöglicht. Die
Angaben sind weder gesetzlich noch vertraglich vorgeschrieben. Ohne die Übertragung funktionieren nur die Adresssuche
und die automatische Ortsermittlung nicht; die App speichert dann die Koordinaten als Ortsangabe. Vermeiden können
Sie die Übertragung, indem Sie die Adresssuche nicht nutzen und häufige Orte als „Gespeicherte Standorte“ anlegen
oder die automatische Fahrterkennung nicht einrichten.

## 5. Export, Backup und Teilen

PDF- und CSV-Exporte sowie Backups (JSON) erstellt die App auf Ihrem Gerät. Exporte enthalten die gewählten Fahrten
mit Fahrzeug, Fahrername und gegebenenfalls dem Änderungsprotokoll. Ein Backup enthält alle Fahrten (auch
Koordinaten), Fahrzeuge samt Bluetooth-Gerät, Kategorien, Standorte, Vorlagen und das Änderungsprotokoll, aber keine
Einstellungen. Die Dateien sind nicht verschlüsselt. Über den Android-Teilen-Dialog entscheiden Sie selbst, wohin
eine Datei geht (z. B. E-Mail, Cloud-Speicher). Für den Empfänger gilt dessen Datenschutzerklärung. Beim
Wiederherstellen liest die App nur die Datei, die Sie auswählen.

## 6. Android-Systemsicherung

Die App lässt die Android-Systemsicherung zu, damit Ihre Fahrten einen Gerätewechsel überstehen. Haben Sie auf Ihrem
Gerät die Sicherung in Ihr Google-Konto oder die Übertragung auf ein neues Gerät eingeschaltet, sichert Android die
Daten der App dabei mit und stellt sie bei einer Neuinstallation wieder her. Ab Android 9 ist diese Sicherung
Ende-zu-Ende-verschlüsselt, wenn eine Displaysperre eingerichtet ist. Die Sicherung steuern Google bzw. der
Gerätehersteller und Sie in den Android-Einstellungen; dort können Sie sie auch ausschalten. Wir haben darauf keinen
Zugriff.

## 7. Bezug über App-Stores, Kontakt und Links

- **Google Play:** Beim Herunterladen verarbeitet Google Daten nach seiner
  [Datenschutzerklärung](https://policies.google.com/privacy). Über die Google Play Console erhalten wir von Google
  zusammengefasste Statistiken (z. B. Zahl der Installationen) und, sofern Sie Android das Teilen von Diagnosedaten
  erlaubt haben, Absturz- und Fehlerberichte ohne direkte Kennung (Gerätemodell, Android-Version, technischer
  Fehlerverlauf). Wir nutzen sie nur, um Fehler zu beheben (Art. 6 Abs. 1 lit. f DSGVO; unser berechtigtes Interesse
  ist eine funktionierende App). Die Berichte bleiben in der Play Console, bis Google sie nach eigenen Fristen löscht;
  wir speichern sie nicht außerhalb der Play Console.
- **F-Droid und GitHub:** Es gelten die Datenschutzerklärungen dieser Plattformen.
- **Kontakt:** Schreiben Sie uns (z. B. per E-Mail oder als Bewertung in Google Play), verarbeiten wir Ihre Angaben
  (Name, E-Mail-Adresse, Inhalt), um zu antworten. Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO, soweit es um die
  Nutzung der App geht, sonst Art. 6 Abs. 1 lit. f DSGVO (unser berechtigtes Interesse, Anfragen und Bewertungen zu
  beantworten). E-Mails verarbeitet unser E-Mail-Anbieter als Auftragsverarbeiter. Wir löschen Ihre Nachricht, wenn
  die Anfrage erledigt ist, sofern keine gesetzliche Aufbewahrungspflicht besteht.
- **Links:** Links in der App (z. B. zu GitHub, unserer Website oder dieser Erklärung) öffnen Ihren Browser. Dort
  gilt die Datenschutzerklärung der jeweiligen Website.

## 8. Speicherdauer und Löschung

Die Daten bleiben auf Ihrem Gerät, bis Sie sie in der App löschen (einzeln oder mit „Einstellungen › Alle Daten
löschen“), die App-Daten in den Android-Einstellungen löschen oder die App deinstallieren. Abgeschlossene Fahrten
eines als „änderungssicher“ markierten Fahrzeugs lassen sich bewusst nicht einzeln löschen, sondern nur stornieren;
vollständig entfernen Sie sie mit „Alle Daten löschen“.

Export- und Backup-Dateien liegen im Zwischenspeicher der App: ein Backup bis zum nächsten Backup, ein Export, bis
Sie frühestens einen Tag später einen weiteren Export erstellen. „Alle Daten löschen“ entfernt sie sofort; Android
kann den Zwischenspeicher auch früher leeren. Dateien, die Sie geteilt oder gespeichert haben, und Systemsicherungen
löscht die App nicht.

## 9. Kein Verkauf, keine Werbung, kein Profiling

Wir verkaufen keine Daten. Außer den Anfragen an die OSMF (Abschnitt 4) übermittelt die App keine Daten an Dritte.
Die App enthält keine Werbung, keine Analyse- oder Tracking-Dienste und keine weiteren Bibliotheken, die Daten an
Dritte senden. Es gibt keine automatisierte Entscheidungsfindung und kein Profiling.

## 10. Ihre Rechte

Sie haben das Recht auf Auskunft (Art. 15 DSGVO), Berichtigung (Art. 16), Löschung (Art. 17), Einschränkung der
Verarbeitung (Art. 18) und Datenübertragbarkeit (Art. 20). Da wir Ihre App-Daten nicht erhalten, können Sie die
meisten Rechte direkt in der App ausüben: ansehen, ändern, löschen, exportieren. Für Daten bei der OSMF wenden Sie
sich an privacy@osmfoundation.org. Für Fragen an uns: info@garske-systems.de.

**Widerspruchsrecht (Art. 21 DSGVO):** Soweit wir Daten auf Grundlage von Art. 6 Abs. 1 lit. f DSGVO verarbeiten
(Abschnitt 7), können Sie dem aus Gründen, die sich aus Ihrer besonderen Situation ergeben, jederzeit widersprechen,
formlos per E-Mail an info@garske-systems.de. Künftige Absturzberichte verhindern Sie am einfachsten, indem Sie in den
Android-Einstellungen das Teilen von Nutzungs- und Diagnosedaten ausschalten.

Sie können sich außerdem bei einer Datenschutz-Aufsichtsbehörde beschweren (Art. 77 DSGVO), zum Beispiel bei der
für uns zuständigen Landesbeauftragten für Datenschutz und Informationsfreiheit Nordrhein-Westfalen,
Kavalleriestraße 2–4, 40213 Düsseldorf.

## 11. Kinder

Die App richtet sich an Erwachsene (ab 18 Jahren), die ein Fahrtenbuch führen.

## 12. Sicherheit

Die Daten liegen im privaten Speicher der App, die Verbindung zu Nominatim ist verschlüsselt. Schützen Sie Ihr Gerät
mit einer Displaysperre, und bewahren Sie Exporte und Backups sicher auf.

## 13. Änderungen

Wir passen diese Erklärung an, wenn sich die App oder die Rechtslage ändert. Die aktuelle Fassung steht immer unter
<https://garske-systems.github.io/FOSStenbuch/datenschutz.html>. Frühere Fassungen finden Sie in der
[Versionsgeschichte](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/datenschutz.md).

---

# Privacy Policy for the FOSStenbuch App

Last updated: 16 October 2026. This is a translation; if the two versions differ, the German version above applies.

## Summary

- FOSStenbuch stores all your trips, vehicles, saved locations and settings on your device. We do not receive this
  data. Exception: if Android backup to your Google account is switched on, Android includes the app data in it
  (section 6).
- No user account, no ads, no analytics or tracking services, no crash reporting from the app.
- The app has exactly one online connection: address search and place names via **OpenStreetMap Nominatim**
  (section 4).

## 1. Controller

Garske Systems, owner Patrick M. Garske, An der Bausenheide 55, 40474 Düsseldorf, Germany, e-mail
info@garske-systems.de ([legal notice](https://garske-systems.de/impressum)). This policy covers the Android app
"FOSStenbuch" (package name `de.garsys.fosstenbuch`), whether you get it from Google Play, F-Droid or GitHub.

## 2. Data on your device

The app stores what you enter or record in its private storage on the device. Other apps cannot access it, and
neither can we:

- **Trips:** date and time, start and destination, odometer readings, distance, category (e.g. business/private),
  business partner, route, notes, cancellation reason. With GPS recording also the distance measured by GPS; for
  automatically detected trips the coordinates of the start and end point.
- **Change log:** which details of a trip were changed and when, at the end of the trip, when accepting an
  automatically detected trip or later (old and new value).
- **Vehicles:** make, model, licence plate, fuel type, notes, and the name and hardware (MAC) address of the
  Bluetooth device you select for automatic trip detection.
- **Saved locations and templates:** name, coordinates, address, business partner, trip details of the template.
- **Settings:** e.g. driver name (shown in exports), reminder time, theme.

The app does not store a route (sequence of positions). While recording, it only keeps the latest position to keep
counting the distance.

The legal basis for this processing on your device is Art. 6(1)(b) GDPR (providing the app under the
[terms of use](nutzungsbedingungen.md#terms-of-use-for-the-fosstenbuch-app)). If you record data about other people
(e.g. business partners), you are responsible for it.

## 3. Permissions

All permissions are optional. The app only asks for them when you use the related feature. Without them you can still
start and end trips, just without a GPS distance.

| Permission | Purpose |
|---|---|
| Location (precise/approximate) | Measure a trip's distance by GPS; suggest a saved location near you (matched on the device only); save the current position as a saved location |
| Background location ("Allow all the time") | For automatic trip detection: recording while your phone is connected to the vehicle's Bluetooth, even when the app is closed. It also lets a running recording continue in the background after an app update. |
| Nearby devices (Bluetooth) | Select the vehicle's paired Bluetooth device and detect when it connects or disconnects. The app does not scan for other devices. |
| Notifications | Notice during a recording; daily reminder if enabled |
| Foreground service (location) | GPS recording with a notification |
| Run at startup | Reschedule the reminder; finish an automatic recording that was interrupted by switching off |
| Internet | Address search and place names (section 4) |

While recording, the app shows a notification. On Android 13 and later it only appears if you allow notifications;
otherwise Android lists the running recording only in its active-apps overview. The position itself is determined by
Android (GPS or, if GPS is off, the device's location service). Depending on your device settings, Google's or the
manufacturer's location service may process data for this; the app has no influence on that.

## 4. OpenStreetMap Nominatim (address search and place names)

For addresses the app uses the Nominatim service at `nominatim.openstreetmap.org`, operated by the
**OpenStreetMap Foundation (OSMF)**, St John's Innovation Centre, Cowley Road, Cambridge, CB4 0WS, United Kingdom.
The app connects directly from your device over an encrypted connection (HTTPS). We receive no data in the process.

A request is sent in two cases:

1. **Address search:** when you tap "Suchen" (Search) under "Adresse suchen (OpenStreetMap)" while creating or
   editing a saved location, your search term is sent.
2. **Place names for automatically detected trips:** after an automatically detected trip ends, the coordinates of
   the start and end point (rounded to about 10 m) are sent to turn them into an address. This does not happen for
   points within 500 m of a saved location. The app does not ask for each trip; it informs you before you select a
   Bluetooth device for automatic detection and before you allow location "all the time".

For technical reasons the OSMF also receives your IP address and the app's identifier ("FOSStenbuch-Android/1.0"),
but no device or user ID. The OSMF processes this data as an independent controller, according to its own statement
on servers in the United Kingdom and the Netherlands. The European Commission has adopted an adequacy decision for
the United Kingdom (Art. 45 GDPR). Details: [OSMF privacy policy](https://osmfoundation.org/wiki/Privacy_Policy),
contact privacy@osmfoundation.org.

The legal basis is Art. 6(1)(b) GDPR, as the request is needed for the feature you use. Providing this data is not
required by law or contract. Without the transfer only the address search and automatic place names do not work; the
app then stores the coordinates as the place name. You can avoid the transfer by not using the address search,
saving frequent places as saved locations, or not setting up automatic trip detection.

## 5. Export, backup and sharing

The app creates PDF and CSV exports and backups (JSON) on your device. Exports contain the selected trips with the
vehicle, driver name and, where applicable, the change log. A backup contains all trips (including coordinates),
vehicles with their Bluetooth device, categories, saved locations, templates and the change log, but no settings. The
files are not encrypted. You decide where a file goes using the Android share dialog (e.g. e-mail, cloud storage);
the recipient's privacy policy applies there. When restoring, the app only reads the file you select.

## 6. Android system backup

The app allows Android system backup so that your trips survive a change of device. If you have enabled backup to
your Google account or transfer to a new device, Android includes the app's data and restores it when you reinstall
the app. From Android 9 on, this backup is end-to-end encrypted if a screen lock is set. The backup is controlled by
Google or the device manufacturer and by you in the Android settings, where you can also switch it off. We have no
access to it.

## 7. App stores, contact and links

- **Google Play:** when you download the app, Google processes data under its
  [privacy policy](https://policies.google.com/privacy). Through the Google Play Console, Google gives us aggregated
  statistics (e.g. number of installs) and, if you allowed Android to share diagnostic data, crash and error reports
  without direct identifiers (device model, Android version, technical stack trace). We only use them to fix bugs
  (Art. 6(1)(f) GDPR; our legitimate interest is a working app). The reports stay in the Play Console until Google
  deletes them under its own retention periods; we do not store them outside the Play Console.
- **F-Droid and GitHub:** the privacy policies of these platforms apply.
- **Contact:** if you write to us (e.g. by e-mail or as a review on Google Play), we process your details (name,
  e-mail address, content) to reply. The legal basis is Art. 6(1)(b) GDPR where it concerns the use of the app,
  otherwise Art. 6(1)(f) GDPR (our legitimate interest in answering enquiries and reviews). E-mails are processed by
  our e-mail provider as a processor. We delete your message once the matter is settled, unless a statutory
  retention obligation applies.
- **Links:** links in the app (e.g. to GitHub, our website or this policy) open your browser, where the privacy policy
  of the respective website applies.

## 8. Retention and deletion

The data stays on your device until you delete it in the app (one by one or with "Settings › Delete all data"), clear
the app data in the Android settings or uninstall the app. Completed trips of a vehicle marked as "change-protected"
deliberately cannot be deleted one by one, only cancelled; you remove them completely with "Delete all data".

Export and backup files stay in the app's cache: a backup until the next backup, an export until you create another
export at least one day later. "Delete all data" removes them immediately; Android may also clear the cache earlier.
The app does not delete files you have shared or saved, or system backups.

## 9. No sale, no ads, no profiling

We do not sell any data. Apart from the requests to the OSMF (section 4), the app sends no data to third parties. The
app contains no ads, no analytics or tracking services and no other libraries that send data to third parties. There
is no automated decision-making and no profiling.

## 10. Your rights

You have the right of access (Art. 15 GDPR), rectification (Art. 16), erasure (Art. 17), restriction of processing
(Art. 18) and data portability (Art. 20). As we do not receive your app data, you can exercise most of these rights
directly in the app: view, edit, delete, export. For data held by the OSMF, contact privacy@osmfoundation.org.
Questions to us: info@garske-systems.de.

**Right to object (Art. 21 GDPR):** where we process data on the basis of Art. 6(1)(f) GDPR (section 7), you can
object at any time on grounds relating to your particular situation, informally by e-mail to info@garske-systems.de.
The easiest way to stop future crash reports is to turn off sharing of usage and diagnostics data in the Android
settings.

You can also lodge a complaint with a data protection supervisory authority (Art. 77 GDPR), for example the authority
responsible for us: Landesbeauftragte für Datenschutz und Informationsfreiheit Nordrhein-Westfalen,
Kavalleriestraße 2–4, 40213 Düsseldorf, Germany.

## 11. Children

The app is intended for adults (18 and over) who keep a logbook.

## 12. Security

The data is kept in the app's private storage, and the connection to Nominatim is encrypted. Protect your device with
a screen lock and keep exports and backups safe.

## 13. Changes

We update this policy when the app or the law changes. The current version is always available at
<https://garske-systems.github.io/FOSStenbuch/datenschutz.html>. Earlier versions are in the
[version history](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/datenschutz.md).
