package dev.kelton.drehzahl.vehicle;

/**
 * Simulated drive so every screen can be tested on the bench.
 * Rough model of a 2.0 turbo with a 6-speed dual clutch: throttle program, gears, boost lag, warm-up.
 */
public final class DemoSource {
    private static final float[] RATIO = { 14.0f, 8.3f, 5.3f, 3.9f, 3.1f, 2.55f };
    private static final float WHEEL_R = 0.309f, MASS = 1450f;

    private float t, v, boostBar, oil = 24f, coolant = 30f, smoothThrottle;
    private int gear = 1;

    private float program(float time) {
        float k = time % 48f;
        if (k < 7) return 0.95f;
        if (k < 15) return 0.22f;
        if (k < 18) return 0f;
        if (k < 22) return -0.55f;
        if (k < 26) return 0f;
        if (k < 34) return 0.6f;
        if (k < 42) return 0.18f;
        return -0.3f;
    }

    public void step(float dt, VehicleData d) {
        t += dt;
        float target = program(t);
        smoothThrottle += (target - smoothThrottle) * Math.min(1f, dt * 4f);
        float thr = Math.max(0f, smoothThrottle);

        float rpm = v / (2f * (float) Math.PI * WHEEL_R) * 60f * RATIO[gear - 1];
        float upshift = thr > 0.7f ? 6300f : 2500f;
        if (rpm > upshift && gear < 6) gear++;
        if (rpm < 1250f && gear > 1) gear--;
        rpm = Math.max(780f, v / (2f * (float) Math.PI * WHEEL_R) * 60f * RATIO[gear - 1]);

        float spool = Math.max(0f, Math.min(1f, (rpm - 1500f) / 1500f));
        float boostTarget = thr * spool * 1.45f;
        boostBar += (boostTarget - boostBar) * Math.min(1f, dt * 2.5f);
        if (thr < 0.05f) boostBar += (-0.55f - boostBar) * Math.min(1f, dt * 3f);

        float fade = rpm > 5500f ? 1f - (rpm - 5500f) / 2500f : 1f;
        float torque = thr > 0.02f ? (90f + 300f * Math.max(0f, boostBar) / 1.45f) * fade * (0.35f + 0.65f * thr) : -25f;
        float force = torque * RATIO[gear - 1] * 0.9f / WHEEL_R;
        if (smoothThrottle < 0) force = smoothThrottle * MASS * 9.81f;
        float drag = 0.5f * 1.2f * 0.3f * 2.2f * v * v + (v > 0.1f ? 180f : 0f);
        float accel = (force - drag) / MASS;
        v = Math.max(0f, v + accel * dt);
        if (v == 0f) accel = 0f;

        oil += ((100f + thr * 10f) - oil) * dt / 140f;
        coolant += (90f - coolant) * dt / 70f;

        float powerKw = Math.max(0f, torque) * rpm / 9549f;
        d.speedKmh = v * 3.6f;
        d.rpm = rpm;
        d.gear = gear;
        d.throttle = thr;
        d.load = Math.min(1f, Math.max(0f, torque / 390f));
        d.boostKpa = boostBar * 100f;
        d.torqueNm = Math.max(0f, torque);
        d.powerKw = powerKw;
        d.oilC = oil;
        d.coolantC = coolant;
        d.iatC = 26f + Math.max(0f, boostBar) * 12f;
        d.batteryV = 14.1f + 0.1f * (float) Math.sin(t * 0.3f);
        d.lonG = accel / 9.81f;
        d.latG = 0.38f * (float) Math.sin(t * 0.45f) * Math.min(1f, v / 15f);
        d.fuelLph = 0.8f + powerKw * 0.27f / 0.745f;
        d.ambientC = 18f;
        d.mafGs = 3f + powerKw / 1.07f;
        d.powerEstimated = false;
        d.hasSpeed = d.hasEngine = d.hasG = true;
    }
}
