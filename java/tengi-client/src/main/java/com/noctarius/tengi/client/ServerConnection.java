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
package com.noctarius.tengi.client;

import com.noctarius.tengi.Identifier;
import com.noctarius.tengi.Message;
import com.noctarius.tengi.connection.AbstractConnection;
import com.noctarius.tengi.connection.ConnectionContext;
import com.noctarius.tengi.serialization.Serializer;
import io.netty.channel.Channel;

public class ServerConnection
        extends AbstractConnection {

    protected ServerConnection(ConnectionContext<Channel> connectionContext, Identifier connectionId, //
                     Connector connector, Serializer serializer) {

        super(connectionContext, connectionId, connector, serializer);
    }

    public ConnectionContext getConnectionContext() {
        return super.getConnectionContext();
    }

    void publishMessage(Message message) {
        getMessageListeners().forEach((listener) -> listener.onMessage(this, message));
    }

}
