# Nutzungsbedingungen für die App „FOSStenbuch“

Stand: 18. Oktober 2026 · [English version below](#terms-of-use-for-the-fosstenbuch-app)

## 1. Anbieter und Geltungsbereich

Anbieter der App „FOSStenbuch“ (Paketname `de.garsys.fosstenbuch`) ist:

Garske Systems, Inh. Patrick M. Garske<br>
An der Bausenheide 55, 40474 Düsseldorf, Deutschland<br>
E-Mail: info@garske-systems.de · [Impressum](https://garske-systems.de/impressum)

Diese Bedingungen gelten für die Nutzung der App, egal woher Sie sie beziehen (z. B. Google Play, F-Droid oder GitHub).
Wie die App mit Daten umgeht, steht in der [Datenschutzerklärung](datenschutz.md).

## 2. Was die App leistet

1. Die App ist kostenlos. Sie enthält keine Werbung und keine In-App-Käufe und braucht kein Benutzerkonto.
2. Mit der App erfassen, prüfen, werten und exportieren Sie Fahrten auf Ihrem eigenen Gerät. Fahrten werden gestartet
   und beendet (von Hand oder automatisch über das Bluetooth des Fahrzeugs); spätere Änderungen protokolliert die
   App. Abgeschlossene Fahrten lassen sich stornieren, aber nicht einzeln löschen. Die App speichert alle Daten nur
   auf dem Gerät (zur Android-Systemsicherung siehe Datenschutzerklärung, Abschnitt 7).
3. Es besteht kein Anspruch auf bestimmte Funktionen, Updates, Fehlerbehebungen, Support oder ständige
   Verfügbarkeit. Funktionen können geändert oder eingestellt werden.
4. Die Adresssuche und die Ortsnamen automatisch erkannter Fahrten nutzen den freien Dienst Nominatim der
   OpenStreetMap Foundation, die optionalen Zeitstempel einen Zeitstempeldienst Ihrer Wahl (Datenschutzerklärung,
   Abschnitt 5). Diese Dienste betreiben Dritte, nicht wir. Auf ihre Verfügbarkeit, ihre Bedingungen und die
   Richtigkeit ihrer Daten haben wir keinen Einfluss. Die vorgegebenen Zeitstempeldienste sind derzeit ohne Konto und
   kostenlos nutzbar; ob das so bleibt, bestimmen ihre Betreiber.

## 3. Open-Source-Lizenz

Der Quellcode ist unter der [MIT-Lizenz](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/blob/main/LICENSE) veröffentlicht. Sie regelt Ihre Rechte, den Code zu nutzen, zu
verändern und weiterzugeben. Diese Nutzungsbedingungen schränken diese Rechte nicht ein. Für die Haftung gilt Ziffer 6. Für Inhalte Dritter gelten deren Lizenzen (siehe [README, Abschnitt „Drittinhalte“](https://github.com/GARSKE-SYSTEMS/FOSStenbuch#drittinhalte)),
insbesondere für Adressdaten: © OpenStreetMap-Mitwirkende,
[Open Database License (ODbL)](https://www.openstreetmap.org/copyright).

## 4. Keine Steuer- oder Rechtsberatung; was die Schutzfunktionen leisten

1. Die App ist ein Hilfsmittel. Ob ein damit geführtes Fahrtenbuch steuerlich anerkannt wird, hängt davon ab, wie Sie
   es führen (unter anderem zeitnah, vollständig und so, dass nachträgliche Änderungen ausgeschlossen oder erkennbar
   sind), und von der Beurteilung durch Ihr Finanzamt. Für die Anerkennung übernehmen wir keine Gewähr. Angaben wie
   Geschäftspartner und Anlass einer Fahrt tragen Sie selbst ein; die App kann nicht prüfen, ob sie vollständig sind.
2. **Bearbeitungssperre:** Für ein Fahrzeug eingeschaltet, sperrt sie in der App das Bearbeiten abgeschlossener
   Fahrten und die Fahrzeugdaten (Hersteller, Modell, Kennzeichen, Kraftstoff); ausschalten lässt sie sich nicht.
   Stornieren bleibt möglich und wird protokolliert. Möglich bleiben auch das Wiederherstellen eines Backups und
   „Alle Daten löschen“; beides vermerkt die App in einem Verlauf, der im ausführlichen PDF erscheint. Ein
   eingespieltes Backup bestimmt auch, ob und seit wann die Sperre gilt – fehlt sie dort, ist sie danach aufgehoben;
   das Einspielen steht im Verlauf. Dieser Verlauf liegt auf dem Gerät und geht verloren, wenn Sie die App-Daten
   löschen oder die App deinstallieren. Ohne Bearbeitungssperre bleiben abgeschlossene Fahrten bearbeitbar; die
   Änderungen werden protokolliert. Eine Hash-Kette erkennt beim Wiederherstellen versehentliche oder einfache
   Änderungen an Backup-Dateien.
3. **Siegel:** Beim Beenden, Übernehmen, Ändern und Stornieren speichert die App einen SHA-256-Fingerabdruck der
   Fahrtdaten. Ohne Signatur erkennt ein Siegel nur versehentliche oder einfache Änderungen, denn die Berechnung ist
   öffentlich.
4. **Signaturen:** Mit Ihrem Schlüssel signierte Siegel und PDFs belegen, dass sie mit diesem Schlüssel erstellt und
   danach nicht verändert wurden. Sie belegen weder den Zeitpunkt noch, wer den Schlüssel benutzt hat: Wer
   Schlüsseldatei und Passwort hat, kann damit signieren. Es handelt sich nicht um eine qualifizierte elektronische
   Signatur im Sinne der eIDAS-Verordnung.
5. **Zeitstempel:** belegen, dass es ein Siegel zu der angegebenen Zeit schon gab – Beweiswert hat das nur bei einem
   unabhängigen Dienst; ein selbst betriebener Dienst belegt gegenüber Dritten nichts. Siegel, die vor dem
   Einschalten entstanden sind, erhalten einen späteren Zeitstempel. Dass die Angaben stimmen, belegt ein Zeitstempel
   nicht.
6. **Nicht erfasst oder belegt** wird insbesondere: ob alle Fahrten erfasst wurden; ob die Uhr des Geräts stimmte
   (alle Zeiten außer denen der Zeitstempel stammen von ihr); Änderungen an Fahrzeug-, Kategorie- und
   Standortdaten; per GPS gemessene Strecken (sie sind nicht gesiegelt); was ein eingespieltes Backup gegenüber dem
   vorherigen Stand ersetzt hat (vermerkt wird nur das Einspielen). Gegen gezielte Manipulation – etwa mit
   Root-Zugriff, einer veränderten App-Version (der Quellcode ist öffentlich) oder neu berechneten Siegeln und Ketten
   ohne Signatur – schützen Bearbeitungssperre, Hash-Kette und Siegel ohne Signatur nicht.
7. Keine dieser Funktionen ist von einer Behörde, einem Prüfer oder einer Zertifizierungsstelle zertifiziert oder
   testiert.
8. Für die Richtigkeit und Vollständigkeit Ihrer Eingaben und für die Aufbewahrung Ihrer Aufzeichnungen sind Sie
   selbst verantwortlich. Ziehen Sie im Zweifel eine Steuerberaterin oder einen Steuerberater hinzu.
9. Automatisch erkannte Fahrten, per GPS gemessene Strecken und Adressen können ungenau sein. Prüfen Sie sie, bevor
   Sie sie übernehmen.

## 5. Ihre Pflichten

1. Bedienen Sie die App nicht, während Sie ein Fahrzeug führen (§ 23 Abs. 1a StVO). Die automatische Fahrterkennung
   arbeitet ohne Bedienung.
2. Sichern Sie Ihre Daten regelmäßig mit der Backup-Funktion. Bewahren Sie Exporte und Backups sicher auf: Sie sind
   nicht verschlüsselt. Wenn Sie die App deinstallieren oder ihre Daten löschen, sind die Daten auf dem Gerät weg.
3. Erfassen Sie personenbezogene Daten anderer (z. B. Namen von Geschäftspartnern), sind Sie dafür selbst
   verantwortlich.
4. Nutzen Sie die Adresssuche nur im üblichen Umfang. Die
   [Nutzungsregeln von Nominatim](https://operations.osmfoundation.org/policies/nominatim/) verbieten Massenabfragen.
   Schalten Sie Zeitstempel ein, gelten zusätzlich die Bedingungen des gewählten Dienstes.
5. Bewahren Sie eine exportierte Schlüsseldatei und ihr Passwort sicher und getrennt voneinander auf. Ohne beides
   lässt sich Ihr Schlüssel nach einem Gerätewechsel nicht wiederherstellen; wer beides hat, kann mit Ihrem Schlüssel
   signieren.

## 6. Haftung

Die App wird unentgeltlich und ohne Gewähr überlassen. Wir haften nicht für Schäden aus ihrer Nutzung, insbesondere
nicht für Datenverlust – unabhängig davon, ob und wie Sie Ihre Daten gesichert haben. Das gilt nur nicht, soweit das
Gesetz eine Haftung zwingend vorschreibt: bei Vorsatz und grober Fahrlässigkeit, bei arglistig verschwiegenen Mängeln,
bei Verletzung des Lebens, des Körpers oder der Gesundheit, nach dem Produkthaftungsgesetz und nach anderen
zwingenden Vorschriften (z. B. Art. 82 DSGVO).

## 7. Änderungen dieser Bedingungen

Ändern wir diese Bedingungen, etwa bei neuen Funktionen oder einer geänderten Rechtslage, gilt die neue Fassung für
künftige Vertragsschlüsse, etwa wenn Sie die App neu herunterladen. Für bisherige Nutzerinnen und Nutzer gilt sie nur,
wenn sie ihr zustimmen. Die aktuelle Fassung steht immer unter
<https://garske-systems.github.io/FOSStenbuch/nutzungsbedingungen.html>. Frühere Fassungen finden Sie in der
[Versionsgeschichte](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/nutzungsbedingungen.md).

## 8. Schlussbestimmungen

1. Es gilt deutsches Recht unter Ausschluss des UN-Kaufrechts. Als Verbraucherin oder Verbraucher behalten Sie den
   Schutz der zwingenden Vorschriften des Staates, in dem Sie Ihren gewöhnlichen Aufenthalt haben.
2. Wir sind nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor einer
   Verbraucherschlichtungsstelle teilzunehmen.

---

# Terms of Use for the FOSStenbuch App

Last updated: 18 October 2026. This is a translation; if the two versions differ, the German version above applies.

## 1. Provider and scope

The app "FOSStenbuch" (package name `de.garsys.fosstenbuch`) is provided by Garske Systems, owner Patrick M. Garske,
An der Bausenheide 55, 40474 Düsseldorf, Germany, e-mail info@garske-systems.de
([legal notice](https://garske-systems.de/impressum)). These terms apply no matter where you got the app (e.g. Google
Play, F-Droid or GitHub). How the app handles data is described in the [privacy policy](datenschutz.md#privacy-policy-for-the-fosstenbuch-app).

## 2. What the app does

1. The app is free of charge. It has no ads, no in-app purchases and needs no user account.
2. You use it to record, review, analyse and export trips on your own device. Trips are started and ended (manually
   or automatically via the vehicle's Bluetooth); the app logs later changes. Completed trips can be cancelled but not
   deleted one by one. The app stores all data only on the device (for Android system backup, see section 7 of the
   privacy policy).
3. There is no entitlement to particular features, updates, bug fixes, support or availability. Features may change
   or be discontinued.
4. Address search and place names for automatically detected trips use the free Nominatim service of the
   OpenStreetMap Foundation; the optional timestamps use a timestamp service of your choice (privacy policy,
   section 5). These services are run by third parties, not by us. We have no influence on their availability, their
   terms or the accuracy of their data. The predefined timestamp services can currently be used free of charge and
   without an account; whether that stays so is up to their operators.

## 3. Open source licence

The source code is published under the [MIT License](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/blob/main/LICENSE), which governs your rights to use, modify and share
the code. These terms do not restrict those rights. Liability is governed by section 6. Third-party
content is subject to its own licences (see the [README, section "Drittinhalte"](https://github.com/GARSKE-SYSTEMS/FOSStenbuch#drittinhalte)), in
particular address data: © OpenStreetMap contributors,
[Open Database License (ODbL)](https://www.openstreetmap.org/copyright).

## 4. No tax or legal advice; what the protection features do

1. The app is a tool. Whether a logbook kept with it is accepted for tax purposes depends on how you keep it (among
   other things promptly, completely and so that later changes are impossible or detectable) and on your tax
   office. We give no guarantee that it will be accepted. You enter details such as the business partner and purpose
   of a trip yourself; the app cannot check whether they are complete.
2. **Edit lock ("Bearbeitungssperre"):** when switched on for a vehicle, it locks editing of completed trips and of the
   vehicle data (make, model, licence plate, fuel type) in the app; it cannot be switched off. Cancelling remains
   possible and is logged. Restoring a backup and "Delete all data" also remain possible; the app records both in a
   history that appears in the detailed PDF. A restored backup also determines whether and since when the lock
   applies – if it is off there, it is lifted; the restore is recorded in the history. This history is kept on the
   device and is lost when you clear the app data or uninstall the app. Without the edit lock, completed trips remain
   editable; the changes are logged. A hash chain detects accidental or simple changes to backup files when
   restoring.
3. **Seals:** when a trip is ended, accepted, edited or cancelled, the app stores a SHA-256 fingerprint of the trip
   data. Without a signature a seal only detects accidental or simple changes, as the calculation is public.
4. **Signatures:** seals and PDFs signed with your key prove that they were made with this key and not changed
   afterwards. They prove neither the time nor who used the key: anyone with the key file and password can sign with
   it. They are not qualified electronic signatures under the eIDAS Regulation.
5. **Timestamps:** prove that a seal already existed at the stated time – this only carries weight with an
   independent service; a service you run yourself proves nothing to third parties. Seals created before timestamps
   were switched on receive a later timestamp. A timestamp does not prove that the details are correct.
6. **Not recorded or proven** are in particular: whether all trips were recorded; whether the device clock was right
   (all times except those in timestamps come from it); changes to vehicle, category and location data; GPS distances
   (they are not sealed); what a restored backup replaced compared with the previous state (only the restore itself
   is recorded). The edit lock, hash chain and unsigned seals do not protect against deliberate manipulation, e.g.
   with root access, a modified app version (the source code is public) or recomputed seals and chains without a
   signature.
7. None of these features is certified or attested by any authority, auditor or certification body.
8. You are responsible for the accuracy and completeness of your entries and for keeping your records. If in doubt,
   consult a tax adviser.
9. Automatically detected trips, GPS distances and addresses may be inaccurate. Check them before accepting them.

## 5. Your obligations

1. Do not operate the app while driving a vehicle. Automatic trip detection works without any interaction.
2. Back up your data regularly using the backup function. Keep exports and backups safe: they are not encrypted.
   Uninstalling the app or clearing its data deletes the data on the device.
3. If you record personal data of other people (e.g. names of business partners), you are responsible for it.
4. Use the address search only to a normal extent. The
   [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) prohibits bulk queries. If you
   switch on timestamps, the terms of the selected service also apply.
5. Keep an exported key file and its password safe and separate from each other. Without both, your key cannot be
   restored after a change of device; anyone with both can sign with your key.

## 6. Liability

The app is provided free of charge and without warranty. We are not liable for damage arising from its use, in
particular not for data loss – regardless of whether and how you backed up your data. This only does not apply where
the law makes liability mandatory: for intent and gross negligence, for fraudulently concealed defects, for injury to
life, body or health, under the German Product Liability Act and under other mandatory provisions (e.g. Art. 82
GDPR).

## 7. Changes to these terms

If we change these terms, for example for new features or changes in the law, the new version applies to future
agreements, for example when you download the app anew. For existing users it only applies if they agree to it. The
current version is always available at <https://garske-systems.github.io/FOSStenbuch/nutzungsbedingungen.html>. Earlier versions are in the
[version history](https://github.com/GARSKE-SYSTEMS/FOSStenbuch/commits/main/docs/nutzungsbedingungen.md).

## 8. Final provisions

1. German law applies, excluding the UN Convention on Contracts for the International Sale of Goods. Consumers keep
   the protection of the mandatory laws of the country where they habitually reside.
2. We are neither willing nor obliged to take part in dispute resolution proceedings before a consumer arbitration
   board.
