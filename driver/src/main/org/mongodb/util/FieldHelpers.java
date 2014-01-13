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

package org.mongodb.util;

// TODO: Not sure about this class.  Is it generally applicable enough to be public?
public final class FieldHelpers {

    public static boolean asBoolean(final Object fieldValue) {
        if (fieldValue == null) {
            return false;
        } else if (fieldValue instanceof Boolean) {
            return (Boolean) fieldValue;
        } else if (fieldValue instanceof Number) {
            return ((Number) fieldValue).doubleValue() != 0;
        } else {
            throw new IllegalArgumentException("value is of type " + fieldValue.getClass()
                                               + " and can not be converted to a boolean.");
        }
    }

    private FieldHelpers() {
    }
}
