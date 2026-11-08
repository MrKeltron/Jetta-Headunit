# Rooting the onn 10.1" tablet (model 100135925)

Root lets Drehzahl pause Wi-Fi searching during wireless CarPlay, switch on the Bluetooth receiver, and later fix
charging through a USB-C dock. This is how the project's tablet was rooted on Nov 8, 2026. It is written for this one
model and build; other tablets differ.

**Unlocking the bootloader erases everything on the tablet.** Save what you want to keep first (Drehzahl:
Settings > Reset > Save settings to the file, then copy `Android/data/dev.kelton.drehzahl/files/settings.txt` off the
tablet).

## What was used

| | |
|---|---|
| Tablet | onn 10.1" 100135925, Android 15, MediaTek MT8786 (mt6768), build TTA.31, boots slot `_b` |
| PC | Ubuntu 26.04 on a laptop, native (not WSL). The same steps on Windows failed: the USB driver could not hold the tablet's boot mode |
| Tools | [mtkclient](https://github.com/bkerler/mtkclient) (GPL-3.0), [Magisk](https://github.com/topjohnwu/Magisk) 31.1, adb |

## Steps

1. Install the tools (one time):

   ```bash
   sudo apt update && sudo apt install -y git python3-pip python3-venv libusb-1.0-0 android-tools-adb
   cd ~/Desktop && git clone https://github.com/bkerler/mtkclient && cd mtkclient
   python3 -m venv .venv && .venv/bin/pip install .
   sudo cp Setup/Linux/*.rules /etc/udev/rules.d/
   sudo udevadm control --reload-rules && sudo udevadm trigger
   ```

2. **Boot mode (BROM)**, needed for every `mtk` command: start the command first. Then unplug the tablet, hold Power
   about 10 s until it is fully off, hold **both volume buttons**, and plug the cable in while holding them. Let go when
   the terminal says the device was detected. The screen stays black.

3. **Back up every partition except your data** (about 7.3 GB, 10 minutes). Keep this folder; it can restore the tablet:

   ```bash
   mkdir -p ~/Desktop/onn-backup && cd ~/Desktop/onn-backup
   ~/Desktop/mtkclient/.venv/bin/mtk rl . --skip userdata
   sha256sum *.bin > hashes.txt
   ~/Desktop/mtkclient/.venv/bin/mtk reset
   ```

   It should end with "All Dumped partitions success" and leave about 50 `.bin` files, including `init_boot_b.bin`.

4. **Let Magisk patch the boot image on the tablet itself.** Boot the tablet, turn on USB debugging, then:

   ```bash
   adb install Magisk-v31.1.apk
   adb push ~/Desktop/onn-backup/init_boot_b.bin /sdcard/Download/init_boot_b.img
   ```

   On the tablet: Magisk > Install > Select and Patch a File > Download > `init_boot_b.img` > Let's Go. Then copy the
   result back and check it is the same size as the original (8388608 bytes):

   ```bash
   adb pull "$(adb shell ls /sdcard/Download/magisk_patched*.img | tr -d '\r')" ~/Desktop/init_boot_b.magisk.img
   stat -c%s ~/Desktop/init_boot_b.magisk.img
   ```

5. **Unlock and flash** (this is the step that erases the tablet). Each command needs the BROM button combination:

   ```bash
   ~/Desktop/mtkclient/.venv/bin/mtk da seccfg unlock
   ~/Desktop/mtkclient/.venv/bin/mtk w init_boot_b ~/Desktop/init_boot_b.magisk.img
   ~/Desktop/mtkclient/.venv/bin/mtk reset
   ```

   Unplug and press Power. An orange "bootloader is unlocked" screen shows for a few seconds; that is normal. If it
   stops on a "device is corrupt" screen, press Power once. The tablet erases itself and starts the setup wizard.

6. After setup, open Magisk once; it restarts the tablet to finish its own setup. To use root from the PC toolkit, allow
   "Shell" in Magisk > Superuser. The toolkit's `scripts/grant-root.sh` pre-approves Drehzahl without a prompt.

## Undo

Write the stock image back (BROM again): `mtk w init_boot_b ~/Desktop/onn-backup/init_boot_b.bin`. Root is gone and
nothing else changes. Relocking the bootloader (`mtk da seccfg lock`) erases the tablet again.
