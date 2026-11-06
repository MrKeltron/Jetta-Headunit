package dev.kelton.drehzahl.core;

import android.app.admin.DeviceAdminReceiver;

/** Device admin with one policy, "lock the screen", so Drehzahl can turn the screen off when the car's power goes away. */
public final class ScreenAdmin extends DeviceAdminReceiver { }
