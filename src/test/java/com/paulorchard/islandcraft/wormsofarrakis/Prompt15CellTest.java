package com.paulorchard.islandcraft.wormsofarrakis;

import static org.junit.jupiter.api.Assertions.*;

import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleCell;
import com.paulorchard.islandcraft.wormsofarrakis.rumble.RumbleModels;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/** Prompt 15 part 1: the cell mapping and the committed models. */
class Prompt15CellTest {
    private static String res(String path) throws Exception {
        try (var in = Prompt15CellTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyOffsetMapsToTheRightCell() {
        assertEquals(RumbleCell.Kind.NONE, RumbleCell.of(0).kind());
        for (int n = 1; n <= 15; n++) {
            RumbleCell up = RumbleCell.of(n);
            assertEquals(RumbleCell.Kind.RAISE, up.kind());
            assertEquals(1, up.dy(), "raised: the air cell above the surface block");
            assertEquals(n, up.level());
            assertEquals(n, up.offset());
            assertEquals(101, up.y(100));
            RumbleCell down = RumbleCell.of(-n);
            assertEquals(RumbleCell.Kind.LOWER, down.kind());
            assertEquals(0, down.dy(), "lowered: replaces the surface block");
            assertEquals(16 - n, down.level());
            assertEquals(-n, down.offset());
            assertEquals(100, down.y(100));
            assertTrue(up.level() >= 1 && up.level() <= 15 && down.level() >= 1 && down.level() <= 15);
        }
    }

    @Test
    void rangeIsClamped() {
        assertEquals(8, RumbleCell.clamp(12, 8));
        assertEquals(-8, RumbleCell.clamp(-40, 8));
        assertEquals(3, RumbleCell.clamp(3, 8));
        assertEquals(15, RumbleCell.clamp(99, 99), "the limit itself is at most 15");
        assertEquals(1, RumbleCell.clamp(5, 0), "the limit itself is at least 1");
        assertEquals(15, RumbleCell.of(40).level(), "an offset beyond 15 is taken as 15");
    }

    @Test
    void modelsAndBlockstateExistAndReferenceTheDuneSandTexture() throws Exception {
                String state = res(RumbleModels.blockstatePath());
        assertEquals(RumbleModels.blockstate(), state, "the committed blockstate is what the generator writes");
        for (int l = 1; l <= 15; l++) {
            String model = res(RumbleModels.modelPath(l));
            assertEquals(RumbleModels.model(l), model, "level " + l);
            assertTrue(state.contains("\"level=" + l + "\"") && state.contains(RumbleModels.modelId(l)));
            assertTrue(model.contains("\"minecraft:block/sand\""), "the texture by reference (the one Dune Sand uses), never a copied image");
            assertTrue(model.contains("\"to\": [ 16, " + l + ", 16 ]"));
            assertTrue(model.contains("[ 0, " + (16 - l) + ", 16, 16 ]"), "the sides show the matching strip");
        }
        assertNull(Prompt15CellTest.class.getResource("/assets/islandcraft_wormsofarrakis/textures"), "no image is copied");
        java.nio.file.Path dunes = java.nio.file.Path.of("C:/Apps/IslandCraft-MC/DunesOfArrakis/DunesGridV1/src/main/resources/assets/islandcraft_dunesofarrakis/models/block/dune_sand.json");
        if (Files.exists(dunes)) assertTrue(Files.readString(dunes).contains(RumbleModels.TEXTURE), "Dune Sand itself uses this texture");
    }
}
