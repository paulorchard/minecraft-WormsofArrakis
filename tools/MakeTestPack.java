import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Prompt 01 experiment 3: builds the tiny test resource pack (a separate zip, never part of the mod jar).
 * It replaces only assets/minecraft/textures/block/dripstone_block.png with a 64x64 image: a 4x4 grid of coloured
 * cells, each numbered 1..16 in its top-left corner, with a thick red arrow-corner in cell 1 so orientation is obvious.
 * Run:  java tools/MakeTestPack.java testpack/ArrakisWormTestPack.zip
 */
public class MakeTestPack {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path out = Path.of(args.length > 0 ? args[0] : "testpack/ArrakisWormTestPack.zip");
        Files.createDirectories(out.toAbsolutePath().getParent());

        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                float hue = idx / 16f;
                g.setColor(Color.getHSBColor(hue, 0.75f, 0.95f));
                g.fillRect(col * 16, row * 16, 16, 16);
                g.setColor(Color.WHITE);
                g.drawRect(col * 16, row * 16, 15, 15);
                g.setColor(Color.BLACK);
                g.drawString(Integer.toString(idx + 1), col * 16 + 2, row * 16 + 10);
            }
        }
        // orientation mark: black bar along the top edge of the whole image
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 64, 2);
        g.dispose();

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);

        String mcmeta = "{\n  \"pack\": {\n    \"description\": \"Arrakis worm test pack: replaces ONLY block/dripstone_block.png with a numbered 64x64 grid\",\n"
                + "    \"min_format\": 84,\n    \"max_format\": 84\n  }\n}\n";

        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(os)) {
            put(zip, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/minecraft/textures/block/dripstone_block.png", png.toByteArray());
        }
        System.out.println("wrote " + out.toAbsolutePath() + " (" + Files.size(out) + " bytes)");
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }
}
