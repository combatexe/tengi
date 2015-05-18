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
package com.noctarius.tengi.client.impl.transport.http;

import com.noctarius.tengi.core.Identifier;
import com.noctarius.tengi.client.impl.Connector;
import com.noctarius.tengi.client.impl.ServerConnection;
import com.noctarius.tengi.core.serialization.Serializer;
import com.noctarius.tengi.spi.connection.ConnectionContext;
import io.netty.channel.Channel;

class HttpServerConnection
        extends ServerConnection {

    HttpServerConnection(ConnectionContext<Channel> connectionContext, Identifier connectionId, //
                         Connector connector, Serializer serializer) {

        super(connectionContext, connectionId, connector, serializer);
    }
}
