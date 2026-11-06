package dev.kelton.drehzahl.obd;

import java.util.Objects;

public class SchedulerTest {
    static int fails = 0;
    static void eq(String name, Object got, Object want) {
        boolean ok = Objects.equals(String.valueOf(got), String.valueOf(want));
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + (ok ? "" : "  want=" + want));
    }

    public static void main(String[] a) {
        boolean[] sup = new boolean[256];
        sup[0x0B] = true; sup[0x0C] = true; sup[0x0D] = true;
        PidScheduler s = new PidScheduler();
        s.reset(sup, 0x0C, 0x0D);
        eq("boost due at start", s.due(0x0B, 0), true);
        eq("unsupported not due", s.due(0x5C, 0), false);
        long rest = 0;
        for (int i = 0; i < PidScheduler.MISS_LIMIT - 1; i++) rest = s.miss(0x0B, 100 * i);
        eq("six misses keep asking", rest + " " + s.due(0x0B, 600), "0 true");
        rest = s.miss(0x0B, 700);
        eq("seventh miss rests 2 s", rest, 2000);
        eq("resting during the rest", s.due(0x0B, 2000) + " " + s.resting(0x0B, 2000), "false true");
        eq("asked again after the rest", s.due(0x0B, 2700), true);
        for (int i = 0; i < PidScheduler.MISS_LIMIT; i++) rest = s.miss(0x0B, 2700 + i);
        eq("second quiet spell rests 4 s", rest, 4000);
        for (int r = 0; r < 4; r++) for (int i = 0; i < PidScheduler.MISS_LIMIT; i++) rest = s.miss(0x0B, 10000);
        eq("rest stops growing at 16 s", rest, 16000);
        eq("still supported, never dropped", s.has(0x0B), true);
        eq("comes back", s.hit(0x0B), true);
        eq("due right after coming back", s.due(0x0B, 10001), true);
        for (int i = 0; i < PidScheduler.MISS_LIMIT; i++) rest = s.miss(0x0B, 20000);
        eq("rest starts over at 2 s", rest, 2000);
        for (int i = 0; i < 50; i++) rest = s.miss(0x0C, 0);
        eq("rpm never rests", rest + " " + s.due(0x0C, 1), "0 true");
        eq("plain hit is not a comeback", s.hit(0x0D), false);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
    }
}
