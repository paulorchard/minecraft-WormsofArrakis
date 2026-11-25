package com.paulorchard.islandcraft.wormsofarrakis;

import com.paulorchard.islandcraft.wormsofarrakis.fx.CrashFx;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxOptions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.FxPlayer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 11 part 2: the final crash sound sheet (17 rows, 79 plays). */
class Prompt11SoundsTest {
    @Test
    void theFinalSheetHas17RowsAnd79Plays() {
        assertEquals(17, CrashFx.CUES.size());
        assertEquals(79, CrashFx.totalPlays());
        assertEquals(79, FxPlayer.expand(Prompt10Test.anchors(Prompt10Test.crash(16)), FxOptions.full(true), 1).size());
    }

    @Test
    void theThreeChangedRows() {
        CrashFx.Cue t = CrashFx.CUES.get(0);
        assertEquals("Tremor: warden step", t.name());
        assertEquals("entity.warden.step", t.event());
        assertEquals(4.6, t.vol());
        assertEquals(0.25, t.pitch());
        assertEquals(CrashFx.Anchor.APPROACH, t.anchor());
        assertEquals(-0.15, t.off());
        assertEquals(7, t.rep());
        assertEquals(0.33, t.every());
        assertEquals(0.12, t.jit());
        CrashFx.Cue h = CrashFx.CUES.get(1);
        assertEquals("entity.warden.heartbeat", h.event());
        assertEquals(1, h.rep());
        assertEquals(2.4, h.vol());
        assertEquals(0.55, h.pitch());
        assertEquals(0.3, h.off());
        assertTrue(CrashFx.CUES.stream().noneMatch(c -> c.name().contains("Re-entry: impact")));
        assertTrue(CrashFx.CUES.stream().noneMatch(c -> c.event().equals("block.gravel.step")));
        Map<String, Integer> reps = new HashMap<>();
        for (CrashFx.Cue c : CrashFx.CUES) reps.put(c.name(), c.rep());
        assertEquals(8, reps.get("Re-entry: sand collapse"));
        assertEquals(4, reps.get("Re-entry: gravel"));
    }
}
