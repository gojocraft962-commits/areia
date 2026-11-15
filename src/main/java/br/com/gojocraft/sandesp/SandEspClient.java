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
 * Client-side sand ESP — otimizado.
 * - Scan por secao, pula vazias
 * - Areia limitada em Y e quantidade
 * - Suspicious sempre priorizado
 * - Sem sort/alloc por frame
 * Press K to toggle.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 2;              // 2 chunks ~= 32 blocos
    private static final int REFRESH_INTERVAL_TICKS = 25;
    private static final int MOVE_THRESHOLD = 10;

    private static final int MAX_SAND_STORED = 80;          // cap no cache
    private static final int MAX_SAND_OUTLINES = 48;
    private static final int MAX_SAND_ARROWS = 6;
    private static final int MAX_SUS_OUTLINES = 64;
    private static final int MAX_SUS_ARROWS = 24;

    private static final int SAND_Y_RANGE = 24;             // so escaneia areia perto do Y do player
    private static final float ARROW_SIZE = 0.45f;
    private static final int SUSPICIOUS_COLOR = 0xFFFFD700;

    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>(MAX_SAND_STORED);
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
                    refreshCache(client);
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
                    refreshCache(client);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!enabled) return;
            if (SAND_BLOCKS.isEmpty() && SUSPICIOUS_BLOCKS.isEmpty()) return;
            if (context.matrices() == null || context.consumers() == null) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
            int rgb = animatedRgbColor();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
            MatrixStack matrices = context.matrices();

            matrices.push();
            try {
                // Suspicious (poucos, prioritarios) — sem sort extra
                drawList(matrices, lines, cam, SUSPICIOUS_BLOCKS,
                        SUSPICIOUS_COLOR, MAX_SUS_OUTLINES, MAX_SUS_ARROWS, 3.0f);

                // Areia normal — limites baixos
                drawList(matrices, lines, cam, SAND_BLOCKS,
                        rgb, MAX_SAND_OUTLINES, MAX_SAND_ARROWS, 2.0f);
            } finally {
                matrices.pop();
            }
        });
    }

    /** Desenha ate maxOutlines / maxArrows — lista ja vem ordenada do scan. */
    private static void drawList(
            MatrixStack matrices,
            VertexConsumer lines,
            Vec3d cam,
            List<BlockPos> blocks,
            int color,
            int maxOutlines,
            int maxArrows,
            float lineWidth
    ) {
        int n = blocks.size();
        if (n == 0) return;

        int outlines = 0;
        int arrows = 0;
        int limit = Math.min(n, Math.max(maxOutlines, maxArrows));

        for (int i = 0; i < limit; i++) {
            BlockPos pos = blocks.get(i);
            double bx = pos.getX() - cam.x;
            double by = pos.getY() - cam.y;
            double bz = pos.getZ() - cam.z;

            if (outlines < maxOutlines) {
                VertexRendering.drawOutline(
                        matrices, lines, VoxelShapes.fullCube(),
                        bx, by, bz, color, lineWidth
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
        }
    }

    private static void drawLine(
            MatrixStack matrices, VertexConsumer lines,
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
        if (len < 1.0e-5f) return;

        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        lines.vertex(entry, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(entry, nx, ny, nz);
        lines.vertex(entry, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(entry, nx, ny, nz);
    }

    private static void drawArrowHead(
            MatrixStack matrices, VertexConsumer lines,
            double tx, double ty, double tz, int color
    ) {
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 0.001) return;

        double dx = tx / len;
        double dy = ty / len;
        double dz = tz / len;

        double px, py, pz;
        if (Math.abs(dy) < 0.9) {
            px = -dz; py = 0; pz = dx;
        } else {
            px = 0; py = dz; pz = -dy;
        }

        double plen = Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1.0e-5) return;
        px /= plen; py /= plen; pz /= plen;

        double bx = tx - dx * ARROW_SIZE;
        double by = ty - dy * ARROW_SIZE;
        double bz = tz - dz * ARROW_SIZE;
        double s = ARROW_SIZE * 0.55;

        drawLine(matrices, lines, bx + px * s, by + py * s, bz + pz * s, tx, ty, tz, color);
        drawLine(matrices, lines, bx - px * s, by - py * s, bz - pz * s, tx, ty, tz, color);
        drawLine(matrices, lines, bx + px * s, by + py * s, bz + pz * s, bx - px * s, by - py * s, bz - pz * s, color);
    }

    private static void refreshCache(MinecraftClient client) {
        SAND_BLOCKS.clear();
        SUSPICIOUS_BLOCKS.clear();

        BlockPos center = client.player.getBlockPos();
        lastScanCenter = center.toImmutable();

        int playerChunkX = center.getX() >> 4;
        int playerChunkZ = center.getZ() >> 4;
        int playerY = center.getY();
        int bottomSection = client.world.getBottomSectionCoord();

        int minSandY = playerY - SAND_Y_RANGE;
        int maxSandY = playerY + SAND_Y_RANGE;

        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int cx = playerChunkX - CHUNK_RADIUS; cx <= playerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = playerChunkZ - CHUNK_RADIUS; cz <= playerChunkZ + CHUNK_RADIUS; cz++) {
                if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) continue;

                WorldChunk chunk = client.world.getChunk(cx, cz);
                ChunkSection[] sections = chunk.getSectionArray();

                for (int si = 0; si < sections.length; si++) {
                    ChunkSection section = sections[si];
                    if (section == null || section.isEmpty()) continue;

                    int baseY = ChunkSectionPos.getBlockCoord(bottomSection + si);
                    int sectionMaxY = baseY + 15;

                    // Secao fora do range de areia E sem chance de suspicious no Y?
                    // Suspicious e raro: ainda escaneamos secoes fora do range so para suspicious
                    boolean inSandY = sectionMaxY >= minSandY && baseY <= maxSandY;
                    boolean sandFull = SAND_BLOCKS.size() >= MAX_SAND_STORED;

                    if (!inSandY && sandFull) {
                        // Ainda procura suspicious em qualquer Y
                    }

                    int baseX = cx << 4;
                    int baseZ = cz << 4;

                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            int worldY = baseY + ly;
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, ly, lz);

                                if (state.isOf(Blocks.SUSPICIOUS_SAND)
                                        || state.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
                                    cursor.set(baseX + lx, worldY, baseZ + lz);
                                    SUSPICIOUS_BLOCKS.add(cursor.toImmutable());
                                } else if (!sandFull
                                        && inSandY
                                        && worldY >= minSandY && worldY <= maxSandY
                                        && (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND))) {
                                    cursor.set(baseX + lx, worldY, baseZ + lz);
                                    SAND_BLOCKS.add(cursor.toImmutable());
                                    sandFull = SAND_BLOCKS.size() >= MAX_SAND_STORED;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ordena 1x no scan (nao no render) — mais perto primeiro
        Comparator<BlockPos> byDist = Comparator.comparingDouble(
                p -> p.getSquaredDistance(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5)
        );
        SAND_BLOCKS.sort(byDist);
        SUSPICIOUS_BLOCKS.sort(byDist);
    }

    private static int animatedRgbColor() {
        float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
        float scaled = hue * 6.0f;
        int sector = (int) scaled;
        float fraction = scaled - sector;
        float q = 1.0f - fraction;
        float t = fraction;

        float red, green, blue;
        switch (sector % 6) {
            case 0 -> { red = 1f; green = t; blue = 0f; }
            case 1 -> { red = q; green = 1f; blue = 0f; }
            case 2 -> { red = 0f; green = 1f; blue = t; }
            case 3 -> { red = 0f; green = q; blue = 1f; }
            case 4 -> { red = t; green = 0f; blue = 1f; }
            default -> { red = 1f; green = 0f; blue = q; }
        }

        return 0xFF000000
                | (Math.round(red * 255f) << 16)
                | (Math.round(green * 255f) << 8)
                | Math.round(blue * 255f);
    }
}
