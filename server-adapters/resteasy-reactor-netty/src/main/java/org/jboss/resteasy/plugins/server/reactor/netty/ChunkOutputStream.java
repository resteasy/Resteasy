package org.jboss.resteasy.plugins.server.reactor.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ByteProcessor;
import org.jboss.resteasy.spi.AsyncOutputStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * This is the output stream leveraged by {@link
 * ReactorNettyHttpResponse#getOutputStream}.  It provides the heavy lifting
 * for actually transfering the bytes written by RestEasy to a {@link
 * Flux<byte[]>}, which is what reactor-netty works with.  Most of the heavy
 * lifting occurs in {@link #asyncWrite(byte[], int, int)}.
 */
class ChunkOutputStream extends AsyncOutputStream {

   private static final EmitFailureHandler EMIT_FAILURE_HANDLER = EmitFailureHandler.FAIL_FAST;

   private final ReactorNettyHttpResponse parentResponse;

   /**
    * This is the {@link Mono} that we return from
    * {@link ReactorNettyJaxrsServer.Handler#handle(HttpServerRequest, HttpServerResponse)}
    */
   private final Sinks.Empty<Void> completionSink;

   /**
    * Indicates that we've starting sending the response bytes.
    */
   private volatile boolean started;

   /**
    * This is ultimately think 'sink' that we write bytes to in
    * {@link #asyncWrite(byte[], int, int)}.
    */
   private Sinks.Many<Tuple2<byte[], CompletableFuture<Void>>> byteSink;

   /**
    * This is used to establish {@link #byteSink} upon the first writing of bytes.
    */
   private final Supplier<Sinks.Many<Tuple2<byte[], CompletableFuture<Void>>>> byteSinkSupplier;

   ChunkOutputStream(
       final ReactorNettyHttpResponse parentResponse,
       final HttpServerResponse reactorNettyResponse,
       final Sinks.Empty<Void> completionSink
   ) {
       this.parentResponse = Objects.requireNonNull(parentResponse);
       this.completionSink = Objects.requireNonNull(completionSink);
       Objects.requireNonNull(reactorNettyResponse);

       this.byteSinkSupplier = () -> {
           final Sinks.Many<Tuple2<byte[], CompletableFuture<Void>>> outSink =
               Sinks.many().unicast().onBackpressureBuffer();

           final Flux<ByteBuf> byteFlux =
               outSink.asFlux()
                   .map(tup -> new WrappedByteBuf(Unpooled.wrappedBuffer(tup.getT1()), tup.getT2()));

           SinkSubscriber.subscribe(completionSink, Mono.from(reactorNettyResponse.send(byteFlux)));
           return outSink;
       };
   }

   @Override
   public void write(int b) {
       write(new byte[] {(byte)b}, 0, 1);
   }

   @Override
   public void close() throws IOException {
       if (!started || byteSink == null) {
           SinkSubscriber.subscribe(completionSink, Mono.empty());
       } else {
           byteSink.emitComplete(EMIT_FAILURE_HANDLER);
       }
   }

   @Override
   public void write(byte[] bs, int off, int len) {
       try {
           asyncWrite(bs, off, len).get();
       } catch (final InterruptedException ie) {
           Thread.currentThread().interrupt();
           throw new RuntimeException(ie);
       } catch (final ExecutionException ee) {
           throw new RuntimeException(ee);
       }
   }

   @Override
   public void flush() {
       try {
           asyncFlush().get();
       } catch (final InterruptedException ie) {
           Thread.currentThread().interrupt();
           throw new RuntimeException(ie);
       } catch (final ExecutionException ee) {
           throw new RuntimeException(ee);
       }
   }

   @Override
   public CompletableFuture<Void> asyncFlush() {

       // [AG] Discuss with @crankydillo.  Here is my understanding:
       //   - asyncFlush is used mainly for SSE.
       //   - The idea seems to be to send the element immediately without
       //     waiting for the rest of the elements.  Anyway, that's what SSE is.
       //   - But, at the same time, it is trying to not overload.  So, kind of
       //     doing backpressure.  Only if the previous flush is complete,
       //     then request the next element from the app.
       //   - The backpressure mechanism is already built into Reactor Netty.  But,
       //     the question is how to communicate that.
       //   - Please see https://projectreactor.io/docs/netty/release/reference/index.html#_sse.
       //     It reads `The flushing strategy is "flush after every element" emitted
       //     by the provided Publisher`.  So, we do not need to call flush individually.
       //
       //   In summary, we are not controlling flushing, instead Reactor Netty does.
       //   Also, this#asyncWrite will manage the backpressure.  I assume Reactor Netty
       //   will backpressure if it cannot flush, and that would mean an element won't be
       //   requested so asyncWrite won't complete until the element is pulled.  Also,
       //   asyncFlush is called right after an asyncWrite.  So, asyncFlush looks
       //   useless.

       return CompletableFuture.completedFuture(null);
   }

   @Override
   public CompletableFuture<Void> asyncWrite(final byte[] bs, int offset, int length) {
        final CompletableFuture<Void> cf = new CompletableFuture<>();
        if (!started) {
            byteSink = byteSinkSupplier.get();
            parentResponse.committed();
            started = true;
        }

        byte[] bytes = bs;
        if (offset != 0 || length != bs.length) {
            bytes = Arrays.copyOfRange(bs, offset, offset + length);
        }
        byteSink.emitNext(Tuples.of(bytes, cf), EMIT_FAILURE_HANDLER);
        return cf;
   }

    /**
     * The ultimate purpose of this wrapper is to complete the {@link CompletableFuture} returned
     * from {@link #asyncWrite(byte[], int, int)}.  It achieves this with by using the assumption
     * that reactor-netty will call one of the ByteBuf release methods (e.g. {@link ByteBuf#release()}.
     */
    private static class WrappedByteBuf extends ByteBuf {
        private final CompletableFuture<Void> cf;
        private final ByteBuf delegate;

        public WrappedByteBuf(final ByteBuf delegate, final CompletableFuture<Void> cf) {
            this.cf = cf;
            this.delegate = delegate;
        }

        @Override
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            return new WrappedByteBuf(delegate.capacity(newCapacity), cf);
        }

        @Override
        public int maxCapacity() {
            return delegate.maxCapacity();
        }

        @Override
        public ByteBufAllocator alloc() {
            // TODO technically this should be implemented..
            return delegate.alloc();
        }

        @Override
        @Deprecated
        public ByteOrder order() {
            return delegate.order();
        }

        @Override
        @Deprecated
        public ByteBuf order(ByteOrder endianness) {
            return new WrappedByteBuf(delegate.order(endianness), cf);
        }

        @Override
        public ByteBuf unwrap() {
            return new WrappedByteBuf(delegate.unwrap(), cf);
        }

        @Override
        public boolean isDirect() {
            return delegate.isDirect();
        }

        @Override
        public boolean isReadOnly() {
            return delegate.isReadOnly();
        }

        @Override
        public ByteBuf asReadOnly() {
            return new WrappedByteBuf(delegate.asReadOnly(), cf);
        }

        @Override
        public int readerIndex() {
            return delegate.readerIndex();
        }

        @Override
        public ByteBuf readerIndex(int readerIndex) {
            return new WrappedByteBuf(delegate.readerIndex(readerIndex), cf);
        }

        @Override
        public int writerIndex() {
            return delegate.writerIndex();
        }

        @Override
        public ByteBuf writerIndex(int writerIndex) {
            return new WrappedByteBuf(delegate.writerIndex(writerIndex), cf);
        }

        @Override
        public ByteBuf setIndex(int readerIndex, int writerIndex) {
            return new WrappedByteBuf(delegate.setIndex(readerIndex, writerIndex), cf);
        }

        @Override
        public int readableBytes() {
            return delegate.readableBytes();
        }

        @Override
        public int writableBytes() {
            return delegate.writableBytes();
        }

        @Override
        public int maxWritableBytes() {
            return delegate.maxWritableBytes();
        }

        @Override
        public int maxFastWritableBytes() {
            return delegate.maxFastWritableBytes();
        }

        @Override
        public boolean isReadable() {
            return delegate.isReadable();
        }

        @Override
        public boolean isReadable(int size) {
            return delegate.isReadable(size);
        }

        @Override
        public boolean isWritable() {
            return delegate.isWritable();
        }

        @Override
        public boolean isWritable(int size) {
            return delegate.isWritable(size);
        }

        @Override
        public ByteBuf clear() {
            return new WrappedByteBuf(delegate.clear(), cf);
        }

        @Override
        public ByteBuf markReaderIndex() {
            return new WrappedByteBuf(delegate.markReaderIndex(), cf);
        }

        @Override
        public ByteBuf resetReaderIndex() {
            return new WrappedByteBuf(delegate.resetReaderIndex(), cf);
        }

        @Override
        public ByteBuf markWriterIndex() {
            return new WrappedByteBuf(delegate.markWriterIndex(), cf);
        }

        @Override
        public ByteBuf resetWriterIndex() {
            return new WrappedByteBuf(delegate.resetWriterIndex(), cf);
        }

        @Override
        public ByteBuf discardReadBytes() {
            return new WrappedByteBuf(delegate.discardReadBytes(), cf);
        }

        @Override
        public ByteBuf discardSomeReadBytes() {
            return new WrappedByteBuf(delegate.discardSomeReadBytes(), cf);
        }

        @Override
        public ByteBuf ensureWritable(int minWritableBytes) {
            return new WrappedByteBuf(delegate.ensureWritable(minWritableBytes), cf);
        }

        @Override
        public int ensureWritable(int minWritableBytes, boolean force) {
            return delegate.ensureWritable(minWritableBytes, force);
        }

        @Override
        public boolean getBoolean(int index) {
            return delegate.getBoolean(index);
        }

        @Override
        public byte getByte(int index) {
            return delegate.getByte(index);
        }

        @Override
        public short getUnsignedByte(int index) {
            return delegate.getUnsignedByte(index);
        }

        @Override
        public short getShort(int index) {
            return delegate.getShort(index);
        }

        @Override
        public short getShortLE(int index) {
            return delegate.getShortLE(index);
        }

        @Override
        public int getUnsignedShort(int index) {
            return delegate.getUnsignedShort(index);
        }

        @Override
        public int getUnsignedShortLE(int index) {
            return delegate.getUnsignedShortLE(index);
        }

        @Override
        public int getMedium(int index) {
            return delegate.getMedium(index);
        }

        @Override
        public int getMediumLE(int index) {
            return delegate.getMediumLE(index);
        }

        @Override
        public int getUnsignedMedium(int index) {
            return delegate.getUnsignedMedium(index);
        }

        @Override
        public int getUnsignedMediumLE(int index) {
            return delegate.getUnsignedMediumLE(index);
        }

        @Override
        public int getInt(int index) {
            return delegate.getInt(index);
        }

        @Override
        public int getIntLE(int index) {
            return delegate.getIntLE(index);
        }

        @Override
        public long getUnsignedInt(int index) {
            return delegate.getUnsignedInt(index);
        }

        @Override
        public long getUnsignedIntLE(int index) {
            return delegate.getUnsignedIntLE(index);
        }

        @Override
        public long getLong(int index) {
            return delegate.getLong(index);
        }

        @Override
        public long getLongLE(int index) {
            return delegate.getLongLE(index);
        }

        @Override
        public char getChar(int index) {
            return delegate.getChar(index);
        }

        @Override
        public float getFloat(int index) {
            return delegate.getFloat(index);
        }

        @Override
        public float getFloatLE(int index) {
            return delegate.getFloatLE(index);
        }

        @Override
        public double getDouble(int index) {
            return delegate.getDouble(index);
        }

        @Override
        public double getDoubleLE(int index) {
            return delegate.getDoubleLE(index);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst) {
            return new WrappedByteBuf(delegate.getBytes(index, dst), cf);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int length) {
            return new WrappedByteBuf(delegate.getBytes(index, dst, length), cf);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            return new WrappedByteBuf(delegate.getBytes(index, dst, dstIndex, length), cf);
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst) {
            return new WrappedByteBuf(delegate.getBytes(index, dst), cf);
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            return new WrappedByteBuf(delegate.getBytes(index, dst, dstIndex, length), cf);
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            return new WrappedByteBuf(delegate.getBytes(index, dst), cf);
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
            return new WrappedByteBuf(delegate.getBytes(index, out, length), cf);
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return delegate.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            return delegate.getBytes(index, out, position, length);
        }

        @Override
        public CharSequence getCharSequence(int index, int length, Charset charset) {
            return delegate.getCharSequence(index, length, charset);
        }

        @Override
        public ByteBuf setBoolean(int index, boolean value) {
            return new WrappedByteBuf(delegate.setBoolean(index, value), cf);
        }

        @Override
        public ByteBuf setByte(int index, int value) {
            return new WrappedByteBuf(delegate.setByte(index, value), cf);
        }

        @Override
        public ByteBuf setShort(int index, int value) {
            return new WrappedByteBuf(delegate.setShort(index, value), cf);
        }

        @Override
        public ByteBuf setShortLE(int index, int value) {
            return new WrappedByteBuf(delegate.setShortLE(index, value), cf);
        }

        @Override
        public ByteBuf setMedium(int index, int value) {
            return new WrappedByteBuf(delegate.setMedium(index, value), cf);
        }

        @Override
        public ByteBuf setMediumLE(int index, int value) {
            return new WrappedByteBuf(delegate.setMediumLE(index, value), cf);
        }

        @Override
        public ByteBuf setInt(int index, int value) {
            return new WrappedByteBuf(delegate.setInt(index, value), cf);
        }

        @Override
        public ByteBuf setIntLE(int index, int value) {
            return new WrappedByteBuf(delegate.setIntLE(index, value), cf);
        }

        @Override
        public ByteBuf setLong(int index, long value) {
            return new WrappedByteBuf(delegate.setLong(index, value), cf);
        }

        @Override
        public ByteBuf setLongLE(int index, long value) {
            return new WrappedByteBuf(delegate.setLongLE(index, value), cf);
        }

        @Override
        public ByteBuf setChar(int index, int value) {
            return new WrappedByteBuf(delegate.setChar(index, value), cf);
        }

        @Override
        public ByteBuf setFloat(int index, float value) {
            return new WrappedByteBuf(delegate.setFloat(index, value), cf);
        }

        @Override
        public ByteBuf setFloatLE(int index, float value) {
            return new WrappedByteBuf(delegate.setFloatLE(index, value), cf);
        }

        @Override
        public ByteBuf setDouble(int index, double value) {
            return new WrappedByteBuf(delegate.setDouble(index, value), cf);
        }

        @Override
        public ByteBuf setDoubleLE(int index, double value) {
            return new WrappedByteBuf(delegate.setDoubleLE(index, value), cf);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src) {
            return new WrappedByteBuf(delegate.setBytes(index, src), cf);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int length) {
            return new WrappedByteBuf(delegate.setBytes(index, src, length), cf);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            return new WrappedByteBuf(delegate.setBytes(index, src, srcIndex, length), cf);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src) {
            return new WrappedByteBuf(delegate.setBytes(index, src), cf);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            return new WrappedByteBuf(delegate.setBytes(index, src, srcIndex, length), cf);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            return new WrappedByteBuf(delegate.setBytes(index, src), cf);
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return delegate.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return delegate.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            return delegate.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf setZero(int index, int length) {
            return new WrappedByteBuf(delegate.setZero(index, length), cf);
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            return delegate.setCharSequence(index, sequence, charset);
        }

        @Override
        public boolean readBoolean() {
            return delegate.readBoolean();
        }

        @Override
        public byte readByte() {
            return delegate.readByte();
        }

        @Override
        public short readUnsignedByte() {
            return delegate.readUnsignedByte();
        }

        @Override
        public short readShort() {
            return delegate.readShort();
        }

        @Override
        public short readShortLE() {
            return delegate.readShortLE();
        }

        @Override
        public int readUnsignedShort() {
            return delegate.readUnsignedShort();
        }

        @Override
        public int readUnsignedShortLE() {
            return delegate.readUnsignedShortLE();
        }

        @Override
        public int readMedium() {
            return delegate.readMedium();
        }

        @Override
        public int readMediumLE() {
            return delegate.readMediumLE();
        }

        @Override
        public int readUnsignedMedium() {
            return delegate.readUnsignedMedium();
        }

        @Override
        public int readUnsignedMediumLE() {
            return delegate.readUnsignedMediumLE();
        }

        @Override
        public int readInt() {
            return delegate.readInt();
        }

        @Override
        public int readIntLE() {
            return delegate.readIntLE();
        }

        @Override
        public long readUnsignedInt() {
            return delegate.readUnsignedInt();
        }

        @Override
        public long readUnsignedIntLE() {
            return delegate.readUnsignedIntLE();
        }

        @Override
        public long readLong() {
            return delegate.readLong();
        }

        @Override
        public long readLongLE() {
            return delegate.readLongLE();
        }

        @Override
        public char readChar() {
            return delegate.readChar();
        }

        @Override
        public float readFloat() {
            return delegate.readFloat();
        }

        @Override
        public float readFloatLE() {
            return delegate.readFloatLE();
        }

        @Override
        public double readDouble() {
            return delegate.readDouble();
        }

        @Override
        public double readDoubleLE() {
            return delegate.readDoubleLE();
        }

        @Override
        public ByteBuf readBytes(int length) {
            return new WrappedByteBuf(delegate.readBytes(length), cf);
        }

        @Override
        public ByteBuf readSlice(int length) {
            return new WrappedByteBuf(delegate.readSlice(length), cf);
        }

        @Override
        public ByteBuf readRetainedSlice(int length) {
            return new WrappedByteBuf(delegate.readRetainedSlice(length), cf);
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst) {
            return new WrappedByteBuf(delegate.readBytes(dst), cf);
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int length) {
            return new WrappedByteBuf(delegate.readBytes(dst, length), cf);
        }

        @Override
        public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
            return new WrappedByteBuf(delegate.readBytes(dst, dstIndex, length), cf);
        }

        @Override
        public ByteBuf readBytes(byte[] dst) {
            return new WrappedByteBuf(delegate.readBytes(dst), cf);
        }

        @Override
        public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
            return new WrappedByteBuf(delegate.readBytes(dst, dstIndex, length), cf);
        }

        @Override
        public ByteBuf readBytes(ByteBuffer dst) {
            return new WrappedByteBuf(delegate.readBytes(dst), cf);
        }

        @Override
        public ByteBuf readBytes(OutputStream out, int length) throws IOException {
            return new WrappedByteBuf(delegate.readBytes(out, length), cf);
        }

        @Override
        public int readBytes(GatheringByteChannel out, int length) throws IOException {
            return delegate.readBytes(out, length);
        }

        @Override
        public CharSequence readCharSequence(int length, Charset charset) {
            return delegate.readCharSequence(length, charset);
        }

        @Override
        public int readBytes(FileChannel out, long position, int length) throws IOException {
            return delegate.readBytes(out, position, length);
        }

        @Override
        public ByteBuf skipBytes(int length) {
            return new WrappedByteBuf(delegate.skipBytes(length), cf);
        }

        @Override
        public ByteBuf writeBoolean(boolean value) {
            return new WrappedByteBuf(delegate.writeBoolean(value), cf);
        }

        @Override
        public ByteBuf writeByte(int value) {
            return new WrappedByteBuf(delegate.writeByte(value), cf);
        }

        @Override
        public ByteBuf writeShort(int value) {
            return new WrappedByteBuf(delegate.writeShort(value), cf);
        }

        @Override
        public ByteBuf writeShortLE(int value) {
            return new WrappedByteBuf(delegate.writeShortLE(value), cf);
        }

        @Override
        public ByteBuf writeMedium(int value) {
            return new WrappedByteBuf(delegate.writeMedium(value), cf);
        }

        @Override
        public ByteBuf writeMediumLE(int value) {
            return new WrappedByteBuf(delegate.writeMediumLE(value), cf);
        }

        @Override
        public ByteBuf writeInt(int value) {
            return new WrappedByteBuf(delegate.writeInt(value), cf);
        }

        @Override
        public ByteBuf writeIntLE(int value) {
            return new WrappedByteBuf(delegate.writeIntLE(value), cf);
        }

        @Override
        public ByteBuf writeLong(long value) {
            return new WrappedByteBuf(delegate.writeLong(value), cf);
        }

        @Override
        public ByteBuf writeLongLE(long value) {
            return new WrappedByteBuf(delegate.writeLongLE(value), cf);
        }

        @Override
        public ByteBuf writeChar(int value) {
            return new WrappedByteBuf(delegate.writeChar(value), cf);
        }

        @Override
        public ByteBuf writeFloat(float value) {
            return new WrappedByteBuf(delegate.writeFloat(value), cf);
        }

        @Override
        public ByteBuf writeFloatLE(float value) {
            return new WrappedByteBuf(delegate.writeFloatLE(value), cf);
        }

        @Override
        public ByteBuf writeDouble(double value) {
            return new WrappedByteBuf(delegate.writeDouble(value), cf);
        }

        @Override
        public ByteBuf writeDoubleLE(double value) {
            return new WrappedByteBuf(delegate.writeDoubleLE(value), cf);
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src) {
            return new WrappedByteBuf(delegate.writeBytes(src), cf);
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int length) {
            return new WrappedByteBuf(delegate.writeBytes(src, length), cf);
        }

        @Override
        public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
            return new WrappedByteBuf(delegate.writeBytes(src, srcIndex, length), cf);
        }

        @Override
        public ByteBuf writeBytes(byte[] src) {
            return new WrappedByteBuf(delegate.writeBytes(src), cf);
        }

        @Override
        public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
            return new WrappedByteBuf(delegate.writeBytes(src, srcIndex, length), cf);
        }

        @Override
        public ByteBuf writeBytes(ByteBuffer src) {
            return new WrappedByteBuf(delegate.writeBytes(src), cf);
        }

        @Override
        public int writeBytes(InputStream in, int length) throws IOException {
            return delegate.writeBytes(in, length);
        }

        @Override
        public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
            return delegate.writeBytes(in, length);
        }

        @Override
        public int writeBytes(FileChannel in, long position, int length) throws IOException {
            return delegate.writeBytes(in, position, length);
        }

        @Override
        public ByteBuf writeZero(int length) {
            return new WrappedByteBuf(delegate.writeZero(length), cf);
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            return delegate.writeCharSequence(sequence, charset);
        }

        @Override
        public int indexOf(int fromIndex, int toIndex, byte value) {
            return delegate.indexOf(fromIndex, toIndex, value);
        }

        @Override
        public int bytesBefore(byte value) {
            return delegate.bytesBefore(value);
        }

        @Override
        public int bytesBefore(int length, byte value) {
            return delegate.bytesBefore(length, value);
        }

        @Override
        public int bytesBefore(int index, int length, byte value) {
            return delegate.bytesBefore(index, length, value);
        }

        @Override
        public int forEachByte(ByteProcessor processor) {
            return delegate.forEachByte(processor);
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            return delegate.forEachByte(index, length, processor);
        }

        @Override
        public int forEachByteDesc(ByteProcessor processor) {
            return delegate.forEachByteDesc(processor);
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            return delegate.forEachByteDesc(index, length, processor);
        }

        @Override
        public ByteBuf copy() {
            return new WrappedByteBuf(delegate.copy(), cf);
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return new WrappedByteBuf(delegate.copy(index, length), cf);
        }

        @Override
        public ByteBuf slice() {
            return new WrappedByteBuf(delegate.slice(), cf);
        }

        @Override
        public ByteBuf retainedSlice() {
            return new WrappedByteBuf(delegate.retainedSlice(), cf);
        }

        @Override
        public ByteBuf slice(int index, int length) {
            return new WrappedByteBuf(delegate.slice(index, length), cf);
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return new WrappedByteBuf(delegate.retainedSlice(index, length), cf);
        }

        @Override
        public ByteBuf duplicate() {
            return new WrappedByteBuf(delegate.duplicate(), cf);
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return new WrappedByteBuf(delegate.retainedDuplicate(), cf);
        }

        @Override
        public int nioBufferCount() {
            return delegate.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer() {
            return delegate.nioBuffer();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return delegate.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            return delegate.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers() {
            return delegate.nioBuffers();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            return delegate.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return delegate.hasArray();
        }

        @Override
        public byte[] array() {
            return delegate.array();
        }

        @Override
        public int arrayOffset() {
            return delegate.arrayOffset();
        }

        @Override
        public boolean hasMemoryAddress() {
            return delegate.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            return delegate.memoryAddress();
        }

        @Override
        public boolean isContiguous() {
            return delegate.isContiguous();
        }

        @Override
        public String toString(Charset charset) {
            return delegate.toString(charset);
        }

        @Override
        public String toString(int index, int length, Charset charset) {
            return delegate.toString(index, length, charset);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int compareTo(ByteBuf buffer) {
            return delegate.compareTo(buffer);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public ByteBuf retain(int increment) {
            return new WrappedByteBuf(delegate.retain(increment), cf);
        }

        @Override
        public ByteBuf retain() {
            return new WrappedByteBuf(delegate.retain(), cf);
        }

        @Override
        public ByteBuf touch() {
            return new WrappedByteBuf(delegate.touch(), cf);
        }

        @Override
        public ByteBuf touch(Object hint) {
            return new WrappedByteBuf(delegate.touch(hint), cf);
        }

        @Override
        public int refCnt() {
            return delegate.refCnt();
        }

        @Override
        public boolean release() {
            return completingRelease(delegate.release());
        }

        @Override
        public boolean release(int i) {
            return completingRelease(delegate.release(i));
        }

        private boolean completingRelease(final boolean released) {
            if (released) {
                cf.complete(null);
            }
            return released;
        }
    }
}
