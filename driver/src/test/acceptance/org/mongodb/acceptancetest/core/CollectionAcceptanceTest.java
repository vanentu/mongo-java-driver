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

package org.mongodb.acceptancetest.core;

import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.CodeWithScope;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mongodb.DBRef;
import org.mongodb.DatabaseTestCase;
import org.mongodb.Document;
import org.mongodb.MongoCollection;
import org.mongodb.MongoCursor;
import org.mongodb.WriteConcern;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Documents the basic functionality of MongoDB Collections available via the Java driver.
 */
public class CollectionAcceptanceTest extends DatabaseTestCase {
    @Test
    public void shouldBeAbleToIterateOverACollection() {
        int numberOfDocuments = 10;
        initialiseCollectionWithDocuments(numberOfDocuments);

        int countOfDocumentsInIterator = 0;
        for (final Document document : collection.find()) {
            assertThat(document, is(notNullValue()));
            countOfDocumentsInIterator++;
        }
        assertThat(countOfDocumentsInIterator, is(numberOfDocuments));
    }

    @Test
    public void shouldBeAbleToIterateOverACursor() {
        int numberOfDocuments = 10;
        initialiseCollectionWithDocuments(numberOfDocuments);

        MongoCursor<Document> cursor = collection.find().get();
        int countOfDocumentsInIterator = 0;
        try {
            while (cursor.hasNext()) {
                assertThat(cursor.next(), is(notNullValue()));
                countOfDocumentsInIterator++;
            }
        } finally {
            cursor.close();
        }
        assertThat(countOfDocumentsInIterator, is(numberOfDocuments));
    }

    @Test
    public void shouldCountNumberOfDocumentsInCollection() {
        assertThat(collection.find().count(), is(0L));

        collection.insert(new Document("myField", "myValue"));

        assertThat(collection.find().count(), is(1L));
    }

    @Test
    public void shouldGetStatistics() {
        String newCollectionName = "shouldGetStatistics";
        database.tools().createCollection(newCollectionName);
        MongoCollection<Document> newCollection = database.getCollection(newCollectionName);

        Document collectionStatistics = newCollection.tools().getStatistics();
        assertThat(collectionStatistics, is(notNullValue()));

        assertThat((String) collectionStatistics.get("ns"), is(getDatabaseName() + "." + newCollectionName));
    }

    @Test
    public void shouldDropExistingCollection() {
        String collectionName = "shouldDropExistingCollection";
        database.tools().createCollection(collectionName);
        MongoCollection<Document> newCollection = database.getCollection(collectionName);

        assertThat(database.tools().getCollectionNames().contains(collectionName), is(true));

        newCollection.tools().drop();

        assertThat(database.tools().getCollectionNames().contains(collectionName), is(false));
    }

    @Test
    public void shouldAcceptDocumentsWithAllValidValueTypes() {
        Document doc = new Document();
        doc.append("_id", new ObjectId());
        doc.append("bool", true);
        doc.append("int", 3);
        doc.append("short", (short) 4);
        doc.append("long", 5L);
        doc.append("str", "Hello MongoDB");
        doc.append("float", 6.0f);
        doc.append("double", 1.1);
        doc.append("date", new Date());
        doc.append("ts", new BSONTimestamp(5, 1));
        doc.append("pattern", Pattern.compile(".*"));
        doc.append("minKey", new MinKey());
        doc.append("maxKey", new MaxKey());
        doc.append("js", new Code("code"));
        doc.append("jsWithScope", new CodeWithScope("code", new Document()));
        doc.append("null", null);
        doc.append("uuid", UUID.randomUUID());
        doc.append("db ref", new DBRef(new ObjectId(), "test.test"));
        doc.append("binary", new Binary((byte) 42, new byte[]{10, 11, 12}));
        doc.append("byte array", new byte[]{1, 2, 3});
        doc.append("int array", new int[]{4, 5, 6});
        doc.append("list", Arrays.asList(7, 8, 9));
        doc.append("doc list", Arrays.asList(new Document("x", 1), new Document("x", 2)));

        collection.insert(doc);
        Document found = collection.find().getOne();
        assertNotNull(found);
        assertEquals(ObjectId.class, found.get("_id").getClass());
        assertEquals(Boolean.class, found.get("bool").getClass());
        assertEquals(Integer.class, found.get("int").getClass());
        assertEquals(Integer.class, found.get("short").getClass());
        assertEquals(Long.class, found.get("long").getClass());
        assertEquals(String.class, found.get("str").getClass());
        assertEquals(Double.class, found.get("float").getClass());
        assertEquals(Double.class, found.get("double").getClass());
        assertEquals(Date.class, found.get("date").getClass());
        assertEquals(BSONTimestamp.class, found.get("ts").getClass());
        assertEquals(Pattern.class, found.get("pattern").getClass());
        assertEquals(MinKey.class, found.get("minKey").getClass());
        assertEquals(MaxKey.class, found.get("maxKey").getClass());
        assertEquals(Code.class, found.get("js").getClass());
        assertEquals(CodeWithScope.class, found.get("jsWithScope").getClass());
        assertNull(found.get("null"));
        assertEquals(UUID.class, found.get("uuid").getClass());
        // assertEquals(DBRef.class, found.get("db ref")); // see JAVA-918
        assertEquals(Binary.class, found.get("binary").getClass());
        assertEquals(byte[].class, found.get("byte array").getClass());
        assertTrue(found.get("int array") instanceof List);
        assertTrue(found.get("list") instanceof List);
        assertTrue(found.get("doc list") instanceof List);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectDocumentsWithFieldNamesContainingDots() {
        collection.save(new Document("x.y", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNestedDocumentsWithFieldNamesContainingDots() {
        collection.save(new Document("x", new Document("a.b", 1)));
    }

    private void initialiseCollectionWithDocuments(final int numberOfDocuments) {
        for (int i = 0; i < numberOfDocuments; i++) {
            collection.withWriteConcern(WriteConcern.ACKNOWLEDGED).insert(new Document("_id", i));
        }
    }

}
