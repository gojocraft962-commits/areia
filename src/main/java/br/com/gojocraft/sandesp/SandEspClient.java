package br.com.gojocraft.sandesp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sand ESP for Minecraft 1.21.11 / Fabric.
 * Detecta areia, areia vermelha, suspicious sand e suspicious gravel.
 * Outline + setas. Press K to toggle.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 3;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int MAX_OUTLINES = 300;
    private static final int MAX_ARROWS = 40;
    private static final int MOVE_THRESHOLD = 8;
    private static final float ARROW_SIZE = 0.45f;

    // Cor dourada fixa para Suspicious Sand / Gravel
    private static final int SUSPICIOUS_COLOR = 0xFFFFD700;

    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>(256);
    private static final List<BlockPos> SUSPICIOUS_BLOCKS = new ArrayList<>(64);

    private static boolean enabled = true;
    private static int tickCounter = 0;
    private static BlockPos lastScanCenter = null;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.areia_esp.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;

                if (!enabled) {
                    SAND_BLOCKS.clear();
                    SUSPICIOUS_BLOCKS.clear();
                    lastScanCenter = null;
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Areia ESP: OFF"), true);
                    }
                } else if (client.world != null && client.player != null) {
                    lastScanCenter = null;
                    refreshSandCache(client);
                    client.player.sendMessage(
                            Text.literal("Areia ESP: ON (areia " + SAND_BLOCKS.size()
                                    + " | suspicious " + SUSPICIOUS_BLOCKS.size() + ")"),
                            true
                    );
                }
            }

            if (!enabled || client.world == null || client.player == null) {
                return;
            }

            if (++tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                BlockPos center = client.player.getBlockPos();
                if (lastScanCenter == null
                        || lastScanCenter.getManhattanDistance(center) >= MOVE_THRESHOLD) {
                    refreshSandCache(client);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!enabled) {
                return;
            }
            if (SAND_BLOCKS.isEmpty() && SUSPICIOUS_BLOCKS.isEmpty()) {
                return;
            }
            if (context.matrices() == null || context.consumers() == null) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return;
            }

            Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
            int rgbColor = animatedRgbColor();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
            MatrixStack matrices = context.matrices();

            matrices.push();
            try {
                // Suspicious primeiro (prioridade nas setas)
                renderTargets(matrices, lines, cam, SUSPICIOUS_BLOCKS, SUSPICIOUS_COLOR, true);
                renderTargets(matrices, lines, cam, SAND_BLOCKS, rgbColor, false);
            } finally {
                matrices.pop();
            }
        });
    }

    private static void renderTargets(
            MatrixStack matrices,
            VertexConsumer lines,
            Vec3d cam,
            List<BlockPos> blocks,
            int color,
            boolean prioritizeArrows
    ) {
        if (blocks.isEmpty()) {
            return;
        }

        List<BlockPos> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingDouble(pos ->
                pos.getSquaredDistance(cam.x, cam.y, cam.z)));

        int outlines = 0;
        int arrows = 0;
        int maxArrows = prioritizeArrows ? MAX_ARROWS : Math.max(10, MAX_ARROWS / 2);

        for (BlockPos pos : sorted) {
            double bx = pos.getX() - cam.x;
            double by = pos.getY() - cam.y;
            double bz = pos.getZ() - cam.z;

            if (outlines < MAX_OUTLINES) {
                VertexRendering.drawOutline(
                        matrices,
                        lines,
                        VoxelShapes.fullCube(),
                        bx, by, bz,
                        color,
                        prioritizeArrows ? 3.0f : 2.0f
                );
                outlines++;
            }

            if (arrows < maxArrows) {
                double tx = bx + 0.5;
                double ty = by + 0.5;
                double tz = bz + 0.5;
                drawLine(matrices, lines, 0, 0, 0, tx, ty, tz, color);
                drawArrowHead(matrices, lines, tx, ty, tz, color);
                arrows++;
            }

            if (outlines >= MAX_OUTLINES && arrows >= maxArrows) {
                break;
            }
        }
    }

    private static void drawLine(
            MatrixStack matrices,
            VertexConsumer lines,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int color
    ) {
        MatrixStack.Entry entry = matrices.peek();

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f) {
            return;
        }

        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        lines.vertex(entry, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
        lines.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
    }

    private static void drawArrowHead(
            MatrixStack matrices,
            VertexConsumer lines,
            double tx, double ty, double tz,
            int color
    ) {
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 0.001) {
            return;
        }

        double dx = tx / len;
        double dy = ty / len;
        double dz = tz / len;

        double px;
        double py;
        double pz;
        if (Math.abs(dy) < 0.9) {
            px = -dz;
            py = 0;
            pz = dx;
        } else {
            px = 0;
            py = dz;
            pz = -dy;
        }

        double plen = Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1.0e-5) {
            return;
        }
        px /= plen;
        py /= plen;
        pz /= plen;

        double bx = tx - dx * ARROW_SIZE;
        double by = ty - dy * ARROW_SIZE;
        double bz = tz - dz * ARROW_SIZE;
        double spread = ARROW_SIZE * 0.55;

        drawLine(matrices, lines,
                bx + px * spread, by + py * spread, bz + pz * spread,
                tx, ty, tz, color);
        drawLine(matrices, lines,
                bx - px * spread, by - py * spread, bz - pz * spread,
                tx, ty, tz, color);
        drawLine(matrices, lines,
                bx + px * spread, by + py * spread, bz + pz * spread,
                bx - px * spread, by - py * spread, bz - pz * spread,
                color);
    }

    private static void refreshSandCache(MinecraftClient client) {
        SAND_BLOCKS.clear();
        SUSPICIOUS_BLOCKS.clear();

        BlockPos center = client.player.getBlockPos();
        lastScanCenter = center.toImmutable();

        int playerChunkX = center.getX() >> 4;
        int playerChunkZ = center.getZ() >> 4;
        int bottomSection = client.world.getBottomSectionCoord();

        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int cx = playerChunkX - CHUNK_RADIUS; cx <= playerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = playerChunkZ - CHUNK_RADIUS; cz <= playerChunkZ + CHUNK_RADIUS; cz++) {
                if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    continue;
                }

                WorldChunk chunk = client.world.getChunk(cx, cz);
                ChunkSection[] sections = chunk.getSectionArray();

                for (int si = 0; si < sections.length; si++) {
                    ChunkSection section = sections[si];
                    if (section == null || section.isEmpty()) {
                        continue;
                    }

                    int sectionY = bottomSection + si;
                    int baseX = cx << 4;
                    int baseY = ChunkSectionPos.getBlockCoord(sectionY);
                    int baseZ = cz << 4;

                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, ly, lz);

                                if (state.isOf(Blocks.SUSPICIOUS_SAND)
                                        || state.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
                                    cursor.set(baseX + lx, baseY + ly, baseZ + lz);
                                    SUSPICIOUS_BLOCKS.add(cursor.toImmutable());
                                } else if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
                                    cursor.set(baseX + lx, baseY + ly, baseZ + lz);
                                    SAND_BLOCKS.add(cursor.toImmutable());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int animatedRgbColor() {
        float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
        float scaled = hue * 6.0f;
        int sector = (int) scaled;
        float fraction = scaled - sector;
        float q = 1.0f - fraction;
        float t = fraction;

        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> { red = 1.0f; green = t; blue = 0.0f; }
            case 1 -> { red = q; green = 1.0f; blue = 0.0f; }
            case 2 -> { red = 0.0f; green = 1.0f; blue = t; }
            case 3 -> { red = 0.0f; green = q; blue = 1.0f; }
            case 4 -> { red = t; green = 0.0f; blue = 1.0f; }
            default -> { red = 1.0f; green = 0.0f; blue = q; }
        }

        int r = Math.round(red * 255.0f);
        int g = Math.round(green * 255.0f);
        int b = Math.round(blue * 255.0f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
