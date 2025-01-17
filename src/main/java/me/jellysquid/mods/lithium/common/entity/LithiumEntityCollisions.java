package me.jellysquid.mods.lithium.common.entity;

import me.jellysquid.mods.lithium.common.entity.movement.BlockCollisionPredicate;
import me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import me.jellysquid.mods.lithium.common.util.Producer;
import me.jellysquid.mods.lithium.common.world.WorldHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.ICollisionReader;
import net.minecraft.world.IEntityReader;
import net.minecraft.world.border.WorldBorder;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class LithiumEntityCollisions {
    public static final double EPSILON = 1.0E-7D;

    /**
     * [VanillaCopy] CollisionView#getBlockCollisions(Entity, Box)
     * This is a much, much faster implementation which uses simple collision testing against full-cube block shapes.
     * Checks against the world border are replaced with our own optimized functions which do not go through the
     * VoxelShape system.
     * <p>
     * The {@link BlockCollisionPredicate} can be used to filter which blocks will be considered for collision testing
     * during iteration.
     */
    public static Stream<VoxelShape> getBlockCollisions(ICollisionReader world, Entity entity, AxisAlignedBB box, BlockCollisionPredicate predicate) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        final ChunkAwareBlockCollisionSweeper sweeper = new ChunkAwareBlockCollisionSweeper(world, entity, box, predicate);

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<VoxelShape>(Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE) {
            @Override
            public boolean tryAdvance(Consumer<? super VoxelShape> consumer) {
                VoxelShape shape = sweeper.getNextCollidedShape();

                if (shape != null) {
                    consumer.accept(shape);

                    return true;
                }

                return false;
            }
        }, false);
    }

    /**
     * See {@link LithiumEntityCollisions#getBlockCollisions(ICollisionReader, Entity, AxisAlignedBB, BlockCollisionPredicate)}
     *
     * @return True if the box (possibly that of an entity's) collided with any blocks
     */
    public static boolean doesBoxCollideWithBlocks(ICollisionReader world, Entity entity, AxisAlignedBB box, BlockCollisionPredicate predicate) {
        if (isBoxEmpty(box)) {
            return false;
        }

        final ChunkAwareBlockCollisionSweeper sweeper = new ChunkAwareBlockCollisionSweeper(world, entity, box, predicate);
        final VoxelShape shape = sweeper.getNextCollidedShape();

        return shape != null;
    }

    /**
     * See {@link LithiumEntityCollisions#getEntityCollisions(IEntityReader, Entity, AxisAlignedBB, Predicate)}
     *
     * @return True if the box (possibly that of an entity's) collided with any other entities
     */
    public static boolean doesBoxCollideWithEntities(IEntityReader view, Entity entity, AxisAlignedBB box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return false;
        }

        return getEntityCollisionProducer(view, entity, box.inflate(EPSILON), predicate).computeNext(null);
    }

    /**
     * Returns a stream of entity collision boxes.
     */
    public static Stream<VoxelShape> getEntityCollisions(IEntityReader view, Entity entity, AxisAlignedBB box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        return Producer.asStream(getEntityCollisionProducer(view, entity, box.inflate(EPSILON), predicate));
    }

    /**
     * [VanillaCopy] EntityView#getEntityCollisions
     * Re-implements the function named above without stream code or unnecessary allocations. This can provide a small
     * boost in some situations (such as heavy entity crowding) and reduces the allocation rate significantly.
     */
    public static Producer<VoxelShape> getEntityCollisionProducer(IEntityReader view, Entity entity, AxisAlignedBB box, Predicate<Entity> predicate) {
        return new Producer<VoxelShape>() {
            private Iterator<Entity> it;

            @Override
            public boolean computeNext(Consumer<? super VoxelShape> consumer) {
                if (this.it == null) {
                    /*
                     * In case entity's class is overriding method_30949, all types of entities may be (=> are assumed to be) required.
                     * Otherwise only get entities that override method_30948 are required, as other entities cannot collide.
                     */
                    this.it = WorldHelper.getEntitiesWithCollisionBoxForEntity(view, box, entity).iterator();
                }

                while (this.it.hasNext()) {
                    Entity otherEntity = this.it.next();

                    if (!predicate.test(otherEntity)) {
                        continue;
                    }

                    /*
                     * {@link Entity#method_30948} returns false by default, designed to be overridden by
                     * entities whose collisions should be "hard" (boats and shulkers, for now).
                     *
                     * {@link Entity#method_30949} only allows hard collisions if the calling entity is not riding
                     * otherEntity as a vehicle.
                     */
                    if (entity == null) {
                        if (!otherEntity.canBeCollidedWith()) {
                            continue;
                        }
                    } else if (!entity.canCollideWith(otherEntity)) {
                        continue;
                    }

                    if (consumer != null) {
                        consumer.accept(VoxelShapes.create(otherEntity.getBoundingBox()));
                    }
                    return true;
                }

                return false;
            }
        };
    }

    /**
     * This provides a faster check for seeing if an entity is within the world border as it avoids going through
     * the slower shape system.
     *
     * @return True if the {@param box} is fully within the {@param border}, otherwise false.
     */
    public static boolean isWithinWorldBorder(WorldBorder border, AxisAlignedBB box) {
        double wboxMinX = Math.floor(border.getMinX());
        double wboxMinZ = Math.floor(border.getMinZ());

        double wboxMaxX = Math.ceil(border.getMaxX());
        double wboxMaxZ = Math.ceil(border.getMaxZ());

        return box.minX >= wboxMinX && box.minX < wboxMaxX && box.minZ >= wboxMinZ && box.minZ < wboxMaxZ &&
                box.maxX >= wboxMinX && box.maxX < wboxMaxX && box.maxZ >= wboxMinZ && box.maxZ < wboxMaxZ;
    }

    public static boolean canEntityCollideWithWorldBorder(ICollisionReader world, Entity entity) {
        WorldBorder border = world.getWorldBorder();

        boolean isInsideBorder = isWithinWorldBorder(border, entity.getBoundingBox().deflate(EPSILON));
        boolean isCrossingBorder = isWithinWorldBorder(border, entity.getBoundingBox().inflate(EPSILON));

        return !isInsideBorder && isCrossingBorder;
    }

    private static boolean isBoxEmpty(AxisAlignedBB box) {
        return box.getSize() <= EPSILON;
    }
}
