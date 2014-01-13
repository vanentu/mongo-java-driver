/*
 * Copyright (c) 2008 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection;

import org.bson.ByteBuf;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestConnectionProvider implements ConnectionProvider {

    @Override
    public Connection get() {
        return new Connection() {
            @Override
            public void sendMessage(final List<ByteBuf> byteBuffers, final int lastRequestId) {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public ResponseBuffers receiveMessage(final int responseTo) {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public void sendMessageAsync(final List<ByteBuf> byteBuffers, final int lastRequestId,
                                         final SingleResultCallback<Void> callback) {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public void receiveMessageAsync(final int responseTo, final SingleResultCallback<ResponseBuffers> callback) {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public ServerAddress getServerAddress() {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public String getId() {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public void close() {
                throw new UnsupportedOperationException("Not implemented yet!");
            }

            @Override
            public boolean isClosed() {
                throw new UnsupportedOperationException("Not implemented yet!");
            }
        };
    }

    @Override
    public Connection get(final long timeout, final TimeUnit timeUnit) {
        return get();
    }

    @Override
    public void close() {
    }
}
