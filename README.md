# Eichhorn

Merkt sich Videos **mit Zeitpunkt** und Textstellen **mit Markierung** – auf Android, Mac und Linux.
Die App zeigt alles mit Vorschaubild; gibt es keins (mehr), steht der Link da. Antippen öffnet das
Video genau an der Stelle bzw. scrollt die Seite zur gemerkten Textstelle und markiert sie
(Text Fragments, `#:~:text=…`).

## Android

Merken geht auf drei Wegen:

| Wie | Was gespeichert wird |
|---|---|
| **Kachel „Merken“** in den Schnelleinstellungen (runterziehen, neben WLAN/Bildschirmaufnahme) | laufendes Video mit Zeitpunkt (YouTube-App, Browser, andere Player); im Browser zusätzlich der Link |
| **Text markieren › „Merken“** | die Textstelle + die Seite, auf der sie steht |
| **Teilen › „Merken“** aus jeder App | der Link; spielt die teilende App gerade das Video, auch der Zeitpunkt |
| **Lautstärketasten gedrückt halten** (optional, Kurzbefehl der Bedienungshilfe) | wie die Kachel |

Einrichtung (die App führt durch):
1. **Benachrichtigungszugriff** – damit liest Eichhorn Titel und Position des laufenden Videos.
2. **Bedienungshilfe „Eichhorn – Merken“** – liest die Adresse der offenen Seite im Browser.
   In den Einstellungen dieser Bedienungshilfe kann man den Kurzbefehl auf „Lautstärketasten gedrückt halten“ legen.
3. **Kachel** hinzufügen.

Android 13+: Bei selbst installierten APKs sind 1 und 2 erst nach *App-Info › ⋮ › Eingeschränkte Einstellungen zulassen* möglich.

Die Power-Tastenkombi selbst kann keine App abfangen; die Lautstärketasten-Kombi ist der Ersatz.

Aus der YouTube-App liefert Android nur Titel und Position, nicht den Link. Eichhorn sucht das Video
dann über den Titel auf YouTube. Exakt ist „Teilen › Merken“, dann wird der Zeitpunkt aus der laufenden Wiedergabe ergänzt.

## Mac / Linux

Die Desktop-App zeigt die Einträge und nimmt auf `127.0.0.1:47123` (nur lokal) Einträge der
Browser-Erweiterung entgegen. Ist die App zu, wartet die Erweiterung und liefert nach, sobald sie wieder läuft
(Zahl am Symbol = wartende Einträge).

### Browser-Erweiterung (`browser-extension/`)

**Alt+Umschalt+B** (Mac: ⌥⇧B), Klick aufs Symbol oder Rechtsklick › „In Eichhorn merken“.
Gespeichert werden URL, Video-Zeitpunkt, markierter Text und Vorschaubild.

- **Chrome / Brave / Edge:** `chrome://extensions` › Entwicklermodus › „Entpackte Erweiterung laden“ › Ordner `browser-extension`.
  Kürzel ändern / global machen: `chrome://extensions/shortcuts` (global geht dort nur mit Strg+Umschalt+Ziffer).
- **Firefox:** `about:debugging` › „Dieser Firefox“ › „Temporäres Add-on laden“ › `browser-extension/manifest.json`
  (bleibt bis zum Neustart). Dauerhaft braucht Firefox eine signierte Version (`web-ext sign`, kostenloser AMO-Account)
  oder Firefox Developer Edition/ESR mit `xpinstall.signatures.required = false`.

## Export / Import

⋮ › „Als JSON exportieren“ / „JSON importieren“ – gleiches Format auf allen Geräten, Import ergänzt nur
fehlende Einträge. Lokale Vorschaubilder vom Handy gibt es auf dem Rechner nicht; dort steht dann der Link
(YouTube-Vorschaubilder kommen überall neu von YouTube).

Daten: Android im App-Speicher, Mac `~/Library/Application Support/Eichhorn/`, Linux `~/.local/share/eichhorn/`.

## Bauen

```sh
./gradlew :shared:desktopTest                 # Tests
./gradlew :androidApp:assembleRelease         # APK, signiert mit ~/.android/eichhorn-release.jks (+ .pass)
./gradlew :desktopApp:packageDmg              # Mac
./gradlew :desktopApp:packageDeb              # Linux (nur auf Linux; macht auch die GitHub Action)
./gradlew :desktopApp:run                     # Desktop-App direkt starten
tools/make_icons.sh                           # Icons neu zeichnen
```

Aufbau: `shared` (KMP: Modell, Speicher, Link-Logik, gesamte Compose-Oberfläche), `androidApp`
(Kachel, Medien-Sitzungen, Bedienungshilfe, Teilen-Ziel), `desktopApp` (Fenster + lokaler Empfänger),
`browser-extension` (Manifest V3 für Chrome und Firefox).
