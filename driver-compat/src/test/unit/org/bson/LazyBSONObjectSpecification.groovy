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

package org.bson

import com.mongodb.BasicDBObject
import org.bson.types.*
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

@SuppressWarnings(['LineLength'])
class LazyBSONObjectSpecification extends Specification {

    def setupSpec() {
        Map.metaClass.bitwiseNegate = { new BasicDBObject(delegate) }
        Pattern.metaClass.equals = { Pattern other ->
            delegate.pattern() == other.pattern() && delegate.flags() == other.flags()
        }
    }

    @Unroll
    def 'should read #type'() {
        expect:
        value == new LazyBSONObject(bytes as byte[], new LazyBSONCallback()).get('f')

        where:
        value                                                                 | bytes
        -1.01                                                                 | [16, 0, 0, 0, 1, 102, 0, 41, 92, -113, -62, -11, 40, -16, -65, 0]
        Float.MIN_VALUE                                                       | [16, 0, 0, 0, 1, 102, 0, 0, 0, 0, 0, 0, 0, -96, 54, 0]
        Double.MAX_VALUE                                                      | [16, 0, 0, 0, 1, 102, 0, -1, -1, -1, -1, -1, -1, -17, 127, 0]
        0.0                                                                   | [16, 0, 0, 0, 1, 102, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        ''                                                                    | [13, 0, 0, 0, 2, 102, 0, 1, 0, 0, 0, 0, 0]
        'danke'                                                               | [18, 0, 0, 0, 2, 102, 0, 6, 0, 0, 0, 100, 97, 110, 107, 101, 0, 0]
        ',+\\\"<>;[]{}@#$%^&*()+_'                                            | [35, 0, 0, 0, 2, 102, 0, 23, 0, 0, 0, 44, 43, 92, 34, 60, 62, 59, 91, 93, 123, 125, 64, 35, 36, 37, 94, 38, 42, 40, 41, 43, 95, 0, 0]
        'a\u00e9\u3042\u0430\u0432\u0431\u0434'                               | [27, 0, 0, 0, 2, 102, 0, 15, 0, 0, 0, 97, -61, -87, -29, -127, -126, -48, -80, -48, -78, -48, -79, -48, -76, 0, 0]
        new LazyBSONObject([5, 0, 0, 0, 0] as byte[], new LazyBSONCallback()) | [13, 0, 0, 0, 3, 102, 0, 5, 0, 0, 0, 0, 0]
        []                                                                    | [13, 0, 0, 0, 4, 102, 0, 5, 0, 0, 0, 0, 0]
        [1, 2, 3] as int[]                                                    | [34, 0, 0, 0, 4, 102, 0, 26, 0, 0, 0, 16, 48, 0, 1, 0, 0, 0, 16, 49, 0, 2, 0, 0, 0, 16, 50, 0, 3, 0, 0, 0, 0, 0]
        [[]]                                                                  | [21, 0, 0, 0, 4, 102, 0, 13, 0, 0, 0, 4, 48, 0, 5, 0, 0, 0, 0, 0, 0]
        new Binary((byte) 0x01, (byte[]) [115, 116, 11])                      | [16, 0, 0, 0, 5, 102, 0, 3, 0, 0, 0, 1, 115, 116, 11, 0]
        [13, 12] as byte[]                                                    | [15, 0, 0, 0, 5, 102, 0, 2, 0, 0, 0, 0, 13, 12, 0]
        [102, 111, 111] as byte[]                                             | [16, 0, 0, 0, 5, 102, 0, 3, 0, 0, 0, 0, 102, 111, 111, 0]
        new ObjectId('50d3332018c6a1d8d1662b61')                              | [20, 0, 0, 0, 7, 102, 0, 80, -45, 51, 32, 24, -58, -95, -40, -47, 102, 43, 97, 0]
        true                                                                  | [9, 0, 0, 0, 8, 102, 0, 1, 0]
        false                                                                 | [9, 0, 0, 0, 8, 102, 0, 0, 0]
        new Date(582163200)                                                   | [16, 0, 0, 0, 9, 102, 0, 0, 27, -77, 34, 0, 0, 0, 0, 0]
        null                                                                  | [8, 0, 0, 0, 10, 102, 0, 0]
        Pattern.compile('[a]*', Pattern.CASE_INSENSITIVE)                     | [15, 0, 0, 0, 11, 102, 0, 91, 97, 93, 42, 0, 105, 0, 0]
        new Code('var i = 0')                                                 | [22, 0, 0, 0, 13, 102, 0, 10, 0, 0, 0, 118, 97, 114, 32, 105, 32, 61, 32, 48, 0, 0]
        new Symbol('c')                                                       | [14, 0, 0, 0, 14, 102, 0, 2, 0, 0, 0, 99, 0, 0]
        new CodeWScope('i++', ~['x': 1])                                      | [32, 0, 0, 0, 15, 102, 0, 24, 0, 0, 0, 4, 0, 0, 0, 105, 43, 43, 0, 12, 0, 0, 0, 16, 120, 0, 1, 0, 0, 0, 0, 0]
        -12                                                                   | [12, 0, 0, 0, 16, 102, 0, -12, -1, -1, -1, 0]
        Integer.MIN_VALUE                                                     | [12, 0, 0, 0, 16, 102, 0, 0, 0, 0, -128, 0]
        0                                                                     | [12, 0, 0, 0, 16, 102, 0, 0, 0, 0, 0, 0]
        new BSONTimestamp(123999401, 44332)                                   | [16, 0, 0, 0, 17, 102, 0, 44, -83, 0, 0, -87, 20, 100, 7, 0]
        Long.MAX_VALUE                                                        | [16, 0, 0, 0, 18, 102, 0, -1, -1, -1, -1, -1, -1, -1, 127, 0]
        new MinKey()                                                          | [8, 0, 0, 0, -1, 102, 0, 0]
        new MaxKey()                                                          | [8, 0, 0, 0, 127, 102, 0, 0]

        type = BSONType.findByValue(bytes[4])
    }

    def 'should have nested items as lazy'() {
        given:
        byte[] bytes = [
                53, 0, 0, 0, 4, 97, 0, 26, 0, 0, 0, 16, 48, 0, 1, 0, 0, 0, 16, 49, 0, 2, 0, 0, 0, 16, 50, 0,
                3, 0, 0, 0, 0, 3, 111, 0, 16, 0, 0, 0, 1, 122, 0, -102, -103, -103, -103, -103, -103, -71, 63, 0, 0
        ];

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.get('a') instanceof LazyBSONList
        document.get('o') instanceof LazyBSONObject
    }

    def 'should not understand DBRefs'() {
        given:
        byte[] bytes = [
                44, 0, 0, 0, 3, 102, 0, 36, 0, 0, 0, 2, 36, 114, 101, 102,
                0, 4, 0, 0, 0, 97, 46, 98, 0, 7, 36, 105, 100, 0, 18, 52,
                86, 120, -112, 18, 52, 86, 120, -112, 18, 52, 0, 0,
        ]

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.get('f') instanceof LazyBSONObject
        document.get('f').keySet() == ['$ref', '$id'] as Set

    }

    def 'should retain fields order'() {
        given:
        byte[] bytes = [
                47, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 16, 98, 0, 2, 0, 0, 0, 16, 100, 0, 3, 0, 0,
                0, 16, 99, 0, 4, 0, 0, 0, 16, 101, 0, 5, 0, 0, 0, 16, 48, 0, 6, 0, 0, 0, 0
        ]

        when:
        Iterator<String> iterator = new LazyBSONObject(bytes, new LazyBSONCallback()).keySet().iterator()

        then:
        iterator.next() == 'a'
        iterator.next() == 'b'
        iterator.next() == 'd'
        iterator.next() == 'c'
        iterator.next() == 'e'
        iterator.next() == '0'
        !iterator.hasNext()
    }

    def 'should be able to compare itself to others'() {
        given:
        byte[] bytes = [
                39, 0, 0, 0, 3, 97, 0,
                14, 0, 0, 0, 2, 120, 0, 2, 0, 0, 0, 121, 0, 0,
                3, 98, 0,
                14, 0, 0, 0, 2, 120, 0, 2, 0, 0, 0, 121, 0, 0,
                0
        ]

        when:
        LazyBSONObject bsonObject1 = new LazyBSONObject(bytes, new LazyBSONCallback())
        LazyBSONObject bsonObject2 = new LazyBSONObject(bytes, new LazyBSONCallback())
        LazyBSONObject bsonObject3 = new LazyBSONObject(bytes, 7, new LazyBSONCallback())
        LazyBSONObject bsonObject4 = new LazyBSONObject(bytes, 24, new LazyBSONCallback())


        then:
        bsonObject1 == bsonObject2
        bsonObject3 == bsonObject4
        bsonObject1 != bsonObject3
        bsonObject4 == new LazyBSONObject([14, 0, 0, 0, 2, 120, 0, 2, 0, 0, 0, 121, 0, 0] as byte[], new LazyBSONCallback())
        bsonObject1 != new LazyBSONObject([] as byte[], new LazyBSONCallback())
    }

    def 'should return the size of a document'() {
        given:
        byte[] bytes = [12, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 0]

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.getBSONSize() == 12
    }

    def 'should understand that object is empty'() {
        given:
        byte[] bytes = [5, 0, 0, 0, 0]

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.isEmpty()
    }

    def 'should implement Map.keySet()'() {
        given:
        byte[] bytes = [16, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 8, 98, 0, 1, 0]

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.containsField('a')
        !document.containsField('z')
        document.get('z') == null
        document.keySet() == ['a', 'b'] as Set
    }

    def 'should implement Map.entrySet()'() {
        given:
        byte[] bytes = [16, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 8, 98, 0, 1, 0]

        when:
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())

        then:
        document.entrySet().size() == 2
        document.entrySet().find { it.value == 1 } != null
        document.entrySet().find { it.value == true } != null
    }

    def 'should throw on modification'() {
        given:
        LazyBSONObject document = new LazyBSONObject(
                [16, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 8, 98, 0, 1, 0] as byte[],
                new LazyBSONCallback()
        )

        when:
        document.keySet().add('c')

        then:
        thrown(UnsupportedOperationException)

        when:
        document.put('c', 2)

        then:
        thrown(UnsupportedOperationException)

        when:
        document.removeField('a')

        then:
        thrown(UnsupportedOperationException)

        when:
        document.toMap().put('a', 22)

        then:
        thrown(UnsupportedOperationException)
    }

    def 'should pipe to stream'() {
        given:
        byte[] bytes = [16, 0, 0, 0, 16, 97, 0, 1, 0, 0, 0, 8, 98, 0, 1, 0];
        LazyBSONObject document = new LazyBSONObject(bytes, new LazyBSONCallback())
        ByteArrayOutputStream baos = new ByteArrayOutputStream()

        when:
        document.pipe(baos)

        then:
        bytes == baos.toByteArray()

    }


}
