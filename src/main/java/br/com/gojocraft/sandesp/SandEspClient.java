package br.com.gojocraft.sandesp;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sand ESP for Minecraft 1.21.11 / Fabric.
 * Press K to toggle.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int SEARCH_RADIUS = 32;
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>();

    private static boolean enabled = true;
    private static int tickCounter = 0;
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
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Areia ESP: OFF"), true);
                    }
                } else if (client.world != null && client.player != null) {
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
                refreshSandCache(client);
            }
        });

        // BEFORE_DEBUG_RENDER e o evento certo para linhas/overlays
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

            context.matrices().push();
            try {
                for (BlockPos pos : SAND_BLOCKS) {
                    double x1 = pos.getX() - cam.x;
                    double y1 = pos.getY() - cam.y;
                    double z1 = pos.getZ() - cam.z;
                    double x2 = x1 + 1.0;
                    double y2 = y1 + 1.0;
                    double z2 = z1 + 1.0;

                    // Caixa de outline em volta do bloco
                    VertexRendering.drawBox(
                            context.matrices(),
                            lines,
                            x1, y1, z1,
                            x2, y2, z2,
                            rgb[0], rgb[1], rgb[2], 1.0f
                    );
                }
            } finally {
                context.matrices().pop();
            }
        });
    }

    private static void refreshSandCache(MinecraftClient client) {
        SAND_BLOCKS.clear();

        BlockPos center = client.player.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        int minY = Math.max(client.world.getBottomY(), center.getY() - SEARCH_RADIUS);
        int maxY = Math.min(client.world.getTopYInclusive(), center.getY() + SEARCH_RADIUS);

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                int worldX = center.getX() + x;
                int worldZ = center.getZ() + z;

                if (!client.world.getChunkManager().isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    cursor.set(worldX, y, worldZ);
                    var state = client.world.getBlockState(cursor);
                    if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
                        SAND_BLOCKS.add(cursor.toImmutable());
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
