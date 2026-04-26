package com.jvn.gracebound.client.compat.xaero;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;
import xaero.common.HudMod;
import xaero.common.IXaeroMinimap;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.element.render.MinimapElementReader;
import xaero.common.minimap.element.render.MinimapElementRenderLocation;
import xaero.common.minimap.element.render.MinimapElementRenderProvider;
import xaero.common.minimap.element.render.MinimapElementRenderer;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler;
import xaero.map.WorldMap;
import xaero.map.element.MapElementRenderHandler;
import xaero.map.mods.minimap.element.MinimapElementRendererWrapper;

final class XaeroCompatIntegration {
    private static final ResourceLocation GUIDANCE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Gracebound.MOD_ID,
            "map/gracebound_map_guidance.png"
    );
    private static final int TEXTURE_WIDTH = 117;
    private static final int TEXTURE_HEIGHT = 110;
    // Anchor the back/near end tip at the player so the trail projects outward toward grace.
    private static final int ANCHOR_X = TEXTURE_WIDTH - 1;
    private static final int ANCHOR_Y = 0;
    private static final int BOX_LEFT = -ANCHOR_X;
    private static final int BOX_RIGHT = BOX_LEFT + TEXTURE_WIDTH;
    private static final int BOX_TOP = -ANCHOR_Y;
    private static final int BOX_BOTTOM = BOX_TOP + TEXTURE_HEIGHT;
    private static final double TEXTURE_FORWARD_DEGREES = 135.0D; // Art forward points toward bottom-left.
    private static final float MAX_ZOOM_OUT_SCALE = 3.0F;

    private static final GuidanceElementRenderer RENDERER = new GuidanceElementRenderer();

    private static GuidanceMarker activeMarker;
    private static boolean minimapRendererRegistered;
    private static boolean worldMapRendererRegistered;

    private XaeroCompatIntegration() {
    }

    static void clientTick(Player player, Optional<GuidanceTarget> target) {
        tryRegisterRenderers();
        activeMarker = resolveMarker(player, target).orElse(null);
    }

    static void clear() {
        activeMarker = null;
    }

    private static void tryRegisterRenderers() {
        if (!minimapRendererRegistered) {
            registerMinimapRenderer();
        }
        if (!worldMapRendererRegistered) {
            registerWorldMapRenderer();
        }
    }

    private static void registerMinimapRenderer() {
        HudMod hudMod = HudMod.INSTANCE;
        if (hudMod == null) {
            return;
        }

        Minimap minimap = hudMod.getMinimap();
        if (minimap == null) {
            return;
        }

        MinimapElementOverMapRendererHandler overMapHandler = minimap.getOverMapRendererHandler();
        if (overMapHandler == null) {
            return;
        }

        overMapHandler.add(RENDERER);
        minimapRendererRegistered = true;
        Gracebound.LOGGER.info("Gracebound Xaero minimap guidance marker renderer registered.");
    }

    private static void registerWorldMapRenderer() {
        if (!minimapRendererRegistered) {
            return;
        }

        MapElementRenderHandler worldMapHandler = WorldMap.mapElementRenderHandler;
        if (worldMapHandler == null) {
            return;
        }

        IXaeroMinimap modMain = HudMod.INSTANCE;
        if (modMain == null) {
            return;
        }

        MinimapElementRendererWrapper<GuidanceMarker, Void> wrappedRenderer = MinimapElementRendererWrapper.Builder
                .<GuidanceMarker, Void>begin(RENDERER)
                .setModMain(modMain)
                .setShouldRenderSupplier(XaeroCompatIntegration::hasActiveMarker)
                .setOrder(RENDERER.getOrder())
                .build();
        worldMapHandler.add(wrappedRenderer);
        worldMapRendererRegistered = true;
        Gracebound.LOGGER.info("Gracebound Xaero world map guidance marker renderer registered.");
    }

    private static Optional<GuidanceMarker> resolveMarker(Player player, Optional<GuidanceTarget> target) {
        if (target.isEmpty()) {
            return Optional.empty();
        }

        GuidanceTarget guidanceTarget = target.get();
        if (!guidanceTarget.pos().dimension().equals(player.level().dimension())) {
            return Optional.empty();
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = Vec3.atCenterOf(guidanceTarget.pos().pos());
        return Optional.of(new GuidanceMarker(
                playerPos.x,
                playerPos.y,
                playerPos.z,
                computeRotationDegrees(playerPos, targetPos)
        ));
    }

    private static float computeRotationDegrees(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0F;
        }

        double desiredDegrees = Math.toDegrees(Math.atan2(dz, dx));
        return Mth.wrapDegrees((float) (desiredDegrees - TEXTURE_FORWARD_DEGREES));
    }

    private static boolean hasActiveMarker() {
        return activeMarker != null;
    }

    private record GuidanceMarker(double x, double y, double z, float rotationDegrees) {
    }

    private static final class GuidanceElementProvider extends MinimapElementRenderProvider<GuidanceMarker, Void> {
        private boolean consumed;

        @Override
        public void begin(int location, Void context) {
            consumed = false;
        }

        @Override
        public boolean hasNext(int location, Void context) {
            return !consumed && activeMarker != null;
        }

        @Override
        public GuidanceMarker getNext(int location, Void context) {
            consumed = true;
            return activeMarker;
        }

        @Override
        public void end(int location, Void context) {
        }
    }

    private static final class GuidanceElementReader extends MinimapElementReader<GuidanceMarker, Void> {
        @Override
        public boolean isHidden(GuidanceMarker element, Void context) {
            return false;
        }

        @Override
        public double getRenderX(GuidanceMarker element, Void context, float partialTicks) {
            return element.x();
        }

        @Override
        public double getRenderY(GuidanceMarker element, Void context, float partialTicks) {
            return element.y();
        }

        @Override
        public double getRenderZ(GuidanceMarker element, Void context, float partialTicks) {
            return element.z();
        }

        @Override
        public int getInteractionBoxLeft(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_LEFT;
        }

        @Override
        public int getInteractionBoxRight(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_RIGHT;
        }

        @Override
        public int getInteractionBoxTop(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_TOP;
        }

        @Override
        public int getInteractionBoxBottom(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_BOTTOM;
        }

        @Override
        public int getRenderBoxLeft(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_LEFT;
        }

        @Override
        public int getRenderBoxRight(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_RIGHT;
        }

        @Override
        public int getRenderBoxTop(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_TOP;
        }

        @Override
        public int getRenderBoxBottom(GuidanceMarker element, Void context, float partialTicks) {
            return BOX_BOTTOM;
        }

        @Override
        public int getLeftSideLength(GuidanceMarker element, net.minecraft.client.Minecraft minecraft) {
            return 0;
        }

        @Override
        public String getMenuName(GuidanceMarker element) {
            return "Grace Guidance";
        }

        @Override
        public String getFilterName(GuidanceMarker element) {
            return "Gracebound";
        }

        @Override
        public int getMenuTextFillLeftPadding(GuidanceMarker element) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(GuidanceMarker element) {
            return 0x66000000;
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return true;
        }

        @Override
        public boolean isInteractable(int location, GuidanceMarker element) {
            return false;
        }
    }

    private static final class GuidanceElementRenderer extends MinimapElementRenderer<GuidanceMarker, Void> {
        private static Field guiMapScaleField;
        private static boolean guiMapScaleLookupAttempted;

        private GuidanceElementRenderer() {
            super(new GuidanceElementReader(), new GuidanceElementProvider(), null);
        }

        @Override
        public boolean renderElement(
                int location,
                boolean alwaysHighlighted,
                boolean edgeClamped,
                GuiGraphics guiGraphics,
                MultiBufferSource.BufferSource bufferSource,
                Font font,
                RenderTarget framebuffer,
                MinimapRendererHelper minimapRendererHelper,
                Entity renderEntity,
                Player player,
                double renderX,
                double renderY,
                double renderZ,
                int yLevel,
                double depth,
                float optionalScale,
                GuidanceMarker element,
                double partialTranslateX,
                double partialTranslateY,
                boolean cave,
                float partialTicks
        ) {
            if (element == null) {
                return false;
            }

            var poseStack = guiGraphics.pose();
            float visualScale = optionalScale;
            if (isWorldMapLocation(location)) {
                visualScale = capWorldMapZoomOutScale(optionalScale);
            }

            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, depth);
            poseStack.scale(visualScale, visualScale, 1.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(element.rotationDegrees()));

            RenderSystem.setShaderTexture(0, GUIDANCE_TEXTURE);
            RenderSystem.texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            RenderSystem.texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.blit(
                    GUIDANCE_TEXTURE,
                    BOX_LEFT,
                    BOX_TOP,
                    0,
                    0,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT
            );
            RenderSystem.disableBlend();

            poseStack.popPose();
            return true;
        }

        private static boolean isWorldMapLocation(int location) {
            return location == MinimapElementRenderLocation.WORLD_MAP
                    || location == MinimapElementRenderLocation.WORLD_MAP_MENU;
        }

        private static float capWorldMapZoomOutScale(float optionalScale) {
            double mapScale = readCurrentGuiMapScale();
            if (!Double.isFinite(mapScale) || mapScale <= 0.0D) {
                return optionalScale;
            }

            float cappedLocalScale = (float) Math.min(optionalScale, MAX_ZOOM_OUT_SCALE * mapScale);
            return Math.max(0.0F, cappedLocalScale);
        }

        private static double readCurrentGuiMapScale() {
            Minecraft minecraft = Minecraft.getInstance();
            Object currentScreen = minecraft.screen;
            if (currentScreen == null) {
                return Double.NaN;
            }

            Field scaleField = getGuiMapScaleField();
            if (scaleField == null) {
                return Double.NaN;
            }

            try {
                if (!scaleField.getDeclaringClass().isInstance(currentScreen)) {
                    return Double.NaN;
                }
                return scaleField.getDouble(currentScreen);
            } catch (IllegalAccessException exception) {
                return Double.NaN;
            }
        }

        private static Field getGuiMapScaleField() {
            if (guiMapScaleField != null) {
                return guiMapScaleField;
            }
            if (guiMapScaleLookupAttempted) {
                return null;
            }

            guiMapScaleLookupAttempted = true;
            try {
                Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");
                Field scaleField = guiMapClass.getDeclaredField("scale");
                scaleField.setAccessible(true);
                guiMapScaleField = scaleField;
                return scaleField;
            } catch (ReflectiveOperationException exception) {
                Gracebound.LOGGER.debug("Gracebound Xaero compat could not access world map zoom scale field.", exception);
                return null;
            }
        }

        @Override
        public void preRender(
                int location,
                Entity renderEntity,
                Player player,
                double renderX,
                double renderY,
                double renderZ,
                IXaeroMinimap modMain,
                MultiBufferSource.BufferSource bufferSource,
                MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers
        ) {
        }

        @Override
        public void postRender(
                int location,
                Entity renderEntity,
                Player player,
                double renderX,
                double renderY,
                double renderZ,
                IXaeroMinimap modMain,
                MultiBufferSource.BufferSource bufferSource,
                MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers
        ) {
        }

        @Override
        public boolean shouldRender(int location) {
            if (!hasActiveMarker()) {
                return false;
            }

            return location == MinimapElementRenderLocation.OVER_MINIMAP
                    || location == MinimapElementRenderLocation.WORLD_MAP
                    || location == MinimapElementRenderLocation.WORLD_MAP_MENU;
        }

        @Override
        public int getOrder() {
            return 220;
        }
    }
}
