package me.jellysquid.mods.sodium.client.render.pipeline.context;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.cache.HashLightDataCache;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.RenderContextCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockDisplayReader;

import java.util.Map;

public class GlobalRenderContext {
    private static final Map<IBlockDisplayReader, GlobalRenderContext> INSTANCES = new Reference2ObjectOpenHashMap<>();

    private final BlockRenderer blockRenderer;
    private final HashLightDataCache lightCache;

    private GlobalRenderContext(IBlockDisplayReader world) {
        Minecraft client = Minecraft.getInstance();

        this.lightCache = new HashLightDataCache(world);

        BiomeColorBlender biomeColorBlender = RenderContextCommon.createBiomeColorBlender();
        LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(this.lightCache);

        this.blockRenderer = new BlockRenderer(client, lightPipelineProvider, biomeColorBlender);
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    private void resetCache() {
        this.lightCache.clearCache();
    }

    public static GlobalRenderContext getInstance(IBlockDisplayReader world) {
        GlobalRenderContext instance = INSTANCES.get(world);

        if (instance == null) {
            throw new IllegalStateException("No global renderer exists");
        }

        return instance;
    }

    public static void destroyRenderContext(IBlockDisplayReader world) {
        if (INSTANCES.remove(world) == null) {
            throw new IllegalStateException("No render context exists for world: " + world);
        }
    }

    public static void createRenderContext(IBlockDisplayReader world) {
        if (INSTANCES.containsKey(world)) {
            throw new IllegalStateException("Render context already exists for world: " + world);
        }

        INSTANCES.put(world, new GlobalRenderContext(world));
    }

    public static void resetCaches() {
        for (GlobalRenderContext context : INSTANCES.values()) {
            context.resetCache();
        }
    }
}
