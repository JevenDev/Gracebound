package com.jvn.gracebound.client.compat.antiqueatlas;

import com.jvn.gracebound.Gracebound;
import com.jvn.gracebound.guidance.GuidanceTarget;
import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.util.ToucanIds;
import com.jvn.toucanlib.util.ToucanResourceLocations;
import com.mojang.math.Axis;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class AntiqueAtlasCompatIntegration {
    private static final String ATLAS_RENDERER_CLASS_NAME = "folk.sisby.antique_atlas.gui.AtlasRenderer";
    private static final String ATLAS_OVERLAY_CLASS_NAME = "folk.sisby.antique_atlas.gui.AtlasOverlay";
    private static final String ATLAS_DRAW_BATCHER_CLASS_NAME = "folk.sisby.antique_atlas.util.DrawBatcher";

    private static final ToucanIds IDS = ToucanIds.create(Gracebound.MOD_ID);
    private static final ResourceLocation GUIDANCE_TEXTURE = IDS.id("map/gracebound_map_guidance.png");
    private static final String OVERLAY_ID_PATH = "guidance_overlay";

    private static final int TEXTURE_WIDTH = 100;
    private static final int TEXTURE_HEIGHT = 293;
    private static final int ANCHOR_X = TEXTURE_WIDTH / 2;
    private static final int ANCHOR_Y = TEXTURE_HEIGHT - 1;

    private static final int MAP_BORDER_WIDTH = 17;
    private static final int MAP_BORDER_HEIGHT = 11;

    private static final float ATLAS_MARKER_BASE_SCALE = 0.1F;
    private static final double TEXTURE_FORWARD_DEGREES = -90.0D;
    private static final double DESTINATION_RADIUS_BLOCKS = 3.0D;
    private static final double DESTINATION_FADE_BAND_BLOCKS = 2.0D;
    private static final float MIN_DESTINATION_ALPHA = 0.0F;
    private static final float GUIDANCE_TRANSITION_SPEED = 3.5F;
    private static final float MIN_RENDER_ALPHA = 0.01F;
    private static final float HELD_MODE_DEPTH_BIAS = -0.01F;

    private static GuidanceMarker activeMarker;
    private static GuidanceMarker pendingMarker;
    private static float markerVisibilityAlpha;
    private static long lastTransitionTickNanos;

    private static boolean overlayRegistrationAttempted;
    private static boolean overlayRegistered;
    private static boolean overlayRenderDisabled;

    private static Class<?> atlasIdentifierClass;
    private static Object guidanceTextureIdentifier;
    private static Method drawSingleMethod;

    private AntiqueAtlasCompatIntegration() {
    }

    static void clientTick(Player player, Optional<GuidanceTarget> target) {
        tryRegisterOverlay();
        updateTransitionState(resolveMarker(player, target).orElse(null));
    }

    static void clear() {
        activeMarker = null;
        pendingMarker = null;
        markerVisibilityAlpha = 0.0F;
        lastTransitionTickNanos = 0L;
    }

    private static void tryRegisterOverlay() {
        if (overlayRegistered || overlayRegistrationAttempted) {
            return;
        }
        overlayRegistrationAttempted = true;

        try {
            Class<?> atlasRendererClass = Class.forName(ATLAS_RENDERER_CLASS_NAME);
            Class<?> atlasOverlayClass = Class.forName(ATLAS_OVERLAY_CLASS_NAME);
            Method registerOverlayMethod = Arrays.stream(atlasRendererClass.getMethods())
                    .filter(method -> method.getName().equals("registerOverlay"))
                    .filter(method -> method.getParameterCount() == 2)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("registerOverlay"));

            atlasIdentifierClass = registerOverlayMethod.getParameterTypes()[0];
            Object overlayId = createIdentifier(atlasIdentifierClass, Gracebound.MOD_ID, OVERLAY_ID_PATH);
            Object overlay = Proxy.newProxyInstance(
                    atlasOverlayClass.getClassLoader(),
                    new Class<?>[]{atlasOverlayClass},
                    new OverlayInvocationHandler()
            );

            registerOverlayMethod.invoke(null, overlayId, overlay);
            overlayRegistered = true;
            Gracebound.LOGGER.info("Gracebound Antique Atlas guidance overlay registered.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            overlayRenderDisabled = true;
            Gracebound.LOGGER.warn("Gracebound Antique Atlas compat disabled due to API mismatch.", exception);
        }
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
                playerPos.z,
                computeWorldDirectionDegrees(playerPos, targetPos),
                horizontalDistanceToTarget,
                guidanceKey,
                player.level().dimension().location().toString()
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

    private static float computeAlpha(GuidanceMarker marker) {
        float destinationAlpha = computeDestinationAlpha((float) marker.horizontalDistanceToTarget());
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
        float smooth = ToucanEasing.smoothstep(t);
        return Mth.lerp(smooth, MIN_DESTINATION_ALPHA, 1.0F);
    }

    private static float worldRotationToTextureRotation(float directionDegrees) {
        return Mth.wrapDegrees((float) (directionDegrees - TEXTURE_FORWARD_DEGREES));
    }

    private static void renderOverlay(Object renderer, Object matrices, Object vertexConsumers, int light, float markerScale) {
        if (overlayRenderDisabled || !overlayRegistered || activeMarker == null || markerVisibilityAlpha <= MIN_RENDER_ALPHA) {
            return;
        }

        GuidanceMarker marker = activeMarker;
        float alpha = computeAlpha(marker);
        if (alpha <= MIN_RENDER_ALPHA) {
            return;
        }

        try {
            String rendererDimensionId = extractDimensionId(invokeAny(renderer, new String[]{"dim"}));
            if (rendererDimensionId != null && !rendererDimensionId.equals(marker.dimensionId())) {
                return;
            }

            double markerX = ((Number) invokeAny(renderer, new String[]{"worldXToScreenX"}, marker.playerX())).doubleValue()
                    - ((Number) invokeAny(renderer, new String[]{"bookX"})).doubleValue();
            double markerY = ((Number) invokeAny(renderer, new String[]{"worldZToScreenY"}, marker.playerZ())).doubleValue()
                    - ((Number) invokeAny(renderer, new String[]{"bookY"})).doubleValue();

            int mapWidth = ((Number) invokeAny(renderer, new String[]{"mapWidth"})).intValue();
            int mapHeight = ((Number) invokeAny(renderer, new String[]{"mapHeight"})).intValue();
            markerX = Mth.clamp(markerX, MAP_BORDER_WIDTH, mapWidth + MAP_BORDER_WIDTH);
            markerY = Mth.clamp(markerY, MAP_BORDER_HEIGHT, mapHeight + MAP_BORDER_HEIGHT);

            float visualScale = resolveVisualScale(markerScale);
            if (!Float.isFinite(visualScale) || visualScale <= 0.0F) {
                return;
            }

            float rotationDegrees = worldRotationToTextureRotation(marker.worldDirectionDegrees());
            drawGuidanceTexture(matrices, vertexConsumers, markerX, markerY, visualScale, rotationDegrees, alpha, light);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            overlayRenderDisabled = true;
            Gracebound.LOGGER.warn("Gracebound Antique Atlas compat disabled due to render API mismatch.", exception);
        }
    }

    private static float resolveVisualScale(float markerScale) {
        return markerScale * ATLAS_MARKER_BASE_SCALE;
    }

    private static void drawGuidanceTexture(
            Object matrices,
            Object vertexConsumers,
            double x,
            double y,
            float scale,
            float rotationDegrees,
            float alpha,
            int light
    ) throws ReflectiveOperationException {
        ensureDrawSingleReady();

        invokeAny(matrices, new String[]{"pushPose", "method_22903", "push"});
        try {
            invokeAny(matrices, new String[]{"translate", "method_22904", "method_46416"}, x, y, 0.0D);
            invokeAny(matrices, new String[]{"scale", "method_22905"}, scale, scale, 1.0F);
            invokeAny(matrices, new String[]{"mulPose", "method_22907", "multiply"}, Axis.ZP.rotationDegrees(rotationDegrees));

            int argb = ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | 0x00FFFFFF;
            float drawDepth = vertexConsumers == null ? 0.0F : HELD_MODE_DEPTH_BIAS;
            drawSingleMethod.invoke(
                    null,
                    matrices,
                    vertexConsumers,
                    guidanceTextureIdentifier,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    light,
                    -ANCHOR_X,
                    -ANCHOR_Y,
                    drawDepth,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    0,
                    0,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    argb,
                    true
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ReflectiveOperationException("Failed to draw Antique Atlas overlay.", cause);
        } finally {
            invokeAny(matrices, new String[]{"popPose", "method_22909", "pop"});
        }
    }

    private static void ensureDrawSingleReady() throws ReflectiveOperationException {
        if (drawSingleMethod == null) {
            Class<?> drawBatcherClass = Class.forName(ATLAS_DRAW_BATCHER_CLASS_NAME);
            drawSingleMethod = Arrays.stream(drawBatcherClass.getMethods())
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(method -> method.getName().equals("drawSingle"))
                    .filter(method -> method.getParameterCount() == 17)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("DrawBatcher.drawSingle"));
            atlasIdentifierClass = drawSingleMethod.getParameterTypes()[2];
        }

        if (guidanceTextureIdentifier == null || !atlasIdentifierClass.isInstance(guidanceTextureIdentifier)) {
            guidanceTextureIdentifier = createIdentifier(
                    atlasIdentifierClass,
                    GUIDANCE_TEXTURE.getNamespace(),
                    GUIDANCE_TEXTURE.getPath()
            );
        }
    }

    private static Object createIdentifier(Class<?> identifierClass, String namespace, String path)
            throws ReflectiveOperationException {
        if (identifierClass.getName().equals(ResourceLocation.class.getName())) {
            return ToucanResourceLocations.id(namespace, path);
        }

        Object twoArg = invokeStaticStringFactory(identifierClass, new String[]{
                "fromNamespaceAndPath",
                "of",
                "method_60655"
        }, namespace, path);
        if (twoArg != null) {
            return twoArg;
        }

        try {
            return identifierClass.getConstructor(String.class, String.class).newInstance(namespace, path);
        } catch (ReflectiveOperationException ignored) {
        }

        String id = namespace + ":" + path;
        Object oneArg = invokeStaticStringFactory(identifierClass, new String[]{
                "tryParse",
                "parse",
                "of",
                "method_60654"
        }, id);
        if (oneArg != null) {
            return oneArg;
        }

        try {
            return identifierClass.getConstructor(String.class).newInstance(id);
        } catch (ReflectiveOperationException ignored) {
        }

        throw new NoSuchMethodException("Could not create identifier for class " + identifierClass.getName());
    }

    private static Object invokeStaticStringFactory(Class<?> owner, String[] names, Object... args)
            throws ReflectiveOperationException {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals(name)) {
                    continue;
                }
                if (!owner.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                if (!areArgumentsCompatible(method.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    return method.invoke(null, args);
                } catch (IllegalAccessException ignored) {
                } catch (InvocationTargetException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                }
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, String[] names, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target, names, args);
        if (method == null) {
            throw new NoSuchMethodException(
                    "No matching method for " + target.getClass().getName() + " names=" + Arrays.toString(names)
            );
        }

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ReflectiveOperationException(cause);
        }
    }

    private static Method findMethod(Object target, String[] names, Object... args) {
        Set<Method> candidates = new LinkedHashSet<>();
        candidates.addAll(Arrays.asList(target.getClass().getMethods()));
        candidates.addAll(Arrays.asList(target.getClass().getDeclaredMethods()));

        for (String name : names) {
            for (Method candidate : candidates) {
                if (!candidate.getName().equals(name)) {
                    continue;
                }
                if (candidate.getParameterCount() != args.length) {
                    continue;
                }
                if (!areArgumentsCompatible(candidate.getParameterTypes(), args)) {
                    continue;
                }
                candidate.setAccessible(true);
                return candidate;
            }
        }
        return null;
    }

    private static boolean areArgumentsCompatible(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Object argument = args[i];
            if (argument == null) {
                if (parameterType.isPrimitive()) {
                    return false;
                }
                continue;
            }

            Class<?> boxedParameterType = boxPrimitive(parameterType);
            if (!boxedParameterType.isInstance(argument)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static String extractDimensionId(Object dimensionKey) {
        if (dimensionKey == null) {
            return null;
        }

        try {
            Object location = invokeAny(dimensionKey, new String[]{"location", "getValue"});
            if (location != null) {
                return location.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private record GuidanceMarker(
            double playerX,
            double playerZ,
            float worldDirectionDegrees,
            double horizontalDistanceToTarget,
            long guidanceKey,
            String dimensionId
    ) {
    }

    private static final class OverlayInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("onScreenRender".equals(methodName) && args != null && args.length == 1) {
                Object screenContext = args[0];
                Object renderer = invokeAny(screenContext, new String[]{"screen"});
                Object drawContext = invokeAny(screenContext, new String[]{"context"});
                Object matrices = invokeAny(drawContext, new String[]{"getMatrices", "pose", "method_51448"});
                int light = 0x00F000F0;
                float markerScale = ((Number) invokeAny(screenContext, new String[]{"markerScale"})).floatValue();
                renderOverlay(renderer, matrices, null, light, markerScale);
                return null;
            }

            if ("onRender".equals(methodName) && args != null && args.length == 1) {
                Object renderContext = args[0];
                Object renderer = invokeAny(renderContext, new String[]{"renderer"});
                Object matrices = invokeAny(renderContext, new String[]{"matrices"});
                Object vertexConsumers = invokeAny(renderContext, new String[]{"vertexConsumers"});
                int light = ((Number) invokeAny(renderContext, new String[]{"light"})).intValue();
                float markerScale = ((Number) invokeAny(renderContext, new String[]{"markerScale"})).floatValue();
                renderOverlay(renderer, matrices, vertexConsumers, light, markerScale);
                return null;
            }

            if (method.getDeclaringClass() == Object.class) {
                return switch (methodName) {
                    case "toString" -> "GraceboundAtlasOverlay";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> null;
                };
            }

            return null;
        }
    }
}
