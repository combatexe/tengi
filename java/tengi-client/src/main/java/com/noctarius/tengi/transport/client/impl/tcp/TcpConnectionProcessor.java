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
package com.noctarius.tengi.transport.client.impl.tcp;

import com.noctarius.tengi.Identifier;
import com.noctarius.tengi.buffer.MemoryBuffer;
import com.noctarius.tengi.buffer.impl.MemoryBufferFactory;
import com.noctarius.tengi.client.Connector;
import com.noctarius.tengi.client.MessagePublisher;
import com.noctarius.tengi.connection.Connection;
import com.noctarius.tengi.connection.ConnectionContext;
import com.noctarius.tengi.serialization.Serializer;
import com.noctarius.tengi.serialization.codec.AutoClosableDecoder;
import com.noctarius.tengi.transport.client.impl.ClientConnectionProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

class TcpConnectionProcessor
        extends ClientConnectionProcessor<ByteBuf, Channel> {

    TcpConnectionProcessor(Serializer serializer, MessagePublisher messagePublisher,
                           CompletableFuture<Connection> connectorFuture, Connector connector) {

        super(serializer, messagePublisher, connectorFuture, connector);
    }

    @Override
    protected AutoClosableDecoder decode(ChannelHandlerContext ctx, ByteBuf buffer)
            throws Exception {

        MemoryBuffer memoryBuffer = MemoryBufferFactory.create(buffer);
        return getSerializer().retrieveDecoder(memoryBuffer);
    }

    @Override
    protected ConnectionContext<Channel> createConnectionContext(ChannelHandlerContext ctx, //
                                                                 Identifier connectionId, Connector connector) {

        return new TcpConnectionContext(connectionId, getSerializer(), connector);
    }

    @Override
    protected Connection createConnection(ConnectionContext<Channel> connectionContext, Identifier connectionId,
                                          Connector connector, Serializer serializer) {

        return new TcpServerConnection(connectionContext, connectionId, connector, serializer);
    }

}
