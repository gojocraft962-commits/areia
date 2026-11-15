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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sand ESP — versao anti-crash.
 * Outline RGB + Suspicious dourado. Press K to toggle.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 2;
    private static final int REFRESH_INTERVAL_TICKS = 30;
    private static final int MOVE_THRESHOLD = 12;
    private static final int JOIN_DELAY_TICKS = 40; // espera mundo estabilizar

    private static final int MAX_SAND_STORED = 60;
    private static final int MAX_SAND_OUTLINES = 40;
    private static final int MAX_SUS_OUTLINES = 48;
    private static final int SAND_Y_RANGE = 20;

    private static final int SUSPICIOUS_COLOR = 0xFFFFD700;

    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>(MAX_SAND_STORED);
    private static final List<BlockPos> SUSPICIOUS_BLOCKS = new ArrayList<>(32);

    private static boolean enabled = true;
    private static int tickCounter = 0;
    private static int joinTicks = 0;
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
            try {
                // Mundo saiu / desconectou
                if (client.world == null || client.player == null) {
                    SAND_BLOCKS.clear();
                    SUSPICIOUS_BLOCKS.clear();
                    lastScanCenter = null;
                    joinTicks = 0;
                    return;
                }

                // Aguarda um pouco apos entrar no mundo/servidor
                if (joinTicks < JOIN_DELAY_TICKS) {
                    joinTicks++;
                    return;
                }

                while (toggleKey.wasPressed()) {
                    enabled = !enabled;
                    if (!enabled) {
                        SAND_BLOCKS.clear();
                        SUSPICIOUS_BLOCKS.clear();
                        lastScanCenter = null;
                        client.player.sendMessage(Text.literal("Areia ESP: OFF"), true);
                    } else {
                        lastScanCenter = null;
                        safeRefresh(client);
                        client.player.sendMessage(
                                Text.literal("Areia ESP: ON (areia " + SAND_BLOCKS.size()
                                        + " | suspicious " + SUSPICIOUS_BLOCKS.size() + ")"),
                                true
                        );
                    }
                }

                if (!enabled) {
                    return;
                }

                if (++tickCounter >= REFRESH_INTERVAL_TICKS) {
                    tickCounter = 0;
                    BlockPos center = client.player.getBlockPos();
                    if (lastScanCenter == null
                            || lastScanCenter.getManhattanDistance(center) >= MOVE_THRESHOLD) {
                        safeRefresh(client);
                    }
                }
            } catch (Throwable t) {
                // Nunca derruba o cliente por causa do ESP
                SAND_BLOCKS.clear();
                SUSPICIOUS_BLOCKS.clear();
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            try {
                if (!enabled) return;
                if (SAND_BLOCKS.isEmpty() && SUSPICIOUS_BLOCKS.isEmpty()) return;
                if (context.matrices() == null || context.consumers() == null) return;

                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null || client.player == null) return;
                if (joinTicks < JOIN_DELAY_TICKS) return;

                Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
                int rgb = animatedRgbColor();
                VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
                MatrixStack matrices = context.matrices();

                matrices.push();
                try {
                    drawOutlines(matrices, lines, cam, SUSPICIOUS_BLOCKS, SUSPICIOUS_COLOR, MAX_SUS_OUTLINES, 3.0f);
                    drawOutlines(matrices, lines, cam, SAND_BLOCKS, rgb, MAX_SAND_OUTLINES, 2.0f);
                } finally {
                    matrices.pop();
                }
            } catch (Throwable t) {
                // ignora erro de render
            }
        });
    }

    private static void drawOutlines(
            MatrixStack matrices,
            VertexConsumer lines,
            Vec3d cam,
            List<BlockPos> blocks,
            int color,
            int max,
            float width
    ) {
        int n = Math.min(blocks.size(), max);
        for (int i = 0; i < n; i++) {
            BlockPos pos = blocks.get(i);
            double x = pos.getX() - cam.x;
            double y = pos.getY() - cam.y;
            double z = pos.getZ() - cam.z;
            VertexRendering.drawOutline(
                    matrices, lines, VoxelShapes.fullCube(),
                    x, y, z, color, width
            );
        }
    }

    private static void safeRefresh(MinecraftClient client) {
        try {
            refreshCache(client);
        } catch (Throwable t) {
            SAND_BLOCKS.clear();
            SUSPICIOUS_BLOCKS.clear();
        }
    }

    private static void refreshCache(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

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
                if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    continue;
                }

                WorldChunk chunk;
                try {
                    chunk = client.world.getChunk(cx, cz);
                } catch (Throwable t) {
                    continue;
                }
                if (chunk == null) continue;

                ChunkSection[] sections = chunk.getSectionArray();
                if (sections == null) continue;

                for (int si = 0; si < sections.length; si++) {
                    ChunkSection section = sections[si];
                    if (section == null || section.isEmpty()) continue;

                    int baseY = ChunkSectionPos.getBlockCoord(bottomSection + si);
                    int sectionMaxY = baseY + 15;
                    boolean inSandY = sectionMaxY >= minSandY && baseY <= maxSandY;
                    boolean sandFull = SAND_BLOCKS.size() >= MAX_SAND_STORED;

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

        Comparator<BlockPos> byDist = Comparator.comparingDouble(
                p -> p.getSquaredDistance(
                        center.getX() + 0.5,
                        center.getY() + 0.5,
                        center.getZ() + 0.5
                )
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
