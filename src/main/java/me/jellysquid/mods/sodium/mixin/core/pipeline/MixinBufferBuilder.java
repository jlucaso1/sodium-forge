package me.jellysquid.mods.sodium.mixin.core.pipeline;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.VertexType;
import me.jellysquid.mods.sodium.client.model.vertex.VertexTypeBlittable;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.util.UnsafeUtil;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder implements VertexBufferView, VertexDrain {
    @Shadow
    private int nextElementBytes;

    @Shadow
    private ByteBuffer byteBuffer;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private static int roundUpPositive(int amount) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private VertexFormat vertexFormat;

    @Shadow
    private int vertexCount;

    @Override
    public boolean ensureBufferCapacity(int bytes) {
        if (this.nextElementBytes + bytes <= this.byteBuffer.capacity()) {
            return false;
        }

        int newSize = this.byteBuffer.capacity() + roundUpPositive(bytes);

        LOGGER.debug("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", this.byteBuffer.capacity(), newSize);

        this.byteBuffer.position(0);

        ByteBuffer byteBuffer = GLAllocation.createDirectByteBuffer(newSize);
        byteBuffer.put(this.byteBuffer);
        byteBuffer.rewind();

        this.byteBuffer = byteBuffer;

        return true;
    }

    @Override
    public ByteBuffer getDirectBuffer() {
        return this.byteBuffer;
    }

    @Override
    public int getWriterPosition() {
        return this.nextElementBytes;
    }

    @Override
    public VertexFormat getVertexFormat() {
        return this.vertexFormat;
    }

    @Override
    public void flush(int vertexCount, VertexFormat format) {
        if (this.vertexFormat != format) {
            throw new IllegalStateException("Mis-matched vertex format (expected: [" + format + "], currently using: [" + this.vertexFormat + "])");
        }

        this.vertexCount += vertexCount;
        this.nextElementBytes += vertexCount * format.getSize();
    }

    @Override
    public <T extends VertexSink> T createSink(VertexType<T> factory) {
        VertexTypeBlittable<T> blittable = factory.asBlittable();

        if (blittable != null && blittable.getBufferVertexFormat() == this.vertexFormat)  {
            return blittable.createBufferWriter(this, UnsafeUtil.isAvailable());
        }

        return factory.createFallbackWriter((IVertexBuilder) this);
    }
}
