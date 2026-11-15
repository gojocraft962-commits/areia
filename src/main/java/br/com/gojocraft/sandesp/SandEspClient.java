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
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side sand ESP for Minecraft 1.21.11 / Fabric.
 * Press K to toggle the outline.
 */
public final class SandEspClient implements ClientModInitializer {
    private static final int SEARCH_RADIUS = 12;
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>();

    private static boolean enabled = true;
    private static int tickCounter = REFRESH_INTERVAL_TICKS - 1;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sand_rgb_esp.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (!enabled) {
                    SAND_BLOCKS.clear();
                } else {
                    tickCounter = REFRESH_INTERVAL_TICKS - 1;
                }
            }

            if (!enabled || client.world == null || client.player == null) {
                SAND_BLOCKS.clear();
                tickCounter = REFRESH_INTERVAL_TICKS - 1;
                return;
            }

            if (++tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                refreshSandCache(client);
            }
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.world == null || client.player == null
                    || SAND_BLOCKS.isEmpty()
                    || context.matrices() == null || context.consumers() == null) {
                return;
            }

            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            int color = animatedRgbColor();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

            context.matrices().push();
            try {
                for (BlockPos pos : SAND_BLOCKS) {
                    double x = pos.getX() - cameraPos.x;
                    double y = pos.getY() - cameraPos.y;
                    double z = pos.getZ() - cameraPos.z;

                    VertexRendering.drawOutline(
                            context.matrices(),
                            lines,
                            VoxelShapes.fullCube(),
                            x, y, z,
                            color,
                            2.0f
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

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    var state = client.world.getBlockState(cursor);
                    if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
                        SAND_BLOCKS.add(cursor.toImmutable());
                    }
                }
            }
        }
    }

    private static int animatedRgbColor() {
        float hue = (System.currentTimeMillis() % 5000L) / 5000.0f;
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
