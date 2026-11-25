package com.paulorchard.islandcraft.wormsofarrakis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.paulorchard.islandcraft.wormsofarrakis.fx.SizeFactor;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveOptions;
import com.paulorchard.islandcraft.wormsofarrakis.test.PresetCommand;
import com.paulorchard.islandcraft.wormsofarrakis.test.Presets;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Prompt 12 part 2: the four size presets and the command. */
class Prompt12PresetTest {
    /** The prompt's table: crash, ballistic, emerge, engulf, crest at small 8, medium 16, large 32, huge 48. */
    private static final int[][] TABLE = {
            {49, 98, 195, 292},     // crash (Moves.fall)
            {62, 101, 179, 257},    // ballistic (Moves.crash)
            {40, 80, 160, 240},     // crest: 5 W
            {12, 12, 12, 12},       // emerge: n + 2
            {4, 4, 4, 4}};          // engulf: exposed + 2

    @Test
    void theMinimumSegmentsOfAllTwentyPairsAreComputedAndMatchTheTable() {
        StringBuilder diff = new StringBuilder();
        for (int m = 0; m < Presets.MOVES.size(); m++) {
            Presets.Size[] sizes = Presets.Size.values();
            for (int s = 0; s < sizes.length; s++) {
                int got = Presets.minSegments(Presets.MOVES.get(m), sizes[s].width);
                if (got != TABLE[m][s]) diff.append(Presets.MOVES.get(m)).append(' ').append(sizes[s].label).append(": computed ").append(got).append(" table ").append(TABLE[m][s]).append('\n');
            }
        }
        assertEquals("", diff.toString(), "deviations from the prompt's table");
    }

    @Test
    void everyPresetBuildsWithoutForceAndOneSegmentFewerIsRejectedForTheMovesWithALengthCheck() {
        for (String mv : Presets.MOVES) for (Presets.Size size : Presets.Size.values()) {
            Presets.Preset p = Presets.of(size, mv);
            assertNull(Presets.refusal(mv, size.width, p.segments()), mv + " " + size.label + " builds");
            assertNotNull(MoveFactory.get(p.spec()));
            if (mv.equals("crash") || mv.equals("ballistic") || mv.equals("emerge") || mv.equals("engulf"))
                assertNotNull(Presets.refusal(mv, size.width, p.segments() - 1), mv + " " + size.label + ": one segment fewer is rejected (the minimum is the minimum)");
            if (mv.equals("crest")) assertEquals((int) Math.ceil(5 * size.width), p.segments(), "a crest is 5 W");
            assertFalse(Boolean.parseBoolean(String.valueOf(p.spec().p(10, 0) > 0)), "never forced");
        }
    }

    @Test
    void theSpecEqualsTheLongFormOfTheTestCommand() {
        for (Presets.Size size : Presets.Size.values()) {
            for (String mv : new String[]{"crash", "crest", "emerge", "engulf"}) {
                int segs = Presets.minSegments(mv, size.width);
                WormSpec longForm = MoveOptions.parse(mv, size.width, segs, "", false).spec();
                assertEquals(longForm.serialize(), Presets.of(size, mv).spec().serialize(), mv + " " + size.label);
            }
            int segs = Presets.minSegments("ballistic", size.width);
            WormSpec arc = MoveFactory.spec("crash", size.width, segs, 10, 70, 12, 0, 12, 4, true, true, false, 1.0, 1.0, 1);
            assertEquals(arc.serialize(), Presets.of(size, "ballistic").spec().serialize());
        }
        // every other field at its default: sway on, fx on (crest, crash, emerge, engulf), tail on for the crest
        WormSpec crest = Presets.of(Presets.Size.MEDIUM, "crest").spec();
        assertTrue(MoveFactory.swayParams(crest).isDefault());
        assertTrue(MoveFactory.tailSpec(crest).isDefault());
        assertTrue(MoveFactory.fxOptions(crest).on());
    }

    @Test
    void theCommandAcceptsFourSizesAndFiveMovesAndNothingElse() throws CommandSyntaxException {
        List<String> got = new ArrayList<>();
        CommandDispatcher<String> d = new CommandDispatcher<>();
        d.register(PresetCommand.<String>tree((ctx, size, move) -> { got.add(size.label + " " + move); return 1; }));
        for (String size : new String[]{"small", "medium", "large", "huge"}) for (String mv : new String[]{"crash", "ballistic", "crest", "emerge", "engulf"})
            assertEquals(1, d.execute("arrakis-worm " + size + " " + mv, "src"));
        assertEquals(20, got.size());
        assertEquals("small crash", got.get(0));
        assertEquals("huge engulf", got.get(19));
        for (String bad : new String[]{"arrakis-worm", "arrakis-worm small", "arrakis-worm tiny crash", "arrakis-worm small crasharc", "arrakis-worm small crash 49", "arrakis-worm small crash force",
                "arrakis-worm small crash fx=off", "arrakis-worm 8 crash", "arrakis-worm small crash 0 0 0", "arrakis_worm small crash"})
            assertThrows(CommandSyntaxException.class, () -> d.execute(bad, "src"), bad);
        // tab completion: the four sizes, then the five moves
        Suggestions s1 = d.getCompletionSuggestions(d.parse("arrakis-worm ", "src")).join();
        assertEquals(List.of("small", "medium", "large", "huge"), s1.getList().stream().map(x -> x.getText()).toList().stream().sorted((a, b) -> order(List.of("small", "medium", "large", "huge"), a) - order(List.of("small", "medium", "large", "huge"), b)).toList());
        Suggestions s2 = d.getCompletionSuggestions(d.parse("arrakis-worm large ", "src")).join();
        assertEquals(5, s2.getList().size());
        assertTrue(s2.getList().stream().map(x -> x.getText()).toList().containsAll(Presets.MOVES));
    }

    private static int order(List<String> l, String x) { return l.indexOf(x); }

    @Test
    void theLinesAndTheMovesText() {
        Presets.Preset p = Presets.of(Presets.Size.SMALL, "crash");
        assertTrue(p.describe().startsWith("preset small crash = crash W8 segments=49 (sizeFactor 0.25)"), p.describe());
        assertTrue(p.describe().endsWith("/arrakis_worm_test crash 8 49"), p.describe());
        assertTrue(Presets.of(Presets.Size.HUGE, "ballistic").describe().startsWith("preset huge ballistic = ballistic crash W48 segments=257 (sizeFactor 1.00)"));
        assertTrue(Presets.of(Presets.Size.MEDIUM, "ballistic").describe().endsWith("/arrakis_worm_test crasharc 16 101"));
        List<String> lines = MoveOptions.movesText();
        String all = String.join("\n", lines);
        assertTrue(all.contains("/arrakis-worm <small|medium|large|huge> <crash|ballistic|crest|emerge|engulf>"));
        assertTrue(all.contains("preset crash segments: small=49 medium=98 large=195 huge=292"), all);
        assertTrue(all.contains("preset engulf segments: small=4 medium=4 large=4 huge=4"));
        assertTrue(all.contains("preset crest segments: small=40 medium=80 large=160 huge=240"));
        assertEquals(SizeFactor.of(8), 0.25);
    }

    private static double spacing(Moves.Move m) {
        double[][] j = m.pose(m.at(0.5)[0]).joints;
        return Math.hypot(Math.hypot(j[1][0] - j[0][0], j[1][1] - j[0][1]), j[1][2] - j[0][2]);
    }

    @Test
    void numbersForTheReport() throws IOException {
        StringBuilder b = new StringBuilder();
        for (String mv : Presets.MOVES) for (Presets.Size size : Presets.Size.values()) {
            Presets.Preset p = Presets.of(size, mv);
            Moves.Move m = MoveFactory.get(p.spec());
            double r0 = m.r0;
            b.append(String.format(Locale.ROOT, "%s %s W%.0f: segments %d, cycle %.2f s, play %.2f s, r0 %.2f, joint spacing %.3f blocks, body length %.1f blocks%n", mv, size.label, size.width, p.segments(), m.cycle, m.playTime(), r0, spacing(m), spacing(m) * p.segments()));
        }
        Files.writeString(Path.of("build/prompt12-presets.txt"), b.toString());
    }
}
