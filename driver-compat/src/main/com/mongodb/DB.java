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

package com.mongodb;

import org.mongodb.Codec;
import org.mongodb.CreateCollectionOptions;
import org.mongodb.Document;
import org.mongodb.MongoCommandFailureException;
import org.mongodb.MongoCursor;
import org.mongodb.MongoNamespace;
import org.mongodb.annotations.ThreadSafe;
import org.mongodb.codecs.PrimitiveCodecs;
import org.mongodb.connection.BufferProvider;
import org.mongodb.connection.Cluster;
import org.mongodb.connection.ClusterDescription;
import org.mongodb.operation.CommandOperation;
import org.mongodb.operation.CreateUserOperation;
import org.mongodb.operation.DropUserOperation;
import org.mongodb.operation.Find;
import org.mongodb.operation.QueryOperation;
import org.mongodb.operation.UpdateUserOperation;
import org.mongodb.operation.User;
import org.mongodb.operation.UserExistsOperation;
import org.mongodb.session.Session;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.DBObjects.toDocument;
import static com.mongodb.MongoExceptions.mapException;
import static org.mongodb.MongoCredential.createMongoCRCredential;

/**
 * A thread-safe client view of a logical database in a MongoDB cluster. A DB instance can be achieved from a {@link MongoClient}
 * instance using code like:<br/>
 * <pre>
 * {@code
 * MongoClient mongoClient = new MongoClient();
 * DB db = mongoClient.getDB("<db name>");
 * }
 * </pre>
 *
 * @see MongoClient
 * @mongodb.driver.manual reference/glossary/#term-database database
 */
@ThreadSafe
public class DB {
    private final Mongo mongo;
    private final String name;
    private final ConcurrentHashMap<String, DBCollection> collectionCache;
    private final Bytes.OptionHolder optionHolder;
    private final Codec<Document> documentCodec;
    private final Codec<Document> commandCodec = new org.mongodb.codecs.DocumentCodec();
    private volatile ReadPreference readPreference;
    private volatile WriteConcern writeConcern;

    DB(final Mongo mongo, final String name, final Codec<Document> documentCodec) {
        this.mongo = mongo;
        this.name = name;
        this.documentCodec = documentCodec;
        this.collectionCache = new ConcurrentHashMap<String, DBCollection>();
        this.optionHolder = new Bytes.OptionHolder(mongo.getOptionHolder());
    }

    /**
     * Constructs a new instance of the {@code DB}.
     *
     * @param mongo the mongo instance
     * @param name  the database name
     */
    public DB(final Mongo mongo, final String name) {
        this(mongo, name, new DocumentCodec(PrimitiveCodecs.createDefault()));
    }

    /**
     * Gets the Mongo instance
     *
     * @return the mongo instance that this database was created from.
     */
    public Mongo getMongo() {
        return mongo;
    }

    /**
     * Sets the read preference for this database. Will be used as default for
     * read operations from any collection in this database. See the
     * documentation for {@link ReadPreference} for more information.
     *
     * @param readPreference {@code ReadPreference} to use
     */
    public void setReadPreference(final ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    /**
     * Sets the write concern for this database. It will be used for
     * write operations to any collection in this database. See the
     * documentation for {@link WriteConcern} for more information.
     *
     * @param writeConcern {@code WriteConcern} to use
     */
    public void setWriteConcern(final WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    /**
     * Gets the read preference for this database.
     *
     * @return {@code ReadPreference} to be used for read operations, if not specified explicitly
     */
    public ReadPreference getReadPreference() {
        return readPreference != null ? readPreference : mongo.getReadPreference();
    }

    /**
     * Gets the write concern for this database.
     *
     * @return {@code WriteConcern} to be used for write operations, if not specified explicitly
     */    public WriteConcern getWriteConcern() {
        return writeConcern != null ? writeConcern : mongo.getWriteConcern();
    }

    /**
     * Starts a new "consistent request". Following this call and until requestDone() is called, all db operations should use the same
     * underlying connection. This is useful to ensure that operations happen in a certain order with predictable results.
     */
    public void requestStart() {
        getMongo().pinSession();
    }

    /**
     * Ends the current "consistent request"
     */
    public void requestDone() {
        getMongo().unpinSession();
    }

    /**
     * ensure that a connection is assigned to the current "consistent request" (from primary pool, if connected to a replica set)
     */
    public void requestEnsureConnection() {
        // do nothing for now
    }

    /**
     * Gets a collection with a given name.
     * If the collection does not exist, a new collection is created.
     * <p/>
     * This class is NOT part of the public API.  Be prepared for non-binary compatible changes in minor releases.
     *
     * @param name the name of the collection
     * @return the collection
     */
    protected DBCollection doGetCollection(final String name) {
        return getCollection(name);
    }

    /**
     * Gets a collection with a given name.
     * If the collection does not exist, a new collection is created.
     *
     * @param name the name of the collection to return
     * @return the collection
     */
    public DBCollection getCollection(final String name) {
        DBCollection collection = collectionCache.get(name);
        if (collection != null) {
            return collection;
        }

        collection = new DBCollection(name, this, documentCodec);
        if (mongo.getMongoClientOptions().getDbDecoderFactory() != DefaultDBDecoder.FACTORY) {
            collection.setDBDecoderFactory(mongo.getMongoClientOptions().getDbDecoderFactory());
        }
        if (mongo.getMongoClientOptions().getDbEncoderFactory() != DefaultDBEncoder.FACTORY) {
            collection.setDBEncoderFactory(mongo.getMongoClientOptions().getDbEncoderFactory());
        }
        DBCollection old = collectionCache.putIfAbsent(name, collection);
        return old != null ? old : collection;
    }

    /**
     * Drops this database. Removes all data on disk. Use with caution.
     *
     * @throws MongoException
     */
    public void dropDatabase() {
        executeCommand(new Document("dropDatabase", 1), null);
    }

    /**
     * Returns a collection matching a given string.
     *
     * @param s the name of the collection
     * @return the collection
     */
    public DBCollection getCollectionFromString(final String s) {
        return getCollection(s);
    }

    /**
     * Returns the name of this database.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns a set containing the names of all collections in this database.
     *
     * @return the names of collections in this database
     * @throws MongoException
     */
    public Set<String> getCollectionNames() {
        MongoNamespace namespacesCollection = new MongoNamespace(name, "system.namespaces");
        Find findAll = new Find().readPreference(org.mongodb.ReadPreference.primary());
        try {
            MongoCursor<Document> cursor = new QueryOperation<Document>(namespacesCollection, findAll, commandCodec, commandCodec,
                                                                        getBufferPool(), getSession(), false).execute();

            HashSet<String> collections = new HashSet<String>();
            int lengthOfDatabaseName = getName().length();
            while (cursor.hasNext()) {
                String collectionName = cursor.next().getString("name");
                if (!collectionName.contains("$")) {
                    String collectionNameWithoutDatabasePrefix = collectionName.substring(lengthOfDatabaseName + 1);
                    collections.add(collectionNameWithoutDatabasePrefix);
                }
            }
            return collections;
        } catch (org.mongodb.MongoException e) {
            throw mapException(e);
        }
    }

    /**
     * Creates a collection with a given name and options.
     * If the collection does not exist, a new collection is created.
     * <p/>
     * Possible options:
     * <ul>
     * <li>
     * <b>capped</b> ({@code boolean}) - Enables a collection cap.
     * False by default. If enabled, you must specify a size parameter.
     * </li>
     * <li>
     * <b>size</b> ({@code int}) - If capped is true, size specifies a maximum size in bytes for the capped collection.
     * When capped is false, you may use size to preallocate space.
     * </li>
     * <li>
     * <b>max</b> ({@code int}) -   Optional. Specifies a maximum "cap" in number of documents for capped collections.
     * You must also specify size when specifying max.
     * </li>
     * <p/>
     * </ul>
     * <p/>
     * Note that if the {@code options} parameter is {@code null},
     * the creation will be deferred to when the collection is written to.
     *
     * @param collectionName    the name of the collection to return
     * @param options options
     * @return the collection
     * @throws MongoException
     */
    public DBCollection createCollection(final String collectionName, final DBObject options) {
        CreateCollectionOptions createCollectionOptions = toCreateCollectionOptions(collectionName, options);
        executeCommand(createCollectionOptions.asDocument(), getReadPreference().toNew());
        return getCollection(collectionName);
    }

    private CreateCollectionOptions toCreateCollectionOptions(final String collectionName, final DBObject options) {
        if (options.get("size") != null && !(options.get("size") instanceof Number)) {
            throw new IllegalArgumentException("'size' should be Number");
        }
        if (options.get("max") != null && !(options.get("max") instanceof Number)) {
            throw new IllegalArgumentException("'max' should be Number");
        }
        if (options.get("capped") != null && !(options.get("capped") instanceof Boolean)) {
            throw new IllegalArgumentException("'capped' should be Boolean");
        }
        if (options.get("autoIndexId") != null && !(options.get("capped") instanceof Boolean)) {
            throw new IllegalArgumentException("'capped' should be Boolean");
        }

        boolean capped = false;
        boolean autoIndex = true;
        long sizeInBytes = 0;
        long maxDocuments = 0;
        if (options.get("capped") != null) {
            capped = (Boolean) options.get("capped");
        }
        if (options.get("size") != null) {
            sizeInBytes = ((Number) options.get("size")).longValue();
        }
        if (options.get("autoIndexId") != null) {
            autoIndex = (Boolean) options.get("autoIndexId");
        }
        if (options.get("max") != null) {
            maxDocuments = ((Number) options.get("max")).longValue();
        }
        return new CreateCollectionOptions(collectionName, capped, sizeInBytes, autoIndex, maxDocuments);
    }

    /**
     * Executes a database command. This method constructs a simple DBObject using cmd as the field name and {@code true} as its value, and
     * calls {@link DB#command(com.mongodb.DBObject) }
     *
     * @param cmd command to execute
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final String cmd) {
        return command(new BasicDBObject(cmd, Boolean.TRUE), 0, getReadPreference());
    }

    /**
     * Executes a database command.
     * This method constructs a simple dbobject and calls {@link DB#command(com.mongodb.DBObject, int)  }
     *
     * @param cmd     name of the command to be executed
     * @param options query options to use
     * @return result of the command execution
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final String cmd, final int options) {
        return command(new BasicDBObject(cmd, Boolean.TRUE), options, getReadPreference());
    }

    /**
     * Executes a database command.
     *
     * @param cmd document representing the command to execute
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd) {
        return command(cmd, 0, getReadPreference());
    }

    /**
     * Executes a database command.
     *
     * @param cmd     {@code DBObject} representation the command to be executed
     * @param options query options to use
     * @return result of the command execution
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd, final int options) {
        return command(cmd, options, getReadPreference());
    }

    /**
     * Executes a database command.
     *
     * @param cmd       dbobject representing the command to execute
     * @param options   query options to use
     * @param readPreference ReadPreferences for this command (nodes selection is the biggest part of this)
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd, final int options, final ReadPreference readPreference) {
        //TODO: options never used
        org.mongodb.CommandResult result;
        try {
            result = new CommandOperation(getName(),
                                          toDocument(cmd),
                                          readPreference.toNew(),
                                          commandCodec,
                                          commandCodec,
                                          getClusterDescription(),
                                          getBufferPool(),
                                          getSession(),
                                          false).execute();
        } catch (MongoCommandFailureException ex) {
            result = ex.getCommandResult();
        } catch (org.mongodb.MongoException ex) {
            throw mapException(ex);
        }
        return new CommandResult(result);
    }

    /**
     * Executes a database command.
     * This method calls {@link DB#command(com.mongodb.DBObject, int, com.mongodb.DBEncoder) } with 0 as query option.
     *
     * @param cmd     {@code DBObject} representation of the command to be executed
     * @param encoder {@link DBEncoder} to be used for command encoding
     * @return result of the command execution
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd, final DBEncoder encoder) {
        return command(cmd, 0, getReadPreference(), encoder);
    }

    /**
     * Executes a database command.
     * This method calls {@link DB#command(com.mongodb.DBObject, int, com.mongodb.ReadPreference,
     * com.mongodb.DBEncoder) } with a null readPreference.
     *
     * @param cmd     {@code DBObject} representation the command to be executed
     * @param options query options to use
     * @param encoder {@link DBEncoder} to be used for command encoding
     * @return result of the command execution
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd, final int options, final DBEncoder encoder) {
        return command(cmd, options, getReadPreference(), encoder);
    }

    /**
     * Executes a database command.
     *
     * @param cmd       {@code DBObject} representation the command to be executed
     * @param options   query options to use
     * @param readPreference {@link ReadPreference} for this command (nodes selection is the biggest part of this)
     * @param encoder   {@link DBEncoder} to be used for command encoding
     * @return result of the command execution
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands Commands
     */
    public CommandResult command(final DBObject cmd, final int options, final ReadPreference readPreference,
                                 final DBEncoder encoder) {
        //TODO: options never used.  See JAVA-1063
        Document document = encoder != null ? toDocument(cmd, encoder, commandCodec) : toDocument(cmd);
        org.mongodb.CommandResult result;
        try {
            result = new CommandOperation(getName(),
                                          document,
                                          readPreference.toNew(),
                                          commandCodec,
                                          commandCodec,
                                          getClusterDescription(),
                                          getBufferPool(),
                                          getSession(),
                                          false).execute();
        } catch (MongoCommandFailureException ex) {
            result = ex.getCommandResult();
        } catch (org.mongodb.MongoException ex) {
            throw mapException(ex);
        }
        return new CommandResult(result);
    }

    /**
     * Gets another database on same server
     *
     * @param name name of the database
     * @return the DB for the given name
     */
    public DB getSisterDB(final String name) {
        return mongo.getDB(name);
    }

    /**
     * Checks to see if a collection with a given name exists on a server.
     *
     * @param collectionName a name of the collection to test for existence
     * @return {@code false} if no collection by that name exists, {@code true} if a match to an existing collection was found
     * @throws MongoException
     */
    public boolean collectionExists(final String collectionName) {
        Set<String> collectionNames = getCollectionNames();
        for (final String name : collectionNames) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates JavaScript functions on the database server.
     * This is useful if you need to touch a lot of data lightly, in which case network transfer could be a bottleneck.
     *
     * @param code @{code String} representation of JavaScript function
     * @param args arguments to pass to the JavaScript function
     * @return result of the command execution
     * @throws MongoException
     */
    public CommandResult doEval(final String code, final Object... args) {
        Document commandDocument = new Document("$eval", code).append("args", args);
        return new CommandResult(executeCommand(commandDocument, getReadPreference().toNew()));
    }

    /**
     * Calls {@link DB#doEval(java.lang.String, java.lang.Object[]) }.
     * If the command is successful, the "retval" field is extracted and returned.
     * Otherwise an exception is thrown.
     *
     * @param code @{code String} representation of JavaScript function
     * @param args arguments to pass to the JavaScript function
     * @return result of the execution
     * @throws MongoException
     */
    public Object eval(final String code, final Object... args) {
        CommandResult result = doEval(code, args);
        result.throwOnError();
        return result.get("retval");
    }

    /**
     * Helper method for calling a 'dbStats' command.
     * It returns storage statistics for a given database.
     *
     * @return result of the execution
     * @throws MongoException
     */
    public CommandResult getStats() {
        Document commandDocument = new Document("dbStats", 1).append("scale", 1);
        return new CommandResult(executeCommand(commandDocument, getReadPreference().toNew()));
    }

    /**
     * Adds or updates a user for this database
     *
     * @param userName the user name
     * @param password the password
     * @throws MongoException
     * @deprecated Use {@code DB.command} to call either the addUser or updateUser command
     * @mongodb.driver.manual reference/command/nav-user-role/  User manipulation commands
     */
    @Deprecated
    public WriteResult addUser(final String userName, final char[] password) {
        return addUser(userName, password, false);
    }

    /**
     * Adds or updates a user for this database
     *
     * @param userName the user name
     * @param password the password
     * @param readOnly if true, user will only be able to read
     * @throws MongoException
     * @deprecated Use {@code DB.command} to call either the addUser or updateUser command
     * @mongodb.driver.manual reference/command/nav-user-role/  User manipulation commands
     */
    @Deprecated
    public WriteResult addUser(final String userName, final char[] password, final boolean readOnly) {
        User user = new User(createMongoCRCredential(userName, getName(), password), readOnly);
        org.mongodb.WriteResult writeResult;
        if (new UserExistsOperation(getName(), userName, getBufferPool(), getSession(), true).execute()) {
            writeResult = new UpdateUserOperation(user, getBufferPool(), getSession(), true).execute();
        } else {
            writeResult = new CreateUserOperation(user, getBufferPool(), getSession(), true).execute();
        }
        return new WriteResult(new CommandResult(writeResult.getCommandResult()), getWriteConcern());
    }

    /**
     * Removes the specified user from the database.
     *
     * @param userName user to be removed
     * @throws MongoException
     * @deprecated Use {@code DB.command} to call the dropUser command
     * @mongodb.driver.manual reference/command/nav-user-role/  User manipulation commands
     */
    @Deprecated
    public WriteResult removeUser(final String userName) {
        org.mongodb.WriteResult writeResult = new DropUserOperation(getName(), userName, getBufferPool(), getSession(), true).execute();
        return new WriteResult(new CommandResult(writeResult.getCommandResult()), getWriteConcern());
    }

    /**
     * Makes it possible to execute "read" queries on a slave node
     *
     * @see ReadPreference#secondaryPreferred()
     * @deprecated Replaced with {@code ReadPreference.secondaryPreferred()}
     */
    @Deprecated
    public void slaveOk() {
        addOption(Bytes.QUERYOPTION_SLAVEOK);
    }

    /**
     * Adds the given flag to the query options.
     *
     * @param option value to be added
     */
    public void addOption(final int option) {
        optionHolder.add(option);
    }

    /**
     * Sets the query options, overwriting previous value.
     *
     * @param options bit vector of query options
     */
    public void setOptions(final int options) {
        optionHolder.set(options);
    }

    /**
     * Resets the query options.
     */
    public void resetOptions() {
        optionHolder.reset();
    }

    /**
     * Gets the query options
     *
     * @return bit vector of query options
     */
    public int getOptions() {
        return optionHolder.get();
    }

    @Override
    public String toString() {
        return "DB{name='" + name + '\'' + '}';
    }

    Cluster getCluster() {
        return getMongo().getCluster();
    }

    Session getSession() {
        return getMongo().getSession();
    }

    org.mongodb.CommandResult executeCommand(final Document commandDocument, final org.mongodb.ReadPreference requestedReadPreference) {
        try {
            return new CommandOperation(getName(), commandDocument, requestedReadPreference, commandCodec, commandCodec,
                                        getClusterDescription(), getBufferPool(), getSession(), false).execute();
        } catch (org.mongodb.MongoException e) {
            throw mapException(e);
        }
    }

    ClusterDescription getClusterDescription() {
        try {
            return getCluster().getDescription();
        } catch (org.mongodb.MongoException e) {
            throw mapException(e);
        }
    }

    Bytes.OptionHolder getOptionHolder() {
        return optionHolder;
    }

    BufferProvider getBufferPool() {
        return getMongo().getBufferProvider();
    }
}
