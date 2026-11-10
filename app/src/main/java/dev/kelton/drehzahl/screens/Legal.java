package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Terms and Credits text. Edit the strings below to change what the pages say. */
final class Legal {
    private Legal() { }

    private static final String[][] TERMS = {
        { "What Drehzahl is",
          "Drehzahl is a free, open source head unit app for an Android tablet mounted in a car. It shows media, sound settings, "
          + "vehicle readings and shortcuts to other apps. It is a hobby project published under the MIT License. By installing or "
          + "using it you agree to these terms." },
        { "Driving comes first",
          "Do not set up, adjust or read detailed screens while the car is moving. Set things up while parked. You are responsible "
          + "for following the laws where you drive, including rules about screens and phone use, and for mounting the tablet so it "
          + "does not block your view, airbags or controls." },
        { "Readings are for information only",
          "Gauges, g-forces, trip data and service reminders can be wrong, late or simulated (the Demo drive source is entirely made "
          + "up). Never rely on them for safety, maintenance or legal decisions. Use the car's own instruments, warning lights and "
          + "service records." },
        { "Tablet changes",
          "The PC toolkit can remove preinstalled apps and change tablet settings over ADB. Removals can be undone with the toolkit's "
          + "Undo option or a factory reset. You make these changes at your own risk." },
        { "Root features",
          "On a rooted tablet Drehzahl can add a small module (\"Drehzahl Bluetooth receiver\") that changes how Android's Bluetooth "
          + "works so phones can play music to the tablet. It only runs when you switch it on and confirm the root prompt, and it can "
          + "be switched off on the same page. On Android 12 and older it stops the tablet from sending sound to Bluetooth headphones "
          + "while it is on. With \"Pause Wi-Fi search during wireless CarPlay\" on, Drehzahl also switches off Android's automatic "
          + "joining of Wi-Fi networks while a phone is connected over Wi-Fi Direct, and switches it back on when the phone leaves; "
          + "in that time the tablet does not join known networks by itself. With \"Dock mode\" on, a second module (\"Drehzahl dock mode\") "
          + "asks the tablet's USB-C port to take power only and switches the tablet's USB to host whenever power arrives. The tablet "
          + "can still end up powering a dock that has lost power, which drains its battery. Root access bypasses Android's normal protections; "
          + "you use it at your own risk." },
        { "Other apps and services",
          "CarPlay runs in CarLink, a separate app with its own license (AGPL-3.0) and its own terms. Drehzahl only opens it. Music, "
          + "maps and other apps you launch from Drehzahl are covered by their own terms." },
        { "Your data",
          "Drehzahl does not ask for internet access and sends nothing anywhere. Settings, trip memories and service data stay on the "
          + "tablet. Media information is read from the tablet to show what is playing and is not stored." },
        { "Names and trademarks",
          "Drehzahl is not made by, endorsed by or connected to Volkswagen AG, Apple Inc., Google LLC, Walmart Inc., Lenovo or any car "
          + "or tablet maker. Volkswagen, Jetta, GLI, CarPlay, iPhone, Android, onn and Lenovo are trademarks of their owners and are used only "
          + "to describe compatibility." },
        { "No warranty",
          "Drehzahl is provided as is, without warranty of any kind. The authors are not liable for any damage, loss, fines or injury "
          + "arising from its use, as set out in the MIT License included with the source code." },
    };

    static void terms(Context c, LinearLayout p) {
        for (String[] s : TERMS) {
            p.addView(W.header(c, s[0]));
            p.addView(body(c, s[1]));
        }
        p.addView(W.note(c, "Last updated November 5, 2026."));
    }

    private static final String[][] CREDITS = {
        { "Created by", "Kelton Chelbian" },
        { "Software used",
          "Android SDK and Android Open Source Project (Apache License 2.0)\n"
          + "Roboto font, built into Android, used by the Classic theme (Apache License 2.0)\n"
          + "Android Debug Bridge (ADB), Android platform-tools, used by the PC toolkit\n"
          + "Windows PowerShell, used by the PC toolkit" },
        { "CarPlay engine (separate app)",
          "CarLink by F-e-n-y-x, github.com/F-e-n-y-x/CarLink (AGPL-3.0)\n"
          + "Built on DiPlay by shihabal3amri (GPL-3.0), xcertplay (GPL-3.0) and DiAuto (AGPL-3.0)" },
        { "Fonts (SIL Open Font License 1.1)",
          "Saira by Omnibus-Type, used by the GLI theme\n"
          + "Rajdhani by Indian Type Foundry, used by the Carbon theme\n"
          + "Inter by Rasmus Andersson and the Inter Project Authors, used by the Simple theme\n"
          + "JetBrains Mono by JetBrains, used by the Code theme\n"
          + "Font files and license texts come from the Fontsource packages; the licenses are in the fonts folder of the source code" },
        { "Optional add-ons",
          "OnePro (Dark) icon pack by Eatos, sold separately on Google Play\n"
          + "Root features (Bluetooth receiver, Wi-Fi search pause, dock mode) work with the root tools Magisk, KernelSU and APatch (installed separately)\n"
          + "mtkclient by Bjoern Kerler (GPL-3.0), used separately to back up and root the onn tablet (see docs/ROOTING.md)" },
        { "References",
          "Android audio effects documentation (DynamicsProcessing, Equalizer, BassBoost, Virtualizer, LoudnessEnhancer, PresetReverb)\n"
          + "Icon pack appfilter.xml format used by third-party Android launchers\n"
          + "Android Bluetooth source (AOSP): the A2DP sink and AVRCP controller roles and the settings that switch them on\n"
          + "Magisk module format (module.prop, system.prop, system folder overlays)\n"
          + "CarLink's own audio statistics and notes on Wi-Fi gaps, used to find the CarPlay audio dropouts\n"
          + "DiPlay's notes on head units searching for Wi-Fi networks during Wi-Fi Direct CarPlay, which pointed to the network search\n"
          + "Android Wi-Fi source (AOSP): auto-join and the periodic connectivity scan\n"
          + "ELM327 datasheet (Elm Electronics): commands, timeouts and CAN header settings\n"
          + "SAE J1979 / ISO 15765-4: OBD2 readings, several readings per request on CAN, the misfire monitor results (mode 06)\n"
          + "Published Bluetooth LE service layouts of common OBD2 adapters (Vgate, Veepeak, HM-10 style modules)" },
        { "License", "Drehzahl is released under the MIT License. The full text is in the LICENSE file of the source code." },
    };

    static void credits(Context c, LinearLayout p) {
        for (String[] s : CREDITS) {
            p.addView(W.header(c, s[0]));
            p.addView(body(c, s[1]));
        }
    }

    private static TextView body(Context c, String s) {
        TextView t = W.text(c, s, 17, Theme.TEXT);
        t.setLineSpacing(0, 1.35f);
        t.setPadding(Theme.dp(4), 0, Theme.dp(4), Theme.dp(6));
        return t;
    }
}
