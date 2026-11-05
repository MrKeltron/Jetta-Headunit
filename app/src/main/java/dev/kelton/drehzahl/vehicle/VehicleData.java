package dev.kelton.drehzahl.vehicle;

/** One snapshot of everything the car screens can show. Always stored in metric units. */
public final class VehicleData {
    public float speedKmh;
    public float rpm;
    public float boostKpa;      // above atmosphere
    public float oilC;
    public float coolantC;
    public float iatC;
    public float throttle;      // 0..1
    public float load;          // 0..1
    public float batteryV;
    public float torqueNm;
    public float powerKw;
    public float latG;          // + = car accelerating to the right (turning right)
    public float lonG;          // + = accelerating
    public float fuelLph;
    public int gear;
    public boolean live;        // false while showing demo data
    public boolean hasSpeed;
    public boolean hasEngine;
    public boolean hasG;
}
