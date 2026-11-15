package br.com.gojocraft.sandesp;

import java.util.ArrayList;
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
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sand ESP for Minecraft 1.21.11 / Fabric.
 * Optimized: scans by chunk section, skips empty sections, redraw limit.
 * Press K to toggle.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int CHUNK_RADIUS = 3;          // 3 chunks = ~48 blocos
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int MAX_DRAW = 400;            // limite de caixas por frame
    private static final int MOVE_THRESHOLD = 8;        // rescan so se andar 8+ blocos

    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>(256);

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
                    lastScanCenter = null;
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Areia ESP: OFF"), true);
                    }
                } else if (client.world != null && client.player != null) {
                    lastScanCenter = null; // forca rescan
                    refreshSandCache(client);
                    client.player.sendMessage(
                            Text.literal("Areia ESP: ON (" + SAND_BLOCKS.size() + " blocos)"),
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

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if (!enabled || SAND_BLOCKS.isEmpty()) {
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
            float[] rgb = animatedRgb();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());

            int drawn = 0;
            context.matrices().push();
            try {
                for (BlockPos pos : SAND_BLOCKS) {
                    if (drawn >= MAX_DRAW) break;

                    double x1 = pos.getX() - cam.x;
                    double y1 = pos.getY() - cam.y;
                    double z1 = pos.getZ() - cam.z;

                    VertexRendering.drawBox(
                            context.matrices(),
                            lines,
                            x1, y1, z1,
                            x1 + 1.0, y1 + 1.0, z1 + 1.0,
                            rgb[0], rgb[1], rgb[2], 1.0f
                    );
                    drawn++;
                }
            } finally {
                context.matrices().pop();
            }
        });
    }

    /**
 * Escaneia por secao de chunk (16x16x16).
 * Pula secoes vazias — bem mais rapido que varrer bloco a bloco no mundo todo.
 */
    private static void refreshSandCache(MinecraftClient client) {
        SAND_BLOCKS.clear();

        BlockPos center = client.player.getBlockPos();
        lastScanCenter = center.toImmutable();

        int playerChunkX = center.getX() >> 4;
        int playerChunkZ = center.getZ() >> 4;
        int bottomSection = client.world.getBottomSectionCoord();
        int topSection = client.world.getTopSectionCoord() - 1;

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
                    if (sectionY < bottomSection || sectionY > topSection) {
                        continue;
                    }

                    int baseX = cx << 4;
                    int baseY = ChunkSectionPos.getBlockCoord(sectionY);
                    int baseZ = cz << 4;

                    // varre so a secao 16x16x16
                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, ly, lz);
                                if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
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

    private static float[] animatedRgb() {
        float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
        float scaled = hue * 6.0f;
        int sector = (int) scaled;
        float fraction = scaled - sector;
        float q = 1.0f - fraction;
        float t = fraction;

        return switch (sector % 6) {
            case 0 -> new float[]{1.0f, t, 0.0f};
            case 1 -> new float[]{q, 1.0f, 0.0f};
            case 2 -> new float[]{0.0f, 1.0f, t};
            case 3 -> new float[]{0.0f, q, 1.0f};
            case 4 -> new float[]{t, 0.0f, 1.0f};
            default -> new float[]{1.0f, 0.0f, q};
        };
    }
}
