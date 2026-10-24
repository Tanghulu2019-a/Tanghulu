package dev.tanghulu.ancientapple.integration;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import java.util.List;

/** Xaero's map uses immediate matrices, while its menus use vanilla DrawContext. */
public final class XaeroAppleRenderer {
    private XaeroAppleRenderer() {}
    public static boolean drawMap(Object graphics,Object waypoint,int x,int y,int opacity) {
        if(!XaeroBridge.isOwnedWaypoint(waypoint))return false;
        try {
            var matrices=(MatrixStack)graphics.getClass().getMethod("pose").invoke(graphics);
            var buffers=(VertexConsumerProvider.Immediate)graphics.getClass().getMethod("getBufferSource").invoke(graphics);
            if(opacity<=0)return true;
            var state=appleState();
            matrices.push();
            try {
                drawItem(matrices,buffers,state,x+.5f,y+.5f,16);
            } finally { matrices.pop(); }
            return true;
        } catch(ReflectiveOperationException|LinkageError|RuntimeException e) { report(e); return false; }
    }
    /** World Map has a separate renderer and waypoint wrapper, independent of the minimap. */
    public static boolean drawFullMap(Object renderer,Object graphics,Object waypoint,boolean hovered,float scale,double x,double y) {
        if(!XaeroBridge.isOwnedWaypoint(waypoint))return false;
        try {
            Object settings=renderer.getClass().getMethod("getContext").invoke(renderer);
            float size=settings.getClass().getField("worldmapWaypointsScale").getFloat(settings)*scale;
            var matrices=(MatrixStack)graphics.getClass().getMethod("pose").invoke(graphics);
            var buffers=(VertexConsumerProvider.Immediate)graphics.getClass().getMethod("getBufferSource").invoke(graphics);
            var client=MinecraftClient.getInstance(); var state=appleState();
            matrices.push();
            try {
                matrices.translate(x,y,0); matrices.scale(size,size,1);
                // Match the existing pin's clickable area: width 30, y=-43..0.
                matrices.push();
                try { drawItem(matrices,buffers,state,0,-20,32); }
                finally { matrices.pop(); }
                if(hovered) {
                    matrices.push();
                    try {
                        matrices.scale(1.2f,1.2f,1);
                        graphics.getClass().getMethod("drawCenteredString",net.minecraft.client.font.TextRenderer.class,String.class,int.class,int.class,int.class)
                            .invoke(graphics,client.textRenderer,XaeroBridge.MARKER_NAME,0,-39,0xffffe6a3);
                    } finally { matrices.pop(); }
                }
            } finally { matrices.pop(); }
            return true;
        } catch(ReflectiveOperationException|LinkageError|RuntimeException e) { report(e); return false; }
    }
    private static ItemRenderState appleState() {
        var client=MinecraftClient.getInstance();
        var state=new ItemRenderState();
        client.getItemModelManager().clearAndUpdate(state,Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack(),
            ItemDisplayContext.GUI,client.world,client.player,0);
        return state;
    }
    private static void drawItem(MatrixStack matrices,VertexConsumerProvider.Immediate buffers,ItemRenderState state,float x,float y,float size) {
        matrices.translate(x,y,0); matrices.scale(size,-size,size);
        // Flush Xaero geometry before switching lights, then restore the exact previous UBO.
        buffers.draw();
        var previousLights=RenderSystem.getShaderLights();
        try {
            MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_FLAT);
            state.render(matrices,new ImmediateItems(buffers),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,0);
            buffers.draw();
        } finally { RenderSystem.setShaderLights(previousLights); }
    }
    private static boolean reported;
    private static void report(Throwable error) {
        if(!reported) {
            reported=true;
            org.slf4j.LoggerFactory.getLogger("AncientAppleHunter").warn("Xaero apple icon rendering failed; using its original icon",error);
        }
    }
    public static boolean drawGui(DrawContext context,Object waypoint,int x,int y,int opacity) {
        if(!XaeroBridge.isOwnedWaypoint(waypoint))return false;
        if(opacity>0)context.drawItem(Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack(),x-8,y-8);
        return true;
    }
    /** Render the resolved vanilla model, including native glint, into Xaero's buffers. */
    private static final class ImmediateItems extends OrderedRenderCommandQueueImpl {
        private final VertexConsumerProvider buffers;
        ImmediateItems(VertexConsumerProvider buffers) { this.buffers=buffers; }
        @Override public void submitItem(MatrixStack matrices,ItemDisplayContext display,int light,int overlay,int outline,
            int[] tints,List<BakedQuad> quads,RenderLayer layer,ItemRenderState.Glint glint) {
            ItemRenderer.renderItem(display,matrices,buffers,light,overlay,tints,quads,layer,glint);
        }
    }
}
