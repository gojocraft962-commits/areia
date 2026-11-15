package br.com.gojocraft.sandesp;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

public final class SandEspClient implements ClientModInitializer {
    private static boolean enabled = true;
    private static KeyBinding toggleKey;
    private static final List<BlockPos> SAND_BLOCKS = new ArrayList<>();
    private static final int SEARCH_RADIUS = 16;
    private static int tickCounter;

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
                if (!enabled) SAND_BLOCKS.clear();
            }

            if (!enabled || client.world == null || client.player == null) {
                SAND_BLOCKS.clear();
                return;
            }

            // Refresh the nearby-block cache every 10 ticks to avoid scanning every frame.
            if (++tickCounter % 10 == 0) {
                refreshSandCache(client);
            }
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.world == null || SAND_BLOCKS.isEmpty()
                    || context.matrices() == null || context.consumers() == null) {
                return;
            }

            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            float hue = (System.currentTimeMillis() % 5000L) / 5000.0f;
            int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            int color = 0xFF000000 | (rgb & 0x00FFFFFF);
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

            context.matrices().push();
            for (BlockPos pos : SAND_BLOCKS) {
                double dx = pos.getX() - cameraPos.x;
                double dy = pos.getY() - cameraPos.y;
                double dz = pos.getZ() - cameraPos.z;
                VertexRendering.drawOutline(
                        context.matrices(),
                        lines,
                        VoxelShapes.fullCube(),
                        dx, dy, dz,
                        color,
                        2.0f
                );
            }
            context.matrices().pop();
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
}
