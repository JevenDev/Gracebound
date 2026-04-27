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
    private static final int TEXTURE_WIDTH = 100;
    private static final int TEXTURE_HEIGHT = 193;
    // Anchor at the lower center so the trail rises outward from the player.
    private static final int ANCHOR_X = TEXTURE_WIDTH / 2;
    private static final int ANCHOR_Y = TEXTURE_HEIGHT - 1;
    private static final int BOX_LEFT = -ANCHOR_X;
    private static final int BOX_RIGHT = BOX_LEFT + TEXTURE_WIDTH;
    private static final int BOX_TOP = -ANCHOR_Y;
    private static final int BOX_BOTTOM = BOX_TOP + TEXTURE_HEIGHT;
    private static final double TEXTURE_FORWARD_DEGREES = -90.0D; // Art forward points straight up.
    private static final double DESTINATION_RADIUS_BLOCKS = 3.0D;
    private static final double DESTINATION_FADE_BAND_BLOCKS = 2.0D;
    private static final float MIN_DESTINATION_ALPHA = 0.0F;
    private static final float GUIDANCE_TRANSITION_SPEED = 3.5F;
    private static final float MIN_RENDER_ALPHA = 0.01F;

    private static final float MAX_WORLD_MAP_ZOOM_OUT_SCALE = 5.0F;
    private static final float MINIMAP_BASE_SCALE_FACTOR = 0.30F;
    private static final float MINIMAP_MIN_SCALE = 0.14F;
    private static final float MINIMAP_FALLBACK_MAX_SCALE = 0.60F;
    private static final float MINIMAP_MAX_HALF_VIEW_FRACTION = 0.70F;

    private static final GuidanceElementRenderer RENDERER = new GuidanceElementRenderer();

    private static GuidanceMarker activeMarker;
    private static GuidanceMarker pendingMarker;
    private static float markerVisibilityAlpha;
    private static long lastTransitionTickNanos;
    private static boolean minimapRendererRegistered;
    private static boolean worldMapRendererRegistered;

    private XaeroCompatIntegration() {
    }

    static void clientTick(Player player, Optional<GuidanceTarget> target) {
        tryRegisterRenderers();
        updateTransitionState(resolveMarker(player, target).orElse(null));
    }

    static void clear() {
        activeMarker = null;
        pendingMarker = null;
        markerVisibilityAlpha = 0.0F;
        lastTransitionTickNanos = 0L;
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
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double horizontalDistanceToTarget = Math.sqrt(dx * dx + dz * dz);
        long guidanceKey = guidanceTarget.pos().pos().asLong();
        return Optional.of(new GuidanceMarker(
                playerPos.x,
                playerPos.y,
                playerPos.z,
                computeWorldDirectionDegrees(playerPos, targetPos),
                horizontalDistanceToTarget,
                guidanceKey
        ));
    }

    private static void updateTransitionState(GuidanceMarker resolvedMarker) {
        float deltaSeconds = getTransitionDeltaSeconds();
        float alphaStep = GUIDANCE_TRANSITION_SPEED * deltaSeconds;

        if (resolvedMarker == null) {
            pendingMarker = null;
            markerVisibilityAlpha = approach(markerVisibilityAlpha, 0.0F, alphaStep);
            if (markerVisibilityAlpha <= MIN_RENDER_ALPHA) {
                markerVisibilityAlpha = 0.0F;
                activeMarker = null;
            }
            return;
        }

        if (activeMarker == null) {
            activeMarker = resolvedMarker;
            pendingMarker = null;
            markerVisibilityAlpha = approach(markerVisibilityAlpha, 1.0F, alphaStep);
            return;
        }

        if (activeMarker.guidanceKey() != resolvedMarker.guidanceKey()) {
            pendingMarker = resolvedMarker;
            markerVisibilityAlpha = approach(markerVisibilityAlpha, 0.0F, alphaStep);
            if (markerVisibilityAlpha <= MIN_RENDER_ALPHA) {
                markerVisibilityAlpha = 0.0F;
                activeMarker = pendingMarker;
                pendingMarker = null;
            }
            return;
        }

        activeMarker = resolvedMarker;
        pendingMarker = null;
        markerVisibilityAlpha = approach(markerVisibilityAlpha, 1.0F, alphaStep);
    }

    private static float getTransitionDeltaSeconds() {
        long now = System.nanoTime();
        if (lastTransitionTickNanos == 0L) {
            lastTransitionTickNanos = now;
            return 0.05F;
        }

        long deltaNanos = now - lastTransitionTickNanos;
        lastTransitionTickNanos = now;
        if (deltaNanos <= 0L) {
            return 0.0F;
        }

        float deltaSeconds = deltaNanos / 1_000_000_000.0F;
        return Mth.clamp(deltaSeconds, 0.0F, 0.25F);
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }

    private static float computeWorldDirectionDegrees(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0F;
        }

        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)));
    }

    private static boolean hasActiveMarker() {
        return activeMarker != null && markerVisibilityAlpha > MIN_RENDER_ALPHA;
    }

    private record GuidanceMarker(
            double x,
            double y,
            double z,
            float worldDirectionDegrees,
            double horizontalDistanceToTarget,
            long guidanceKey
    ) {
    }

    private static final class GuidanceElementProvider extends MinimapElementRenderProvider<GuidanceMarker, Void> {
        private boolean consumed;

        @Override
        public void begin(int location, Void context) {
            consumed = false;
        }

        @Override
        public boolean hasNext(int location, Void context) {
            return !consumed && hasActiveMarker();
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

        private static Class<?> overMapHandlerClass;
        private static Field overMapPsField;
        private static Field overMapPcField;
        private static Field overMapHalfViewWField;
        private static Field overMapHalfViewHField;
        private static boolean overMapStateLookupAttempted;

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
            float visualScale = computeVisualScale(location, optionalScale);
            float rotationDegrees = computeRotationDegrees(location, element);
            float alpha = computeAlpha(element);
            if (alpha <= MIN_RENDER_ALPHA) {
                return true;
            }

            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, depth);
            poseStack.scale(visualScale, visualScale, 1.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotationDegrees));

            applyTextureFiltering(location);
            RenderSystem.enableBlend();
            // Respect PNG transparency/feathered edges on the guidance texture.
            RenderSystem.blendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
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
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();

            poseStack.popPose();
            return true;
        }

        private static float computeAlpha(GuidanceMarker element) {
            float destinationAlpha = computeDestinationAlpha((float) element.horizontalDistanceToTarget());
            return Mth.clamp(markerVisibilityAlpha * destinationAlpha, 0.0F, 1.0F);
        }

        private static float computeDestinationAlpha(float distanceToTarget) {
            float destinationRadius = (float) DESTINATION_RADIUS_BLOCKS;
            float fadeBand = (float) DESTINATION_FADE_BAND_BLOCKS;
            float fadeEndDistance = destinationRadius + fadeBand;
            if (distanceToTarget >= fadeEndDistance) {
                return 1.0F;
            }
            if (distanceToTarget <= destinationRadius) {
                return MIN_DESTINATION_ALPHA;
            }

            float t = (distanceToTarget - destinationRadius) / fadeBand;
            float smooth = t * t * (3.0F - 2.0F * t);
            return Mth.lerp(smooth, MIN_DESTINATION_ALPHA, 1.0F);
        }

        private static float computeVisualScale(int location, float optionalScale) {
            if (isWorldMapLocation(location)) {
                return capWorldMapZoomOutScale(optionalScale);
            }
            if (isMinimapLocation(location)) {
                return capMinimapScale(optionalScale);
            }
            return optionalScale;
        }

        private static float capMinimapScale(float optionalScale) {
            float scaled = optionalScale * MINIMAP_BASE_SCALE_FACTOR;
            float maxScale = MINIMAP_FALLBACK_MAX_SCALE;

            MinimapOverMapState minimapState = readMinimapOverMapState();
            if (minimapState != null) {
                int minHalfView = Math.min(minimapState.halfViewW(), minimapState.halfViewH());
                if (minHalfView > 0) {
                    float boundedScale = (float) ((minHalfView * MINIMAP_MAX_HALF_VIEW_FRACTION)
                            / Math.max(TEXTURE_WIDTH, TEXTURE_HEIGHT));
                    if (Float.isFinite(boundedScale) && boundedScale > 0.0F) {
                        maxScale = boundedScale;
                    }
                }
            }

            maxScale = Math.max(maxScale, MINIMAP_MIN_SCALE);
            return Mth.clamp(scaled, MINIMAP_MIN_SCALE, maxScale);
        }

        private static boolean isMinimapLocation(int location) {
            return location == MinimapElementRenderLocation.OVER_MINIMAP
                    || location == MinimapElementRenderLocation.IN_MINIMAP;
        }

        private static boolean isWorldMapLocation(int location) {
            return location == MinimapElementRenderLocation.WORLD_MAP
                    || location == MinimapElementRenderLocation.WORLD_MAP_MENU;
        }

        private static float computeRotationDegrees(int location, GuidanceMarker element) {
            if (!isMinimapLocation(location)) {
                return worldRotationToTextureRotation(element.worldDirectionDegrees());
            }

            MinimapOverMapState minimapState = readMinimapOverMapState();
            if (minimapState == null) {
                return worldRotationToTextureRotation(element.worldDirectionDegrees());
            }

            double worldRadians = Math.toRadians(element.worldDirectionDegrees());
            double dx = Math.cos(worldRadians);
            double dz = Math.sin(worldRadians);

            // Match Xaero's OVER_MINIMAP translation basis so our arrow points where an offset marker would render.
            double screenX = minimapState.ps() * dx - minimapState.pc() * dz;
            double screenY = minimapState.pc() * dx + minimapState.ps() * dz;
            if (screenX * screenX + screenY * screenY < 1.0E-8D) {
                return worldRotationToTextureRotation(element.worldDirectionDegrees());
            }

            float minimapDirectionDegrees = (float) Math.toDegrees(Math.atan2(screenY, screenX));
            return worldRotationToTextureRotation(minimapDirectionDegrees);
        }

        private static float worldRotationToTextureRotation(float directionDegrees) {
            return Mth.wrapDegrees((float) (directionDegrees - TEXTURE_FORWARD_DEGREES));
        }

        private static void applyTextureFiltering(int location) {
            RenderSystem.setShaderTexture(0, GUIDANCE_TEXTURE);
            int filter = isMinimapLocation(location) ? GL11C.GL_NEAREST : GL11C.GL_LINEAR;
            RenderSystem.texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, filter);
            RenderSystem.texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, filter);
        }

        private static float capWorldMapZoomOutScale(float optionalScale) {
            double mapScale = readCurrentGuiMapScale();
            if (!Double.isFinite(mapScale) || mapScale <= 0.0D) {
                return optionalScale;
            }

            float cappedLocalScale = (float) Math.min(optionalScale, MAX_WORLD_MAP_ZOOM_OUT_SCALE * mapScale);
            return Math.max(0.0F, cappedLocalScale);
        }

        private static MinimapOverMapState readMinimapOverMapState() {
            HudMod hudMod = HudMod.INSTANCE;
            if (hudMod == null) {
                return null;
            }

            Minimap minimap = hudMod.getMinimap();
            if (minimap == null) {
                return null;
            }

            Object overMapHandler = minimap.getOverMapRendererHandler();
            if (overMapHandler == null || !prepareOverMapStateFields(overMapHandler.getClass())) {
                return null;
            }

            try {
                return new MinimapOverMapState(
                        overMapPsField.getDouble(overMapHandler),
                        overMapPcField.getDouble(overMapHandler),
                        overMapHalfViewWField.getInt(overMapHandler),
                        overMapHalfViewHField.getInt(overMapHandler)
                );
            } catch (IllegalAccessException exception) {
                return null;
            }
        }

        private static boolean prepareOverMapStateFields(Class<?> handlerClass) {
            if (handlerClass.equals(overMapHandlerClass)
                    && overMapPsField != null
                    && overMapPcField != null
                    && overMapHalfViewWField != null
                    && overMapHalfViewHField != null) {
                return true;
            }
            if (handlerClass.equals(overMapHandlerClass) && overMapStateLookupAttempted) {
                return false;
            }

            overMapHandlerClass = handlerClass;
            overMapStateLookupAttempted = true;
            try {
                overMapPsField = findFieldRecursive(handlerClass, "ps");
                overMapPcField = findFieldRecursive(handlerClass, "pc");
                overMapHalfViewWField = findFieldRecursive(handlerClass, "halfViewW");
                overMapHalfViewHField = findFieldRecursive(handlerClass, "halfViewH");
                return true;
            } catch (ReflectiveOperationException exception) {
                Gracebound.LOGGER.debug("Gracebound Xaero compat could not access minimap over-map render state.", exception);
                overMapPsField = null;
                overMapPcField = null;
                overMapHalfViewWField = null;
                overMapHalfViewHField = null;
                return false;
            }
        }

        private static Field findFieldRecursive(Class<?> startClass, String fieldName)
                throws ReflectiveOperationException {
            Class<?> cursor = startClass;
            while (cursor != null) {
                try {
                    Field field = cursor.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    cursor = cursor.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
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

        private record MinimapOverMapState(double ps, double pc, int halfViewW, int halfViewH) {
        }
    }
}
