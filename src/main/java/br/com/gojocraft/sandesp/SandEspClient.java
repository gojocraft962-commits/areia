package br.com.gojocraft.sandesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

public final class SandEspClient implements ClientModInitializer {
    private static boolean enabled = true;
    private static KeyBinding toggleKey;
    private static long frame;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sand_rgb_esp.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) enabled = !enabled;
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!enabled || context.world() == null || context.matrices() == null || context.consumers() == null) return;
            var camera = context.camera().getPos();
            var player = context.camera().getBlockPos();
            int radius = 24;
            float hue = (System.currentTimeMillis() % 5000L) / 5000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            float red = ((rgb >> 16) & 255) / 255.0f;
            float green = ((rgb >> 8) & 255) / 255.0f;
            float blue = (rgb & 255) / 255.0f;
            int color = (255 << 24) | ((rgb >> 16) & 0xFFFFFF);
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
            BlockPos.Mutable pos = new BlockPos.Mutable();
            context.matrices().push();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        pos.set(player.getX() + x, player.getY() + y, player.getZ() + z);
                        BlockState state = context.world().getBlockState(pos);
                        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
                            double dx = pos.getX() - camera.x;
                            double dy = pos.getY() - camera.y;
                            double dz = pos.getZ() - camera.z;
                            VertexRendering.drawOutline(context.matrices(), lines,
                                    VoxelShapes.fullCube(), dx, dy, dz, color, 2.0f);
                        }
                    }
                }
            }
            context.matrices().pop();
            frame++;
        });
    }
}
