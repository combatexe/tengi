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
package com.noctarius.tengi.server.transport;

import com.noctarius.tengi.Identifier;
import com.noctarius.tengi.Message;
import com.noctarius.tengi.Packet;
import com.noctarius.tengi.buffer.MemoryBuffer;
import com.noctarius.tengi.buffer.impl.MemoryBufferFactory;
import com.noctarius.tengi.serialization.Serializer;
import com.noctarius.tengi.serialization.codec.impl.DefaultCodec;
import com.noctarius.tengi.serialization.impl.DefaultProtocol;
import com.noctarius.tengi.serialization.impl.DefaultProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class TcpTransportTestCase
        extends AbstractTransportTestCase {

    @Test
    public void testTcpTransport()
            throws Exception {

        Serializer serializer = Serializer.create(new DefaultProtocol(Collections.emptyList()));

        CompletableFuture<Object> future = new CompletableFuture<>();

        Initializer initializer = initializer(serializer, future);
        Runner runner = (channel) -> {
            ByteBuf buffer = Unpooled.buffer();
            MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
            DefaultCodec codec = new DefaultCodec(serializer.getProtocol(), memoryBuffer);

            codec.writeBytes("magic", DefaultProtocolConstants.PROTOCOL_MAGIC_HEADER);
            codec.writeBoolean("loggedIn", false);

            Packet packet = new Packet("login");
            packet.setValue("username", "Stan");

            Message message = Message.create(packet);
            serializer.writeObject("message", message, codec);

            channel.writeAndFlush(buffer);

            Object response = future.get();
            assertEquals(message, response);
        };

        practice(initializer, runner, false, ServerTransport.TCP_TRANSPORT);
    }

    @Test
    public void testPingPong()
            throws Exception {

        Serializer serializer = Serializer.create(new DefaultProtocol(Collections.emptyList()));

        CompletableFuture<Packet> future = new CompletableFuture<>();

        ChannelReader<ByteBuf> channelReader = (ctx, buffer) -> {
            MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
            DefaultCodec codec = new DefaultCodec(serializer.getProtocol(), memoryBuffer);

            boolean loggedIn = codec.readBoolean();
            Identifier connectionId = codec.readObject();

            Message message = codec.readObject();
            Packet packet = message.getBody();

            int counter = packet.getValue("counter");
            if (counter == 4) {
                future.complete(packet);
            } else {
                packet.setValue("counter", counter + 1);
                message = Message.create(packet);

                ByteBuf buffer2 = Unpooled.buffer();
                MemoryBuffer memoryBuffer2 = MemoryBufferFactory.create(buffer2);
                DefaultCodec codec2 = new DefaultCodec(serializer.getProtocol(), memoryBuffer2);

                codec2.writeBoolean("loggedIn", loggedIn);
                codec2.writeObject("connectionId", connectionId);
                serializer.writeObject("message", message, codec2);

                ctx.channel().writeAndFlush(buffer2);
            }
        };

        Initializer initializer = (pipeline) -> pipeline.addLast(inboundHandler(channelReader));

        Runner runner = (channel) -> {
            ByteBuf buffer = Unpooled.buffer();
            MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
            DefaultCodec codec = new DefaultCodec(serializer.getProtocol(), memoryBuffer);

            codec.writeBytes("magic", DefaultProtocolConstants.PROTOCOL_MAGIC_HEADER);
            codec.writeBoolean("loggedIn", false);

            Packet packet = new Packet("pingpong");
            packet.setValue("counter", 1);

            Message message = Message.create(packet);
            serializer.writeObject("message", message, codec);

            channel.writeAndFlush(buffer);

            Packet response = future.get();
            assertEquals(4, (int) response.getValue("counter"));
        };

        practice(initializer, runner, false, ServerTransport.TCP_TRANSPORT);
    }

    private static Initializer initializer(Serializer serializer, CompletableFuture<Object> future) {
        return (pipeline) -> pipeline.addLast(inboundHandler(channelReader(serializer, future)));
    }

    private static ChannelReader<ByteBuf> channelReader(Serializer serializer, CompletableFuture<Object> future) {
        return (ctx, buffer) -> {
            MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
            DefaultCodec codec = new DefaultCodec(serializer.getProtocol(), memoryBuffer);

            boolean loggedIn = codec.readBoolean();
            Identifier connectionId = codec.readObject();

            Object response = serializer.readObject(codec);
            future.complete(response);
        };
    }

}
