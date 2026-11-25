package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prompt 15 part 1: the generator of the 15 block models and the one blockstate of arrakis_rumble_sand, pure text. Run as a build step
 * ({@code ./gradlew generateRumbleModels}); the output is committed and a unit test regenerates it in memory and compares.
 * The texture is the one the Dune Sand model of the Dunes of Arrakis mod uses (read from its dune_sand.json: minecraft:block/sand), by reference,
 * so texture packs apply and a game without Dunes shows the same sand.
 */
public final class RumbleModels {
    private RumbleModels() {}

    public static final String NAMESPACE = "islandcraft_wormsofarrakis", NAME = "arrakis_rumble_sand";
    /** The texture the Dune Sand block of Dunes of Arrakis uses (its model's "all"), by reference. */
    public static final String TEXTURE = "minecraft:block/sand";

    /** One level: a cuboid from the bottom of the block space up to level/16; the sides show the matching strip of the texture (its bottom level/16), not a stretched side. */
    public static String model(int level) {
        String strip = "[ 0, " + (16 - level) + ", 16, 16 ]";
        return "{\n"
                + "  \"parent\": \"minecraft:block/block\",\n"
                + "  \"textures\": { \"all\": \"" + TEXTURE + "\", \"particle\": \"" + TEXTURE + "\" },\n"
                + "  \"elements\": [\n"
                + "    {\n"
                + "      \"from\": [ 0, 0, 0 ],\n"
                + "      \"to\": [ 16, " + level + ", 16 ],\n"
                + "      \"faces\": {\n"
                + "        \"down\":  { \"uv\": [ 0, 0, 16, 16 ], \"texture\": \"#all\", \"cullface\": \"down\" },\n"
                + "        \"up\":    { \"uv\": [ 0, 0, 16, 16 ], \"texture\": \"#all\" },\n"
                + "        \"north\": { \"uv\": " + strip + ", \"texture\": \"#all\", \"cullface\": \"north\" },\n"
                + "        \"south\": { \"uv\": " + strip + ", \"texture\": \"#all\", \"cullface\": \"south\" },\n"
                + "        \"west\":  { \"uv\": " + strip + ", \"texture\": \"#all\", \"cullface\": \"west\" },\n"
                + "        \"east\":  { \"uv\": " + strip + ", \"texture\": \"#all\", \"cullface\": \"east\" }\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    public static String modelId(int level) { return NAMESPACE + ":block/" + NAME + "_" + level; }

    /** The single blockstate file: one variant per level. */
    public static String blockstate() {
        StringBuilder b = new StringBuilder("{\n  \"variants\": {\n");
        for (int l = 1; l <= 15; l++) b.append("    \"level=").append(l).append("\": { \"model\": \"").append(modelId(l)).append("\" }").append(l < 15 ? ",\n" : "\n");
        return b.append("  }\n}\n").toString();
    }

    public static String modelPath(int level) { return "assets/" + NAMESPACE + "/models/block/" + NAME + "_" + level + ".json"; }
    public static String blockstatePath() { return "assets/" + NAMESPACE + "/blockstates/" + NAME + ".json"; }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        write(root.resolve(blockstatePath()), blockstate());
        for (int l = 1; l <= 15; l++) write(root.resolve(modelPath(l)), model(l));
        System.out.println("wrote the blockstate and 15 models under " + root);
    }

    private static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }
}
