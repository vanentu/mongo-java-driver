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

package org.mongodb.protocol;

import org.mongodb.CommandResult;
import org.mongodb.Decoder;
import org.mongodb.Document;
import org.mongodb.MongoException;
import org.mongodb.connection.Connection;
import org.mongodb.connection.SingleResultCallback;

class CommandResultCallback extends CommandResultBaseCallback {
    private final SingleResultCallback<CommandResult> callback;

    public CommandResultCallback(final SingleResultCallback<CommandResult> callback, final Decoder<Document> decoder,
                                 final long requestId, final Connection connection, final boolean closeConnection) {
        super(decoder, requestId, connection, closeConnection);
        this.callback = callback;
    }

    @Override
    protected boolean callCallback(final CommandResult commandResult, final MongoException e) {
        if (e != null) {
            callback.onResult(null, e);
        } else if (!commandResult.isOk()) {
            callback.onResult(null, ProtocolHelper.getCommandFailureException(commandResult));
        } else {
            callback.onResult(commandResult, null);
        }
        return true;
    }
}
