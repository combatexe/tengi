/*
 * Copyright (c) 2015, Christoph Engelbert (aka noctarius) and
 * contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.noctarius.tengi.serialization.impl;

import com.noctarius.tengi.buffer.MemoryBuffer;
import com.noctarius.tengi.buffer.ReadableMemoryBuffer;
import com.noctarius.tengi.buffer.WritableMemoryBuffer;
import com.noctarius.tengi.buffer.impl.MemoryBufferFactory;
import com.noctarius.tengi.serialization.Protocol;
import com.noctarius.tengi.serialization.Serializer;
import com.noctarius.tengi.serialization.codec.AutoClosableDecoder;
import com.noctarius.tengi.serialization.codec.AutoClosableEncoder;
import com.noctarius.tengi.serialization.codec.Decoder;
import com.noctarius.tengi.serialization.codec.Encoder;
import com.noctarius.tengi.serialization.codec.impl.DefaultCodec;
import com.noctarius.tengi.serialization.debugger.SerializationDebugger;
import com.noctarius.tengi.utils.pooling.ObjectPool;
import com.noctarius.tengi.utils.pooling.PooledObject;
import com.noctarius.tengi.utils.pooling.impl.NonBlockingObjectPool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DefaultSerializer
        implements Serializer {

    private final ObjectPool<DefaultCodec> codecPool;
    private final Protocol protocol;

    public DefaultSerializer(Protocol protocol) {
        this.protocol = protocol;
        this.codecPool = new NonBlockingObjectPool<>(new CodecObjectHandler(protocol), 100);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public <O> O readObject(Decoder decoder)
            throws Exception {

        if (!SerializationDebugger.Debugger.ENABLED) {
            return decoder.readObject();

        } else {
            try {
                return decoder.readObject();

            } catch (Exception e) {
                SerializationDebugger debugger = SerializationDebugger.create();
                debugger.fixFramesToStackTrace(e);
                throw e;
            }
        }
    }

    @Override
    public <O> MemoryBuffer writeObject(O object)
            throws Exception {

        PooledObject<DefaultCodec> po = codecPool.acquire();
        try {
            if (!SerializationDebugger.Debugger.ENABLED) {
                ByteBuf buffer = Unpooled.buffer();
                MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
                DefaultCodec codec = po.getObject().setMemoryBuffer(memoryBuffer);
                writeObject("object", object, codec);
                return memoryBuffer;

            } else {
                try {
                    ByteBuf buffer = Unpooled.buffer();
                    MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
                    DefaultCodec codec = po.getObject().setMemoryBuffer(memoryBuffer);
                    writeObject("object", object, codec);
                    return memoryBuffer;

                } catch (Exception e) {
                    SerializationDebugger debugger = SerializationDebugger.create();
                    debugger.fixFramesToStackTrace(e);
                    throw e;
                }
            }
        } finally {
            codecPool.release(po);
        }
    }

    @Override
    public <O> void writeObject(String fieldName, O object, Encoder encoder)
            throws Exception {

        if (!SerializationDebugger.Debugger.ENABLED) {
            encoder.writeObject(fieldName, object);

        } else {
            try {
                encoder.writeObject(fieldName, object);

            } catch (Exception e) {
                SerializationDebugger debugger = SerializationDebugger.create();
                debugger.fixFramesToStackTrace(e);
                throw e;
            }
        }
    }

    @Override
    public AutoClosableEncoder retrieveEncoder(MemoryBuffer memoryBuffer) {
        PooledObject<DefaultCodec> pooledObject = codecPool.acquire((codec) -> codec.setMemoryBuffer(memoryBuffer));
        return new AutoClosableCodec(pooledObject);
    }

    @Override
    public AutoClosableDecoder retrieveDecoder(MemoryBuffer memoryBuffer) {
        PooledObject<DefaultCodec> pooledObject = codecPool.acquire((codec) -> codec.setMemoryBuffer(memoryBuffer));
        return new AutoClosableCodec(pooledObject);
    }

    private final class AutoClosableCodec
            implements AutoClosableDecoder, AutoClosableEncoder {

        private final PooledObject<DefaultCodec> pooledObject;
        private final DefaultCodec defaultCodec;

        private AutoClosableCodec(PooledObject<DefaultCodec> pooledObject) {
            this.pooledObject = pooledObject;
            this.defaultCodec = pooledObject.getObject();
        }

        @Override
        public int readBytes(byte[] bytes) {
            return defaultCodec.readBytes(bytes);
        }

        @Override
        public int readBytes(byte[] bytes, int offset, int length) {
            return defaultCodec.readBytes(bytes, offset, length);
        }

        @Override
        public boolean readBoolean() {
            return defaultCodec.readBoolean();
        }

        @Override
        public boolean[] readBoolArray() {
            return defaultCodec.readBoolArray();
        }

        @Override
        public byte readByte() {
            return defaultCodec.readByte();
        }

        @Override
        public short readUnsignedByte() {
            return defaultCodec.readUnsignedByte();
        }

        @Override
        public short readShort() {
            return defaultCodec.readShort();
        }

        @Override
        public char readChar() {
            return defaultCodec.readChar();
        }

        @Override
        public int readInt() {
            return defaultCodec.readInt();
        }

        @Override
        public long readLong() {
            return defaultCodec.readLong();
        }

        @Override
        public float readFloat() {
            return defaultCodec.readFloat();
        }

        @Override
        public double readDouble() {
            return defaultCodec.readDouble();
        }

        @Override
        public String readString() {
            return defaultCodec.readString();
        }

        @Override
        public <O> O readObject()
                throws Exception {

            return defaultCodec.readObject();
        }

        @Override
        public <O> O readNullableObject()
                throws Exception {

            return defaultCodec.readNullableObject();
        }

        @Override
        public ReadableMemoryBuffer getReadableMemoryBuffer() {
            return defaultCodec.getReadableMemoryBuffer();
        }

        @Override
        public void writeBytes(String fieldName, byte[] bytes) {
            defaultCodec.writeBytes(fieldName, bytes);
        }

        @Override
        public void writeBytes(String fieldName, byte[] bytes, int offset, int length) {
            defaultCodec.writeBytes(fieldName, bytes, offset, length);
        }

        @Override
        public void writeBoolean(String fieldName, boolean value) {
            defaultCodec.writeBoolean(fieldName, value);
        }

        @Override
        public void writeBoolArray(String fieldName, boolean[] values) {
            defaultCodec.writeBoolArray(fieldName, values);
        }

        @Override
        public void writeByte(String fieldName, int value) {
            defaultCodec.writeByte(fieldName, value);
        }

        @Override
        public void writeUnsignedByte(String fieldName, short value) {
            defaultCodec.writeUnsignedByte(fieldName, value);
        }

        @Override
        public void writeShort(String fieldName, short value) {
            defaultCodec.writeShort(fieldName, value);
        }

        @Override
        public void writeChar(String fieldName, char value) {
            defaultCodec.writeChar(fieldName, value);
        }

        @Override
        public void writeInt(String fieldName, int value) {
            defaultCodec.writeInt(fieldName, value);
        }

        @Override
        public void writeLong(String fieldName, long value) {
            defaultCodec.writeLong(fieldName, value);
        }

        @Override
        public void writeFloat(String fieldName, float value) {
            defaultCodec.writeFloat(fieldName, value);
        }

        @Override
        public void writeDouble(String fieldName, double value) {
            defaultCodec.writeDouble(fieldName, value);
        }

        @Override
        public void writeString(String fieldName, String value) {
            defaultCodec.writeString(fieldName, value);
        }

        @Override
        public void writeObject(String fieldName, Object object)
                throws Exception {

            defaultCodec.writeObject(fieldName, object);
        }

        @Override
        public void writeNullableObject(String fieldName, Object object)
                throws Exception {

            defaultCodec.writeNullableObject(fieldName, object);
        }

        @Override
        public WritableMemoryBuffer getWritableMemoryBuffer() {
            return defaultCodec.getWritableMemoryBuffer();
        }

        @Override
        public void close()
                throws Exception {

            codecPool.release(pooledObject);
        }
    }

}
