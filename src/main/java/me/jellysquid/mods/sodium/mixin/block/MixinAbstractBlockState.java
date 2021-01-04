package me.jellysquid.mods.sodium.mixin.block;

import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfoAccess;
import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfo;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixinAbstractBlockState implements BlockStateLightInfoAccess {
    @Shadow
    protected AbstractBlock.AbstractBlockState.Cache cache;

    @SuppressWarnings("ConstantConditions")
    @Override
    public BlockStateLightInfo getLightInfo() {
        return (BlockStateLightInfo) (Object) this.cache;
    }
}
