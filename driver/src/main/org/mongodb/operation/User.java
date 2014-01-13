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

import org.mongodb.MongoCredential;

/**
 * Encapsulates information about a user for the user-related operations.
 *
 * @since 3.0
 */
public class User {
    private final MongoCredential credential;
    private final boolean isReadOnly;

    /**
     * Constructs a new user with the given credential and read-only state.
     *
     * @param credential the credential for this user
     * @param readOnly   true if this is a read-only user
     */
    public User(final MongoCredential credential, final boolean readOnly) {
        this.credential = credential;
        isReadOnly = readOnly;
    }

    /**
     * The credential for this user.
     *
     * @return the user's credential
     */
    public MongoCredential getCredential() {
        return credential;
    }

    /**
     * Whether the user is read-only.
     *
     * @return true if this is a read-only user.
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }
}
