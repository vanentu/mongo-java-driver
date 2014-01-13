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

package org.mongodb.operation;

import org.mongodb.Document;
import org.mongodb.WriteConcern;

public class Update extends BaseUpdate {
    private final Document updateOperations;
    private boolean isMulti = false;

    public Update(final WriteConcern writeConcern, final Document filter, final Document updateOperations) {
        super(writeConcern, filter);

        this.updateOperations = updateOperations;
    }

    public Document getUpdateOperations() {
        return updateOperations;
    }

    public boolean isMulti() {
        return isMulti;
    }

    //CHECKSTYLE:OFF
    public Update multi(final boolean isMulti) {
        this.isMulti = isMulti;
        return this;
    }
    //CHECKSTYLE:ON

    @Override
    public Update upsert(final boolean isUpsert) {
        super.upsert(isUpsert);
        return this;
    }
}

