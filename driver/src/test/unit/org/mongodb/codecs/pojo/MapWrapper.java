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

package org.mongodb.codecs.pojo;

import java.util.Map;

public class MapWrapper {
    private Map<String, String> theMap;

    public void setTheMap(final Map<String, String> theMap) {
        this.theMap = theMap;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MapWrapper that = (MapWrapper) o;

        if (!theMap.equals(that.theMap)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return theMap.hashCode();
    }

    @Override
    public String toString() {
        return "MapWrapper{theMap=" + theMap + '}';
    }
}
