DREHZAHL TABLET TOOLKIT
=======================

What this folder does: talks to your onn tablet over Wi-Fi (ADB) to pair it,
debloat it, install the Drehzahl head unit app and CarLink (CarPlay), and save
reports you can share when asking for help.

BEFORE YOU START (on the tablet, one time)
  1. Settings > About tablet > tap "Build number" 7 times (turns on Developer options)
  2. Settings > System > Developer options > turn on "Wireless debugging"
  3. Tablet and PC must be on the same Wi-Fi

HOW TO USE
  Double click "START HERE.bat", then pick a number:
    1  Connect       first time it walks you through pairing with a 6-digit code
    2  Report        saves reports\device-report.txt specs and app list
    3  Debloat       shows the full list first, nothing is removed until you say Y
    4  Install       Drehzahl, CarLink, permissions, icon pack, car tweaks
    5  Undo          puts removed apps back
    6  Screenshot    saves what the tablet screen shows into reports\
    7  Log           saves the tablet log into reports\ after a crash

GOOD ORDER THE FIRST TIME
  1 -> 2 -> 3 -> 4

FILES
  toolkit\debloat-list.txt   what gets removed or kept (edit it in Notepad)
  toolkit\settings.ini       tablet address, GitHub repo, package names
  apk\                       APK files waiting to be installed
  reports\                   everything the toolkit saves, including toolkit-log.txt

IF SOMETHING GOES WRONG
  - "Could not connect": turn Wireless debugging off and on, then pick 1 again.
    The port changes every time, the toolkit finds the new one by itself.
  - Removed something you needed: pick 5 (Undo).
  - Anything else: share reports\toolkit-log.txt
