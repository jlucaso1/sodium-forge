package me.jellysquid.mods.sodium.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfo;
import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfoAccess;
import me.jellysquid.mods.phosphor.common.chunk.level.LevelUpdateListener;
import me.jellysquid.mods.phosphor.common.chunk.light.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.lighting.LevelBasedGraph;
import net.minecraft.world.lighting.LightDataMap;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SectionLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.BitSet;

@Mixin(LightEngine.class)
public abstract class MixinChunkLightProvider<M extends LightDataMap<M>, S extends SectionLightStorage<M>>
        extends LevelBasedGraph implements LightProviderUpdateTracker, LightProviderBlockAccess, LightInitializer, LevelUpdateListener, InitialLightingAccess {
    private static final BlockState DEFAULT_STATE = Blocks.AIR.defaultBlockState();
    private static final ChunkSection[] EMPTY_SECTION_ARRAY = new ChunkSection[16];

    @Shadow
    @Final
    protected BlockPos.Mutable pos;

    @Shadow
    @Final
    protected IChunkLightProvider chunkSource;

    private final long[] cachedChunkPos = new long[2];
    private final ChunkSection[][] cachedChunkSections = new ChunkSection[2][];

    private final Long2ObjectOpenHashMap<BitSet> buckets = new Long2ObjectOpenHashMap<>();

    private long prevChunkBucketKey = ChunkPos.INVALID_CHUNK_POS;
    private BitSet prevChunkBucketSet;

    protected MixinChunkLightProvider(int levelCount, int expectedLevelSize, int expectedTotalSize) {
        super(levelCount, expectedLevelSize, expectedTotalSize);
    }

    @Inject(method = "clearCache", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.cachedChunkPos != null) {
            Arrays.fill(this.cachedChunkPos, ChunkPos.INVALID_CHUNK_POS);
            Arrays.fill(this.cachedChunkSections, null);
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        if (World.isOutsideBuildHeight(y)) {
            return DEFAULT_STATE;
        }

        final long chunkPos = ChunkPos.asLong(x >> 4, z >> 4);

        for (int i = 0; i < 2; i++) {
            if (this.cachedChunkPos[i] == chunkPos) {
                return this.getBlockStateFromSection(this.cachedChunkSections[i], x, y, z);
            }
        }

        return this.getBlockStateForLightingUncached(x, y, z);
    }

    private BlockState getBlockStateForLightingUncached(int x, int y, int z) {
        return this.getBlockStateFromSection(this.getAndCacheChunkSections(x >> 4, z >> 4), x, y, z);
    }

    private BlockState getBlockStateFromSection(ChunkSection[] sections, int x, int y, int z) {
        ChunkSection section = sections[y >> 4];

        if (section != null) {
            return section.getBlockState(x & 15, y & 15, z & 15);
        }

        return DEFAULT_STATE;
    }

    private ChunkSection[] getAndCacheChunkSections(int x, int z) {
        final IChunk chunk = (IChunk) this.chunkSource.getChunkForLighting(x, z);
        final ChunkSection[] sections = chunk != null ? chunk.getSections() : EMPTY_SECTION_ARRAY;

        final ChunkSection[][] cachedSections = this.cachedChunkSections;
        cachedSections[1] = cachedSections[0];
        cachedSections[0] = sections;

        final long[] cachedCoords = this.cachedChunkPos;
        cachedCoords[1] = cachedCoords[0];
        cachedCoords[0] = ChunkPos.asLong(x, z);

        return sections;
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        BlockStateLightInfo info = ((BlockStateLightInfoAccess) state).getLightInfo();

        if (info != null) {
            return info.getLightSubtracted();
        } else {
            return this.getSubtractedLightFallback(state, x, y, z);
        }
    }

    private int getSubtractedLightFallback(BlockState state, int x, int y, int z) {
        return state.getBlock().getLightBlock(state, this.chunkSource.getLevel(), this.pos.set(x, y, z));
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir) {
        if (state != null && state.useShapeForLightOcclusion()) {
            BlockStateLightInfo info = ((BlockStateLightInfoAccess) state).getLightInfo();

            if (info != null) {
                VoxelShape[] extrudedFaces = info.getExtrudedFaces();

                if (extrudedFaces != null) {
                    return extrudedFaces[dir.ordinal()];
                }
            } else {
                return this.getOpaqueShapeFallback(state, x, y, z, dir);
            }
        }

        return VoxelShapes.empty();
    }

    private VoxelShape getOpaqueShapeFallback(BlockState state, int x, int y, int z, Direction dir) {
        return VoxelShapes.getFaceShape(state.getOcclusionShape(this.chunkSource.getLevel(), this.pos.set(x, y, z)), dir);
    }

    @Override
    public void spreadLightInto(long a, long b) {
        this.checkEdge(a, b, this.computeLevelFromNeighbor(a, b, this.getLevel(a)), false);
        this.checkEdge(b, a, this.computeLevelFromNeighbor(b, a, this.getLevel(b)), false);
    }

    /**
     * The vanilla implementation for removing pending light updates requires iterating over either every queued light
     * update (<8K checks) or every block position within a sub-chunk (16^3 checks). This is painfully slow and results
     * in a tremendous amount of CPU time being spent here when chunks are unloaded on the client and server.
     *
     * To work around this, we maintain a bit-field of queued updates by chunk position so we can simply select every
     * light update within a section without excessive iteration. The bit-field only requires 64 bytes of memory per
     * section with queued updates, and does not require expensive hashing in order to track updates within it. In order
     * to avoid as much overhead as possible when looking up a bit-field for a given chunk section, the previous lookup
     * is cached and used where possible. The integer key for each bucket can be computed by performing a simple bit
     * mask over the already-encoded block position value.
     */
    @Override
    public void cancelUpdatesForChunk(long sectionPos) {
        long key = getBucketKeyForSection(sectionPos);
        BitSet bits = this.removeChunkBucket(key);

        if (bits != null && !bits.isEmpty()) {
            int startX = SectionPos.x(sectionPos) << 4;
            int startY = SectionPos.y(sectionPos) << 4;
            int startZ = SectionPos.z(sectionPos) << 4;

            for (int i = bits.nextSetBit(0); i != -1; i = bits.nextSetBit(i + 1)) {
                int x = (i >> 8) & 15;
                int y = (i >> 4) & 15;
                int z = i & 15;

                this.removeFromQueue(BlockPos.asLong(startX + x, startY + y, startZ + z));
            }
        }
    }

    @Override
    public void onPendingUpdateRemoved(long blockPos) {
        long key = getBucketKeyForBlock(blockPos);

        BitSet bits;

        if (this.prevChunkBucketKey == key) {
            bits = this.prevChunkBucketSet;
        } else {
            bits = this.buckets.get(key);

            if (bits == null) {
                return;
            }
        }

        bits.clear(getLocalIndex(blockPos));

        if (bits.isEmpty()) {
            this.removeChunkBucket(key);
        }
    }

    @Override
    public void onPendingUpdateAdded(long blockPos) {
        long key = getBucketKeyForBlock(blockPos);

        BitSet bits;

        if (this.prevChunkBucketKey == key) {
            bits = this.prevChunkBucketSet;
        } else {
            bits = this.buckets.get(key);

            if (bits == null) {
                this.buckets.put(key, bits = new BitSet(16 * 16 * 16));
            }

            this.prevChunkBucketKey = key;
            this.prevChunkBucketSet = bits;
        }

        bits.set(getLocalIndex(blockPos));
    }

    // Used to mask a long-encoded block position into a bucket key by dropping the first 4 bits of each component
    private static final long BLOCK_TO_BUCKET_KEY_MASK = ~BlockPos.asLong(15, 15, 15);

    private long getBucketKeyForBlock(long blockPos) {
        return blockPos & BLOCK_TO_BUCKET_KEY_MASK;
    }

    private long getBucketKeyForSection(long sectionPos) {
        return BlockPos.asLong(SectionPos.x(sectionPos) << 4, SectionPos.y(sectionPos) << 4, SectionPos.z(sectionPos) << 4);
    }

    private BitSet removeChunkBucket(long key) {
        BitSet set = this.buckets.remove(key);

        if (this.prevChunkBucketSet == set) {
            this.prevChunkBucketKey = ChunkPos.INVALID_CHUNK_POS;
            this.prevChunkBucketSet = null;
        }

        return set;
    }

    // Finds the bit-flag index of a local position within a chunk section
    private static int getLocalIndex(long blockPos) {
        int x = BlockPos.getX(blockPos) & 15;
        int y = BlockPos.getY(blockPos) & 15;
        int z = BlockPos.getZ(blockPos) & 15;

        return (x << 8) | (y << 4) | z;
    }

    @Shadow
    @Final
    protected SectionLightStorage<?> storage;

    /**
     * @author PhiPro
     * @reason Re-implement completely. Change specification of the method.
     * Now controls both source light and light updates. Disabling now additionally removes all data associated to the chunk.
     */
    @Overwrite
    public void enableLightSources(final ChunkPos pos, final boolean enabled) {
        final long chunkPos = SectionPos.getZeroNode(SectionPos.asLong(pos.x, 0, pos.z));
        final LightStorageAccess lightStorage = (LightStorageAccess) this.storage;

        if (enabled) {
            lightStorage.invokeSetColumnEnabled(chunkPos, true);
            lightStorage.setLightUpdatesEnabled(chunkPos, true);
        } else {
            lightStorage.setLightUpdatesEnabled(chunkPos, false);
        }
    }

    @Override
    public void enableSourceLight(final long chunkPos) {
        ((LightStorageAccess) this.storage).invokeSetColumnEnabled(chunkPos, true);
    }

    @Override
    public void enableLightUpdates(final long chunkPos) {
        ((LightStorageAccess) this.storage).setLightUpdatesEnabled(chunkPos, true);
    }
}
