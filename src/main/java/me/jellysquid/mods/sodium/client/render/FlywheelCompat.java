package me.jellysquid.mods.sodium.client.render;

import java.util.Collection;

import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.tileentity.TileEntity;

public class FlywheelCompat {

    /**
     * Filters a collection of TileEntities to avoid rendering conflicts with Flywheel.
     *
     * @param blockEntities The collection to be filtered.
     */
    public static void filterBlockEntityList(Collection<TileEntity> blockEntities) {
        if (SodiumClientMod.flywheelLoaded) {
            InstancedRenderRegistry r = InstancedRenderRegistry.getInstance();
            blockEntities.removeIf(r::shouldSkipRender);
        }
    }

}
