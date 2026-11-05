# Drehzahl

Head unit app for an Android tablet mounted in a car. Built for a 2015 Jetta GLI with an onn 10.1 tablet (Android 15),
but nothing in it is tied to one car or one tablet.

Drehzahl replaces the tablet's home screen with a car layout: a side rail for Home, Media, CarPlay, Car, Sound, Apps and
Settings, an equalizer that works on other apps' audio, live gauges, a g-meter that uses the tablet's own accelerometer,
a lap timer, trip memories and service reminders.

The visuals are original. Menus follow the way a factory infotainment system is organised, but no manufacturer artwork,
fonts or logos are used.

## What works today

| Area | Details |
|---|---|
| Home | Clock, now playing (any app with a media session), CarPlay tile, three live gauges |
| Media | Album art, progress, controls, quick launch for installed music apps |
| CarPlay | Opens [CarLink](https://github.com/F-e-n-y-x/CarLink) (separate app, AGPL-3.0, DiPlay engine). Opens by itself when an iPhone is plugged in |
| Sound | 10-band EQ with presets, treble/mid/bass, balance and fader pad, sound focus, bass boost, surround, loudness, 3-band compressor, limiter, reverb, max switch-on volume, speed-dependent volume |
| Car | 6 configurable gauges (13 readings), g-meter with peaks and mount calibration, lap timer, three trip memories, oil service reminder, driving profiles, data source picker |
| Settings | Day/night/auto (light sensor), accent color, brightness, hide system bars, keep screen on, time and date, units, icon pack, connections, tablet info, terms, credits, reset |
| Apps | App drawer with icon pack support (any pack using the standard appfilter.xml, such as OnePro Dark) |

## Not built yet

- Bluetooth OBD2 (ELM327) data source. The Car screens run on a simulated drive or tablet sensors until then.
- Tuner link data source.
- Fader and front focus output. They need a 4-channel USB audio interface; the settings are saved already.

## Repository layout

```
app/src/main/java/dev/kelton/drehzahl/
  MainActivity.java      side rail, top bar, screen stack, day/night
  Prefs.java             every saved setting
  ui/                    theme, icons, controls, gauge, g-meter, EQ and balance views
  screens/               Home, Media, CarPlay, Car, Sound, Apps, Settings, terms and credits text
  audio/                 SoundEngine (Android audio effects), presets, session watcher
  vehicle/               data hub, demo drive, tablet sensors, metrics, trip computer, lap timer
  media/                 now playing (MediaSession)
  core/                  background service, boot and audio session receivers
  icons/                 icon pack loader
  projection/            CarLink launcher
toolkit/                 Windows PowerShell toolkit: pair, report, debloat, install, undo
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

## License

MIT, see [LICENSE](LICENSE). CarLink, DiPlay and the icon pack are separate projects with their own licenses.
Volkswagen, Jetta, GLI, CarPlay, iPhone, Android and onn are trademarks of their owners and are named only to describe
compatibility.
