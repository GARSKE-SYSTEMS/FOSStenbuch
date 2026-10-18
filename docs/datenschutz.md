# Datenschutzerklärung für die App „FOSStenbuch“

Stand: 18. Oktober 2026 · [English version below](#privacy-policy-for-the-fosstenbuch-app)

## Das Wichtigste in Kürze

- FOSStenbuch speichert alle Ihre Fahrten, Fahrzeuge, Standorte und Einstellungen auf Ihrem Gerät. Wir erhalten
  diese Daten nicht. Ausnahme: Ist die Android-Sicherung in Ihr Google-Konto eingeschaltet, sichert Android die
  App-Daten dort mit (Abschnitt 7).
- Kein Benutzerkonto, keine Werbung, keine Analyse- oder Tracking-Dienste, keine Absturzberichte aus der App.
- Online-Verbindungen baut die App nur für zwei Funktionen auf: die Adresssuche und Ortsnamen über
  **OpenStreetMap Nominatim** (Abschnitt 4) und – nur wenn Sie sie einschalten – **Zeitstempel** eines
  Zeitstempeldienstes, an den dabei nur ein Fingerabdruck geht (Abschnitt 5). Einen eigenen Server betreiben wir
  dafür nicht.

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
  oder später wann geändert wurden (alter und neuer Wert), auch Stornierungen.
- **Siegel:** Beim Beenden, Übernehmen, Ändern und Stornieren einer Fahrt speichert die App ein Siegel: Anlass,
  Zeitpunkt (Uhr des Geräts) und einen SHA-256-Fingerabdruck der Fahrtdaten; ist ein Signaturschlüssel eingerichtet,
  außerdem Signatur und öffentlichen Schlüssel, bei eingeschalteten Zeitstempeln auch den Zeitstempel samt Zeit und
  Adresse des Dienstes (Abschnitt 5).
- **Signaturschlüssel (optional):** Erzeugen oder importieren Sie einen Schlüssel (in den Einstellungen oder auf
  Nachfrage beim Einschalten der Bearbeitungssperre), speichert die
  App den privaten Schlüssel verschlüsselt, und zwar mit einem Schlüssel aus dem Android-Schlüsselspeicher (Android
  Keystore), der sich nicht aus dem Gerät auslesen lässt. Die App sendet den privaten Schlüssel nirgendwohin. Eine
  Schlüsseldatei entsteht nur, wenn Sie ihn exportieren: Sie ist mit Ihrem Passwort verschlüsselt (PBKDF2,
  AES-256-GCM), und Sie bestimmen, wo sie gespeichert wird. Der öffentliche Schlüssel und sein Fingerabdruck stehen in
  Siegeln, Backups und signierten PDFs; wer mehrere davon hat, erkennt daran, dass sie mit demselben Schlüssel
  erstellt wurden.
- **Fahrzeuge:** Hersteller, Modell, Kennzeichen, Kraftstoff, Notizen, Zeitpunkt des Einschaltens der
  Bearbeitungssperre sowie Name und Hardware-Adresse (MAC) des Bluetooth-Geräts, das Sie für die automatische
  Fahrterkennung auswählen.
- **Gespeicherte Standorte und Vorlagen:** Name, Koordinaten, Adresse, Geschäftspartner, Fahrtdaten der Vorlage.
- **Einstellungen:** z. B. Fahrername (erscheint in Exporten), Erinnerungszeit, Design, Zeitstempel (an/aus und
  gewählter Dienst).
- **Verlauf:** Wann ein Backup eingespielt oder „Alle Daten löschen“ ausgeführt wurde, samt der Zahl der Fahrten, die
  dabei nicht zu ihrem Siegel passten. Er liegt bei den Einstellungen und erscheint im ausführlichen PDF.

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
| Internet | Adresssuche und Ortsnamen (Abschnitt 4); Zeitstempel, falls eingeschaltet (Abschnitt 5) |

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

## 5. Zeitstempeldienste (optional, standardmäßig aus)

Unter „Einstellungen › Signatur und Zeitstempel“ können Sie „Zeitstempel (online)“ einschalten; beim Einschalten der
Bearbeitungssperre fragt die App auch danach. Dann lässt die App
jedes neue Siegel (Abschnitt 2) von einem Zeitstempeldienst nach RFC 3161 bestätigen. Siegel, die noch keinen
Zeitstempel haben, holt sie nach einem neuen Siegel, beim Start der App und auf „Jetzt nachholen“ nach – auch solche,
die vor dem Einschalten entstanden sind. Die App verbindet sich direkt von Ihrem Gerät mit dem gewählten Dienst. Wir
betreiben dafür keinen eigenen Server und erhalten keine Daten.

Je Siegel werden nur übertragen: ein SHA-256-Fingerabdruck (32 Byte, gebildet aus den Fahrtdaten und, falls
vorhanden, Signatur und öffentlichem Schlüssel; er enthält keine Fahrtdaten im Klartext), eine Zufallszahl und die Kennung der App
(„FOSStenbuch-Android/1.0“). Technisch bedingt erhält der Dienst außerdem Ihre IP-Adresse und den Zeitpunkt der
Anfrage. Die Antwort, der Zeitstempel, enthält Zeit, Seriennummer und Zertifikat des Dienstes und wird beim Siegel
gespeichert.

Vorgegebene Dienste (voreingestellt ist FreeTSA):

| Dienst | Betreiber | Land |
|---|---|---|
| FreeTSA (`https://freetsa.org/tsr`) | FreeTSA.org, ein privates Projekt (laut Zertifikat des Dienstes in Würzburg) | Deutschland |
| Sectigo Qualified, eIDAS (`https://timestamp.sectigo.com/qualified`) | Sectigo (Europe) SL, Barcelona | Spanien |
| Sectigo (`https://timestamp.sectigo.com`) | Sectigo Limited | Vereinigtes Königreich |

Die Dienste verarbeiten diese Daten in eigener Verantwortung nach ihren eigenen Bestimmungen. Für Sectigo gilt die
[Datenschutzerklärung von Sectigo](https://www.sectigo.com/privacy-policy); sie nennt mögliche Übermittlungen in
Länder außerhalb Europas, etwa in die USA, und die dafür genutzten Garantien. Für das Vereinigte Königreich besteht
ein Angemessenheitsbeschluss der EU-Kommission (Art. 45 DSGVO). FreeTSA veröffentlicht nach unserer Kenntnis keine
eigene Datenschutzerklärung, nur [Nutzungsrichtlinien](https://freetsa.org/freetsa.html).

Tragen Sie unter „Eigene URL …“ einen anderen Dienst ein, gehen dieselben Daten an diesen. Bei einer http-Adresse ist
die Verbindung unverschlüsselt: Dritte im Netz können dann sehen, dass und wann Sie einen Zeitstempel anfordern, und
den Fingerabdruck mitlesen (fälschen können sie den Zeitstempel nicht).

Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO, weil die Anfrage die von Ihnen eingeschaltete Funktion erst
ermöglicht. Die Angaben sind weder gesetzlich noch vertraglich vorgeschrieben. Ohne Zeitstempel funktioniert die App
vollständig, die Siegel haben dann nur keinen. Sie können die Funktion jederzeit in den Einstellungen ausschalten.

## 6. Export, Backup, Teilen und Prüfseite

PDF- und CSV-Exporte sowie Backups (JSON) erstellt die App auf Ihrem Gerät. Exporte enthalten die gewählten Fahrten
mit Fahrzeug, Fahrername und gegebenenfalls dem Änderungsprotokoll. Das ausführliche PDF enthält außerdem die Siegel
samt Zeitstempeln (Zeit und Adresse des Dienstes) und den Verlauf aus Abschnitt 2, dazu für die Prüfseite
maschinenlesbar die Siegel, Zeitstempel und gesiegelten Fahrtdaten mit ihren internen Nummern und genauen Zeiten –
aber keine Koordinaten und bei Privatfahrten weder Notiz noch Geschäftspartner (hat eine Privatfahrt welche,
fehlen auch ihre Siegel). Die CSV-Datei enthält zusätzlich je Fahrt ihre ID und die Zahl der protokollierten
Änderungen. Ist ein Signaturschlüssel eingerichtet, signiert die App
jedes PDF; es enthält dann Signatur, öffentlichen Schlüssel und dessen Fingerabdruck. Ein Backup enthält alle Fahrten
(auch Koordinaten), Fahrzeuge samt Bluetooth-Gerät, Kategorien, Standorte, Vorlagen, das Änderungsprotokoll und die
Siegel (samt Signaturen, öffentlichen Schlüsseln und Zeitstempeln), aber keine Einstellungen, keinen privaten
Schlüssel und nicht den Verlauf. Die Dateien sind nicht verschlüsselt. Über den Android-Teilen-Dialog entscheiden Sie
selbst, wohin eine Datei geht (z. B. E-Mail, Cloud-Speicher). Für den Empfänger gilt dessen Datenschutzerklärung. Beim
Wiederherstellen liest die App nur die Datei, die Sie auswählen.

Auf der Prüfseite <https://garske-systems.github.io/FOSStenbuch/pruefen.html> lassen sich PDFs und Backups
prüfen. Die Prüfung läuft nur in Ihrem Browser; die Datei wird nicht hochgeladen. Wie diese Erklärung liegt
die Seite bei GitHub Pages; beim Aufruf verarbeitet GitHub Zugriffsdaten wie Ihre IP-Adresse nach seiner
[Datenschutzerklärung](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## 7. Android-Systemsicherung

Die App lässt die Android-Systemsicherung zu, damit Ihre Fahrten einen Gerätewechsel überstehen. Haben Sie auf Ihrem
Gerät die Sicherung in Ihr Google-Konto oder die Übertragung auf ein neues Gerät eingeschaltet, sichert Android die
Daten der App dabei mit und stellt sie bei einer Neuinstallation wieder her. Ab Android 9 ist diese Sicherung
Ende-zu-Ende-verschlüsselt, wenn eine Displaysperre eingerichtet ist. Die Sicherung steuern Google bzw. der
Gerätehersteller und Sie in den Android-Einstellungen; dort können Sie sie auch ausschalten. Wir haben darauf keinen
Zugriff. Ein Signaturschlüssel wird dabei nur verschlüsselt mitgesichert und lässt sich auf einem anderen Gerät
nicht entschlüsseln; zum Übertragen dient die Schlüsseldatei (Abschnitt 2).

## 8. Bezug über App-Stores, Kontakt und Links

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

## 9. Speicherdauer und Löschung

Die Daten bleiben auf Ihrem Gerät, bis Sie sie in der App löschen, die App-Daten in den Android-Einstellungen löschen
oder die App deinstallieren. Abgeschlossene Fahrten lassen sich bewusst nicht einzeln löschen, sondern nur
stornieren; einzeln löschen lassen sich nur automatisch erkannte, noch nicht übernommene Fahrten und eine gerade erst
gestartete Fahrt (in den ersten 5 Minuten). Vollständig entfernen Sie Fahrten mit „Einstellungen › Alle Daten
löschen“ oder durch Deinstallieren. Standorte und Vorlagen löschen Sie einzeln, Fahrzeuge und Kategorien, solange
keine Fahrt sie verwendet.

„Alle Daten löschen“ entfernt Fahrten samt Änderungsprotokoll und Siegeln, Fahrzeuge, Standorte, Kategorien,
Vorlagen, den Fahrernamen sowie Export- und Backup-Dateien. Es behält den Verlauf (der das Löschen vermerkt), den
Signaturschlüssel und die übrigen Einstellungen. Den Schlüssel entfernen Sie mit „Schlüssel entfernen“; den Verlauf
nur, indem Sie die App-Daten in den Android-Einstellungen löschen oder die App deinstallieren. Wie lange
Zeitstempeldienste Anfragen speichern, bestimmen deren Betreiber (Abschnitt 5).

Export- und Backup-Dateien liegen im Zwischenspeicher der App: ein Backup bis zum nächsten Backup, ein Export, bis
Sie frühestens einen Tag später einen weiteren Export erstellen. „Alle Daten löschen“ entfernt sie sofort; Android
kann den Zwischenspeicher auch früher leeren. Dateien, die Sie geteilt oder gespeichert haben, und Systemsicherungen
löscht die App nicht.

## 10. Kein Verkauf, keine Werbung, kein Profiling

Wir verkaufen keine Daten. Außer den Anfragen an die OSMF (Abschnitt 4) und – nur wenn Sie Zeitstempel einschalten –
an den gewählten Zeitstempeldienst (Abschnitt 5) übermittelt die App keine Daten an Dritte.
Die App enthält keine Werbung, keine Analyse- oder Tracking-Dienste und keine weiteren Bibliotheken, die Daten an
Dritte senden. Es gibt keine automatisierte Entscheidungsfindung und kein Profiling.

## 11. Ihre Rechte

Sie haben das Recht auf Auskunft (Art. 15 DSGVO), Berichtigung (Art. 16), Löschung (Art. 17), Einschränkung der
Verarbeitung (Art. 18) und Datenübertragbarkeit (Art. 20). Da wir Ihre App-Daten nicht erhalten, können Sie die
meisten Rechte direkt in der App ausüben: ansehen, ändern, löschen, exportieren. Für Daten bei der OSMF wenden Sie
sich an privacy@osmfoundation.org, für Daten bei einem Zeitstempeldienst an dessen Betreiber. Für Fragen an uns:
info@garske-systems.de.

**Widerspruchsrecht (Art. 21 DSGVO):** Soweit wir Daten auf Grundlage von Art. 6 Abs. 1 lit. f DSGVO verarbeiten
(Abschnitt 8), können Sie dem aus Gründen, die sich aus Ihrer besonderen Situation ergeben, jederzeit widersprechen,
formlos per E-Mail an info@garske-systems.de. Künftige Absturzberichte verhindern Sie am einfachsten, indem Sie in den
Android-Einstellungen das Teilen von Nutzungs- und Diagnosedaten ausschalten.

Sie können sich außerdem bei einer Datenschutz-Aufsichtsbehörde beschweren (Art. 77 DSGVO), zum Beispiel bei der
für uns zuständigen Landesbeauftragten für Datenschutz und Informationsfreiheit Nordrhein-Westfalen,
Kavalleriestraße 2–4, 40213 Düsseldorf.

## 12. Kinder

Die App richtet sich an Erwachsene (ab 18 Jahren), die ein Fahrtenbuch führen.

## 13. Sicherheit

Die Daten liegen im privaten Speicher der App, die Verbindungen zu Nominatim und zu den vorgegebenen
Zeitstempeldiensten sind verschlüsselt (HTTPS). Schützen Sie Ihr Gerät mit einer Displaysperre, bewahren Sie Exporte
und Backups sicher auf und Schlüsseldatei und Passwort getrennt voneinander.

## 14. Änderungen

Wir passen diese Erklärung an, wenn sich die App oder die Rechtslage ändert. Die aktuelle Fassung steht immer unter
<https://garske-systems.github.io/FOSStenbuch/datenschutz.html>. Frühere Fassungen finden Sie in der
[Versionsgeschichte](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/datenschutz.md).

---

# Privacy Policy for the FOSStenbuch App

Last updated: 18 October 2026. This is a translation; if the two versions differ, the German version above applies.

## Summary

- FOSStenbuch stores all your trips, vehicles, saved locations and settings on your device. We do not receive this
  data. Exception: if Android backup to your Google account is switched on, Android includes the app data in it
  (section 7).
- No user account, no ads, no analytics or tracking services, no crash reporting from the app.
- The app only goes online for two features: address search and place names via **OpenStreetMap Nominatim**
  (section 4) and – only if you switch them on – **timestamps** from a timestamp service, which only receives a
  fingerprint (section 5). We do not run a server of our own for this.

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
  automatically detected trip or later (old and new value), including cancellations.
- **Seals:** when a trip is ended, accepted, edited or cancelled, the app stores a seal: occasion, time (device clock)
  and a SHA-256 fingerprint of the trip data; if a signing key is set up, also the signature and public key, and with
  timestamps switched on the timestamp with the time and address of the service (section 5).
- **Signing key (optional):** if you create or import a key (in the settings, or when asked while switching on the
  edit lock), the app stores the private key
  encrypted with a key from the Android Keystore that cannot be read out of the device. The app never sends the
  private key anywhere. A key file is only created when you export the key: it is encrypted with your password
  (PBKDF2, AES-256-GCM), and you decide where it is saved. The public key and its fingerprint appear in seals,
  backups and signed PDFs; anyone who has several of them can tell that they were made with the same key.
- **Vehicles:** make, model, licence plate, fuel type, notes, the time the edit lock was switched on, and the name
  and hardware (MAC) address of the Bluetooth device you select for automatic trip detection.
- **Saved locations and templates:** name, coordinates, address, business partner, trip details of the template.
- **Settings:** e.g. driver name (shown in exports), reminder time, theme, timestamps (on/off and selected service).
- **History:** when a backup was restored or "Delete all data" was used, with the number of trips that did not match
  their seal at that time. It is kept with the settings and appears in the detailed PDF.

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
| Internet | Address search and place names (section 4); timestamps, if switched on (section 5) |

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

## 5. Timestamp services (optional, off by default)

Under "Einstellungen › Signatur und Zeitstempel" (Settings › Signature and timestamps) you can switch on "Zeitstempel
(online)"; the app also offers this when you switch on the edit lock. The app then has every new seal (section 2) confirmed by an RFC 3161 timestamp service. It requests the
missing timestamps after a new seal, when the app starts and on "Jetzt nachholen" (Catch up now) – also for seals
created before the switch was turned on. The app connects directly from your device to the selected service.
We do not run a server of our own for this and receive no data.

Per seal only the following is sent: a SHA-256 fingerprint (32 bytes, derived from the trip data and, if present,
the signature and public key; it contains no trip data in plain text), a random number and the app's identifier
("FOSStenbuch-Android/1.0"). For technical reasons the service also receives your IP address and the time of the
request. The response, the timestamp, contains the time, serial number and certificate of the service and is stored
with the seal.

Predefined services (FreeTSA is preselected):

| Service | Operator | Country |
|---|---|---|
| FreeTSA (`https://freetsa.org/tsr`) | FreeTSA.org, a private project (located in Würzburg according to the service's certificate) | Germany |
| Sectigo Qualified, eIDAS (`https://timestamp.sectigo.com/qualified`) | Sectigo (Europe) SL, Barcelona | Spain |
| Sectigo (`https://timestamp.sectigo.com`) | Sectigo Limited | United Kingdom |

The services process this data as independent controllers under their own terms. For Sectigo, the
[Sectigo privacy policy](https://www.sectigo.com/privacy-policy) applies; it mentions possible transfers to countries
outside Europe, such as the US, and the safeguards used. The European Commission has adopted an adequacy decision for
the United Kingdom (Art. 45 GDPR). To our knowledge FreeTSA does not publish a privacy policy of its own, only
[usage policies](https://freetsa.org/freetsa.html).

If you enter another service under "Eigene URL …" (Custom URL), the same data goes to that service. With an http
address the connection is unencrypted: others on the network can see that and when you request a timestamp and read
the fingerprint (they cannot forge the timestamp).

The legal basis is Art. 6(1)(b) GDPR, as the request is needed for the feature you switched on. Providing this data
is not required by law or contract. The app works fully without timestamps; the seals then simply have none. You can
switch the feature off at any time in the settings.

## 6. Export, backup, sharing and verification page

The app creates PDF and CSV exports and backups (JSON) on your device. Exports contain the selected trips with the
vehicle, driver name and, where applicable, the change log. The detailed PDF also contains the seals with their
timestamps (time and address of the service) and the history from section 2, plus, in machine-readable form for the
verification page, the seals, timestamps and sealed trip data with their internal numbers and exact times – but no
coordinates and, for private trips, neither notes nor business partner (if a private trip has them, its seals are
left out as well). The CSV file also contains each trip's ID and number of logged changes. If a signing key is set up, the app signs every PDF; it then contains the signature,
the public key and its fingerprint. A backup contains all trips (including coordinates), vehicles with their
Bluetooth device, categories, saved locations, templates, the change log and the seals (with signatures, public keys
and timestamps), but no settings, no private key and not the history. The files are not encrypted. You decide where
a file goes using the Android share dialog (e.g. e-mail, cloud storage); the recipient's privacy policy applies
there. When restoring, the app only reads the file you select.

PDFs and backups can be checked on the verification page
<https://garske-systems.github.io/FOSStenbuch/pruefen.html>. The check runs only in your browser; the file is not
uploaded. Like this policy, the page is hosted on GitHub Pages; when you open it, GitHub processes access data such as
your IP address under its
[privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## 7. Android system backup

The app allows Android system backup so that your trips survive a change of device. If you have enabled backup to
your Google account or transfer to a new device, Android includes the app's data and restores it when you reinstall
the app. From Android 9 on, this backup is end-to-end encrypted if a screen lock is set. The backup is controlled by
Google or the device manufacturer and by you in the Android settings, where you can also switch it off. We have no
access to it. A signing key is only included in encrypted form and cannot be decrypted on another device; use the key
file to transfer it (section 2).

## 8. App stores, contact and links

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

## 9. Retention and deletion

The data stays on your device until you delete it in the app, clear the app data in the Android settings or uninstall
the app. Completed trips deliberately cannot be deleted one by one, only cancelled; only automatically detected trips
that have not been accepted yet and a trip started just now (within the first 5 minutes) can be deleted individually.
You remove trips completely with "Settings › Delete all data" or by uninstalling. Saved locations and templates can be
deleted one by one, vehicles and categories as long as no trip uses them.

"Delete all data" removes trips with their change log and seals, vehicles, saved locations, categories, templates,
the driver name and export and backup files. It keeps the history (which records the deletion), the signing key and
the other settings. You remove the key with "Schlüssel entfernen" (Remove key); the history only by clearing the app
data in the Android settings or uninstalling the app. How long timestamp services keep requests is up to their
operators (section 5).

Export and backup files stay in the app's cache: a backup until the next backup, an export until you create another
export at least one day later. "Delete all data" removes them immediately; Android may also clear the cache earlier.
The app does not delete files you have shared or saved, or system backups.

## 10. No sale, no ads, no profiling

We do not sell any data. Apart from the requests to the OSMF (section 4) and – only if you switch on timestamps – to
the selected timestamp service (section 5), the app sends no data to third parties.
The app contains no ads, no analytics or tracking services and no other libraries that send data to third parties. There
is no automated decision-making and no profiling.

## 11. Your rights

You have the right of access (Art. 15 GDPR), rectification (Art. 16), erasure (Art. 17), restriction of processing
(Art. 18) and data portability (Art. 20). As we do not receive your app data, you can exercise most of these rights
directly in the app: view, edit, delete, export. For data held by the OSMF, contact privacy@osmfoundation.org; for
data held by a timestamp service, contact its operator. Questions to us: info@garske-systems.de.

**Right to object (Art. 21 GDPR):** where we process data on the basis of Art. 6(1)(f) GDPR (section 8), you can
object at any time on grounds relating to your particular situation, informally by e-mail to info@garske-systems.de.
The easiest way to stop future crash reports is to turn off sharing of usage and diagnostics data in the Android
settings.

You can also lodge a complaint with a data protection supervisory authority (Art. 77 GDPR), for example the authority
responsible for us: Landesbeauftragte für Datenschutz und Informationsfreiheit Nordrhein-Westfalen,
Kavalleriestraße 2–4, 40213 Düsseldorf, Germany.

## 12. Children

The app is intended for adults (18 and over) who keep a logbook.

## 13. Security

The data is kept in the app's private storage, and the connections to Nominatim and to the predefined timestamp
services are encrypted (HTTPS). Protect your device with a screen lock, keep exports and backups safe, and keep the
key file and its password separate from each other.

## 14. Changes

We update this policy when the app or the law changes. The current version is always available at
<https://garske-systems.github.io/FOSStenbuch/datenschutz.html>. Earlier versions are in the
[version history](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/datenschutz.md).
