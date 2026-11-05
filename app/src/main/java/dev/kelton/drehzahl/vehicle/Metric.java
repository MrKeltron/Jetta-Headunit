package dev.kelton.drehzahl.vehicle;

/** Everything a gauge can show. To add a reading: add one entry to ALL. */
public abstract class Metric {
    public final String id, label;

    Metric(String id, String label) { this.id = id; this.label = label; }

    public abstract float value(VehicleData d);
    public abstract String unit();
    public abstract float min();
    public abstract float max();
    public float warn() { return Float.NaN; }
    public int decimals() { return 0; }

    public static final Metric[] ALL = {
        new Metric("boost", "Boost") {
            public float value(VehicleData d) { return Units.pressure(d.boostKpa); }
            public String unit() { return Units.pressureUnit(); }
            public float min() { return Units.pressure(-80f); }
            public float max() { return Units.pressure(180f); }
            public int decimals() { return Units.pressureDecimals(); }
        },
        new Metric("oil", "Oil temp") {
            public float value(VehicleData d) { return Units.temp(d.oilC); }
            public String unit() { return Units.tempUnit(); }
            public float min() { return Units.temp(40f); }
            public float max() { return Units.temp(150f); }
            public float warn() { return Units.temp(125f); }
        },
        new Metric("coolant", "Coolant") {
            public float value(VehicleData d) { return Units.temp(d.coolantC); }
            public String unit() { return Units.tempUnit(); }
            public float min() { return Units.temp(40f); }
            public float max() { return Units.temp(130f); }
            public float warn() { return Units.temp(108f); }
        },
        new Metric("iat", "Intake air") {
            public float value(VehicleData d) { return Units.temp(d.iatC); }
            public String unit() { return Units.tempUnit(); }
            public float min() { return Units.temp(-10f); }
            public float max() { return Units.temp(80f); }
            public float warn() { return Units.temp(60f); }
        },
        new Metric("power", "Power") {
            public float value(VehicleData d) { return Units.power(d.powerKw); }
            public String unit() { return Units.powerUnit(); }
            public float min() { return 0f; }
            public float max() { return Units.power(240f); }
        },
        new Metric("torque", "Torque") {
            public float value(VehicleData d) { return Units.torque(d.torqueNm); }
            public String unit() { return Units.torqueUnit(); }
            public float min() { return 0f; }
            public float max() { return Units.torque(450f); }
        },
        new Metric("speed", "Speed") {
            public float value(VehicleData d) { return Units.speed(d.speedKmh); }
            public String unit() { return Units.speedUnit(); }
            public float min() { return 0f; }
            public float max() { return Units.speed(260f); }
        },
        new Metric("rpm", "Engine speed") {
            public float value(VehicleData d) { return d.rpm; }
            public String unit() { return "rpm"; }
            public float min() { return 0f; }
            public float max() { return 7000f; }
            public float warn() { return 6500f; }
        },
        new Metric("throttle", "Throttle") {
            public float value(VehicleData d) { return d.throttle * 100f; }
            public String unit() { return "%"; }
            public float min() { return 0f; }
            public float max() { return 100f; }
        },
        new Metric("load", "Engine load") {
            public float value(VehicleData d) { return d.load * 100f; }
            public String unit() { return "%"; }
            public float min() { return 0f; }
            public float max() { return 100f; }
        },
        new Metric("battery", "Battery") {
            public float value(VehicleData d) { return d.batteryV; }
            public String unit() { return "V"; }
            public float min() { return 10f; }
            public float max() { return 16f; }
            public int decimals() { return 1; }
        },
        new Metric("ambient", "Outside temp") {
            public float value(VehicleData d) { return Units.temp(d.ambientC); }
            public String unit() { return Units.tempUnit(); }
            public float min() { return Units.temp(-20f); }
            public float max() { return Units.temp(45f); }
        },
        new Metric("maf", "Airflow") {
            public float value(VehicleData d) { return d.mafGs; }
            public String unit() { return "g/s"; }
            public float min() { return 0f; }
            public float max() { return 250f; }
        },
        new Metric("latg", "Lateral g") {
            public float value(VehicleData d) { return Math.abs(d.latG); }
            public String unit() { return "g"; }
            public float min() { return 0f; }
            public float max() { return 1.5f; }
            public int decimals() { return 2; }
        },
        new Metric("long", "Accel / brake g") {
            public float value(VehicleData d) { return d.lonG; }
            public String unit() { return "g"; }
            public float min() { return -1.5f; }
            public float max() { return 1.5f; }
            public int decimals() { return 2; }
        },
    };

    public static Metric byId(String id) {
        for (Metric m : ALL) if (m.id.equals(id)) return m;
        return ALL[0];
    }

    public static String[] labels() {
        String[] s = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) s[i] = ALL[i].label;
        return s;
    }
}
