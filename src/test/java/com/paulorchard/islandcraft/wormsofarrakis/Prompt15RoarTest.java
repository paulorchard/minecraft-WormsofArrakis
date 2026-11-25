package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.HuntConfig;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.Roar;
import com.paulorchard.islandcraft.wormsofarrakis.hunt.SizeTable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Prompt 15 part 4: the distant roar (distances, the volume formula, the origin, the sounds from the crash sheet, no explosion any more). */
class Prompt15RoarTest {
    @Test
    void theDistanceTable() {
        HuntConfig c = new HuntConfig();
        assertEquals(50, Roar.distance(SizeTable.Size.SMALL, c));
        assertEquals(70, Roar.distance(SizeTable.Size.MEDIUM, c));
        assertEquals(90, Roar.distance(SizeTable.Size.LARGE, c));
        assertEquals(110, Roar.distance(SizeTable.Size.HUGE, c));
    }

    @Test
    void theRangeCoversTheDistanceWithTheMargin() {
        HuntConfig c = new HuntConfig();
        for (SizeTable.Size s : SizeTable.Size.values()) {
            double d = Roar.distance(s, c), v = Roar.volume(d, c.roarRangeMargin);
            assertTrue(v >= d * 1.15 / 16 - 1e-9, s.label);
            assertEquals(d * 1.15 / 16, v, 1e-9, "just enough, not more");
            assertTrue(Roar.range(v) >= d * 1.15 - 1e-9, "the range (16 x volume) reaches the trigger player with the margin");
            assertTrue(Roar.loudness(d, v) > 0.1, "and the trigger player hears it: " + Roar.loudness(d, v));
            assertEquals(0, Roar.loudness(Roar.range(v) + 1, v), "beyond the range: silence");
        }
        assertEquals(3.59375, Roar.volume(50, 1.15), 1e-9);
        assertEquals(7.90625, Roar.volume(110, 1.15), 1e-9);
        assertEquals(1.0, Roar.volume(5, 1.15), "never below 1");
        assertTrue(Roar.loudness(10, Roar.volume(110, 1.15)) > Roar.loudness(100, Roar.volume(110, 1.15)), "nearer is louder");
    }

    @Test
    void theOriginLiesAtTheDistanceOnTheBearingAndTheSameSeedGivesTheSameOrigin() {
        for (double b : new double[]{0, 45, 90, 180, 270, 333.3}) for (double d : new double[]{50, 110}) {
            double[] o = Roar.origin(100, -40, b, d);
            assertEquals(d, Math.hypot(o[0] - 100, o[1] + 40), 1e-9);
        }
        double[] n = Roar.origin(0, 0, 0, 50), e = Roar.origin(0, 0, 90, 50);
        assertEquals(-50, n[1], 1e-9, "bearing 0 is north (minus z)");
        assertEquals(50, e[0], 1e-9, "bearing 90 is east (plus x)");
        assertEquals(Roar.bearing(42), Roar.bearing(42));
        assertNotEquals(Roar.bearing(42), Roar.bearing(43));
        double b = Roar.bearing(7);
        assertTrue(b >= 0 && b < 360);
        assertArrayEquals(Roar.origin(5, 6, Roar.bearing(7), 70), Roar.origin(5, 6, Roar.bearing(7), 70));
    }

    @Test
    void theSoundsAreTheRoarRowsOfTheCrashSheet() {
        List<CrashFx.Cue> rows = Roar.rows();
        assertEquals(2, rows.size());
        assertEquals("entity.ravager.roar", rows.get(0).event());
        assertEquals("entity.ender_dragon.growl", rows.get(1).event());
        double v = Roar.volume(90, 1.15);
        List<Roar.Shot> shots = Roar.shots(v, 0.9, 1);
        assertEquals(5, shots.size(), "4 roars and 1 growl");
        assertEquals(0, shots.get(0).t(), 1e-9);
        int ravager = 0, growl = 0;
        for (Roar.Shot s : shots) {
            assertEquals(v, s.volume(), 1e-9, "both layers at the computed volume, not the sheet's 4 and 3.5");
            if (s.sound().equals("entity.ravager.roar")) ravager++; else if (s.sound().equals("entity.ender_dragon.growl")) growl++; else fail(s.sound());
            assertEquals(0.5 * 0.9, s.pitch(), 0.5 * 0.9 * 0.031, "the sheet's pitch 0.5 x RoarFarPitch, within the sheet's jitter 3 %");
        }
        assertEquals(4, ravager);
        assertEquals(1, growl);
        List<Double> times = shots.stream().filter(s -> s.sound().contains("ravager")).map(Roar.Shot::t).toList();
        for (int i = 1; i < times.size(); i++) assertEquals(0.32, times.get(i) - times.get(i - 1), 1e-9, "every 0.32 s");
        double growlT = shots.stream().filter(s -> s.sound().contains("growl")).findFirst().get().t();
        assertEquals(0.1, growlT, 1e-9, "the growl starts 0.1 s after the first roar");
        assertEquals(Roar.shots(v, 0.9, 5), Roar.shots(v, 0.9, 5), "the same seed, the same pitches");
        assertEquals(0.5, Roar.playedPitch(0.45), "the game limits the pitch to 0.5: the far-pitch drop of 0.9 on the sheet's 0.5 is not audible");
    }

    @Test
    void noExplosionAndNoStartPuffAnyMore() throws Exception {
        byte[] b;
        try (var in = Prompt15RoarTest.class.getResourceAsStream("/com/paulorchard/islandcraft/wormsofarrakis/hunt/HuntAdapter.class")) { b = in.readAllBytes(); }
        String s = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(s.contains("entity.generic.explode"), "the start explosion is gone");
        assertTrue(s.contains("entity.warden.step"), "control: the tremor sound is still there");
        assertTrue(s.contains("playRoar"), "the roar is played at the start");
        assertFalse(Roar.shots(4, 0.9, 1).stream().anyMatch(x -> x.sound().contains("explode")));
    }

    @Test
    void theLogLine() {
        String l = Roar.line(10.04, 64, -20.5, 123.4, 90, 6.47, SizeTable.Size.LARGE);
        assertTrue(l.startsWith("hunt: roar origin=10.0,64.0,-20.5 bearing=123 distance=90 volume=6.47"), l);
        assertTrue(l.endsWith("size=large W32"), l);
    }
}
