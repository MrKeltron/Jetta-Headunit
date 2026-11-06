# Drehzahl

Head unit app for an Android tablet mounted in a car. Built for a 2015 Jetta GLI with an onn 10.1 tablet (Android 15),
but nothing in it is tied to one car or one tablet.

Drehzahl replaces the tablet's home screen with a head unit layout: a home screen of buttons (Media, CarPlay, Navigation,
Performance, Car, Sound, Widgets, Apps, Settings, Android settings), each opening its own app with tab buttons. It has an
equalizer that works on other apps' audio, live gauges, a g-meter that uses the tablet's own accelerometer, a lap timer,
trip memories, service reminders and a startup animation.

## Themes

Pick one in Settings > Display. Each has its own colors, font, buttons, background and startup animation.

| Theme | Look | Font |
|---|---|---|
| GLI | Red on black, hex pattern, square buttons. Startup: hex wave, tach sweep to redline, red stripe | Saira |
| Carbon | Carbon weave, round metal buttons, amber accents | Rajdhani |
| Simple | Black and white, big text, fewer details | Inter |
| Code | Terminal home with live telemetry, event log and a boost graph | JetBrains Mono |
| Classic | The first layout: side rail and gauges on Home | Roboto (system) |

Scripts can jump straight to a theme or screen:
`adb shell am start -n dev.kelton.drehzahl/.MainActivity --ei style 0 --ei open 1 --ei page 3`
(style 0 to 4 as in the table; source 0 demo, 1 tablet sensors, 2 OBD2; open 0 Home, 1 Media, 2 CarPlay, 3 Car, 4 Sound, 5 Apps, 6 Settings, 7 Performance,
8 Widgets; `--ez intro true` plays the startup animation).

The visuals are original. Menus follow the way a factory infotainment system is organised, but no manufacturer artwork,
fonts or logos are used.

## What works today

| Area | Details |
|---|---|
| Home | Clock, battery, now playing, three live readouts and ten app buttons |
| Media | Now playing with seek and 10 s / 30 s skips, up next queue, sources, output (system output switcher, volume, quick EQ, balance), phone over Bluetooth |
| Phone over Bluetooth | Lets any phone play music to the tablet like a car stereo (A2DP sink with track info and controls over AVRCP). Android ships tablets with this switched off; on a rooted tablet Drehzahl adds a small module that switches it on (system properties on Android 13 and newer, a resource overlay on Android 10 to 12). Without root the page explains why it cannot work |
| Widgets | Clock, now playing, engine gauges, g-meter, trip, tablet and CarPlay status on one screen |
| CarPlay | Opens [CarLink](https://github.com/F-e-n-y-x/CarLink) (separate app, AGPL-3.0, DiPlay engine). Opens by itself when an iPhone is plugged in |
| Sound | 10-band EQ with presets, treble/mid/bass, balance and fader pad, sound focus, bass boost, surround, loudness, 3-band compressor, limiter, reverb, max switch-on volume, speed-dependent volume |
| Car | 6 configurable gauges (15 readings), g-meter with peaks and mount calibration, lap timer, three trip memories, oil service reminder, driving profiles, data source picker |
| OBD2 | ELM327 adapters by USB cable (own FTDI, CH340, CP210x and CDC-ACM drivers), classic Bluetooth, or Bluetooth LE (no pairing; finds the adapter's data channel by itself). Finds which readings the car answers, asks the engine computer directly on CAN cars, polls continuously, rests and retries a reading the car stops answering (its gauge shows -- meanwhile), reads and clears trouble codes per module, adapter console, log file |
| Screens | Drawn for 1280 x 800 dp. Smaller tablets (for example 800 x 1280 at 240 dpi) shrink the whole layout to fit instead of cutting it off |
| Settings | Day/night/auto (light sensor), accent color, brightness, hide system bars, keep screen on, time and date, units, icon pack, connections, tablet info, terms, credits, reset |
| Apps | App drawer with icon pack support (any pack using the standard appfilter.xml, such as OnePro Dark) |

## Not built yet

- Manufacturer-specific readings (oil temperature on cars that do not report it over generic OBD2, actual vs requested boost).
- Tuner link data source.
- Fader and front focus output. They need a 4-channel USB audio interface; the settings are saved already.

## CarPlay audio dropouts

Wireless CarPlay sends music over the same Wi-Fi link as the screen. On the onn tablet, CarLink's own statistics showed
the audio arriving in bursts with gaps of about 400 ms, while CarLink's default music buffer is 300 ms. Every gap a little
longer than usual empties the buffer and the music cuts out for a moment. Drehzahl's sound effects are not the cause: the
mixer reports no underruns and the effects use about a fifth of the time available per audio block.

Fix, in CarLink: Settings > Show advanced (on) > Sound & voice > Music buffer > Long (1 s), and Buffered music on.
Then disconnect and reconnect CarPlay. The PC toolkit can do this for you (see `toolkit/steps.txt` examples).

If the tablet also joins a Wi-Fi network in the car (a phone hotspot, for example), keep that network on the same channel
as CarPlay or switch Wi-Fi client off; a single Wi-Fi radio has to leave the CarPlay channel for every scan or other channel.

## Repository layout

```
app/src/main/java/dev/kelton/drehzahl/
  MainActivity.java      side rail, top bar, screen stack, day/night
  Prefs.java             every saved setting
  ui/                    themes, backdrops, home buttons, startup animations, icons, controls, gauges, EQ, g-meter
  screens/               Home (grid and classic), Media, CarPlay, Car, Sound, Widgets, Apps, Settings, terms and credits
  audio/                 SoundEngine (Android audio effects), presets, session watcher
  vehicle/               data hub, demo drive, tablet sensors, metrics, trip computer, lap timer
  obd/                   USB serial drivers, Bluetooth link, ELM327 protocol, OBD2 polling, reply parser
  media/                 now playing (MediaSession), Bluetooth receiver
  sys/                   root shell helper
  core/                  background service, boot and audio session receivers
  icons/                 icon pack loader
  projection/            CarLink launcher
overlay/btsink/          resource overlay for the Bluetooth app on Android 10 to 12 (packed into the APK by build.sh)
toolkit/                 Windows PowerShell toolkit: pair, report, debloat, install, undo, screenshot tour,
                         step runner (Run steps.bat) for scripted changes on one or more tablets
tools/make_icon.py       draws the launcher icon (Python standard library only)
build.sh                 builds the APK without Gradle
```

## Installing on the tablet

1. On the tablet: Settings > About tablet, tap Build number 7 times. Then Settings > System > Developer options >
   Wireless debugging on.
2. Copy the `toolkit` folder to a Windows PC on the same Wi-Fi. Put `START HERE.bat` next to it.
3. Run `START HERE.bat`: 1 Connect, 2 Report, 3 Debloat (optional), 4 Install.

Option 4 installs Drehzahl and CarLink, grants the permissions only ADB can grant (media info, audio session
detection, system brightness, keep screen on while charging) and can make Drehzahl the home app.

## Building

No Gradle and no third-party libraries. You need a JDK (17 or newer) and the Android SDK command line build tools.

```
ANDROID_HOME=/path/to/android-sdk ./build.sh
```

The APK lands in `build/out/`. GitHub Actions runs the same script on every push to `main`.

Signing: without a key the build uses a throwaway one, which is fine for testing but cannot update an installed copy.
To sign real builds, put `KEYSTORE`, `KEYSTORE_PASS` and `KEY_ALIAS` in `signing/keystore.properties` (ignored by git).
For GitHub builds, add two repository secrets: `KEYSTORE_B64` (the .jks file in base64) and `KEYSTORE_PASS`. With those
set, every push also publishes a `latest-build` release that the toolkit downloads.

## Fonts

Saira, Rajdhani, Inter and JetBrains Mono are under the SIL Open Font License 1.1. The license texts are in `fonts/`.
The font files in `app/src/main/assets/fonts` are Latin subsets from the Fontsource packages, converted to TTF.

## License

MIT, see [LICENSE](LICENSE). CarLink, DiPlay and the icon pack are separate projects with their own licenses.
Volkswagen, Jetta, GLI, CarPlay, iPhone, Android and onn are trademarks of their owners and are named only to describe
compatibility.
