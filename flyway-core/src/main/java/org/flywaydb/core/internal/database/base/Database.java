/*
 * Copyright 2010-2019 Boxfuse GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.internal.database.base;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.callback.NoopCallbackExecutor;
import org.flywaydb.core.internal.exception.FlywayDbUpgradeRequiredException;
import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.jdbc.DatabaseType;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.flywaydb.core.internal.jdbc.JdbcUtils;
import org.flywaydb.core.internal.license.Edition;
import org.flywaydb.core.internal.license.FlywayEditionUpgradeRequiredException;
import org.flywaydb.core.internal.resource.LoadableResource;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.core.internal.sqlscript.DefaultSqlScriptExecutor;
import org.flywaydb.core.internal.sqlscript.Delimiter;
import org.flywaydb.core.internal.sqlscript.SqlScript;
import org.flywaydb.core.internal.sqlscript.SqlScriptExecutor;
import org.flywaydb.core.internal.sqlscript.SqlScriptFactory;
import org.flywaydb.core.internal.util.ExceptionUtils;

import java.io.Closeable;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction for database-specific functionality.
 */
public abstract class Database<C extends Connection> implements Closeable, SqlScriptFactory {
    private static final Log LOG = LogFactory.getLog(Database.class);

    /**
     * The type of database this is.
     */
    protected final DatabaseType databaseType;

    /**
     * The Flyway configuration.
     */
    protected final Configuration configuration;

    /**
     * The JDBC metadata to use.
     */
    protected final DatabaseMetaData jdbcMetaData;

    /**
     * The main JDBC connection to use.
     */
    private final java.sql.Connection mainJdbcConnection;

    /**
     * The original auto-commit state for connections to this database.
     */
    protected final boolean originalAutoCommit;

    /**
     * The main connection to use.
     */
    private C mainConnection;

    /**
     * The connection to use for migrations.
     */
    private C migrationConnection;










    /**
     * The major.minor version of the database.
     */
    private final MigrationVersion version;

    /**
     * Creates a new Database instance with this JdbcTemplate.
     *
     * @param configuration      The Flyway configuration.
     * @param connection         The main connection to use.
     * @param originalAutoCommit The original auto-commit state for connections to this database.
     */
    public Database(Configuration configuration, java.sql.Connection connection,
                    boolean originalAutoCommit



    ) {
        this.databaseType = DatabaseType.fromJdbcConnection(connection);
        this.configuration = configuration;
        this.mainJdbcConnection = connection;
        this.originalAutoCommit = originalAutoCommit;
        try {
            this.jdbcMetaData = connection.getMetaData();
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to get metadata for connection", e);
        }





        version = determineVersion();
    }

    /**
     * Retrieves a Flyway Connection for this JDBC connection.
     *
     * @param connection The JDBC connection to wrap.
     * @return The Flyway Connection.
     */
    protected abstract C getConnection(java.sql.Connection connection



    );

    /**
     * Ensures Flyway supports this version of this database.
     */
    public abstract void ensureSupported();

    /**
     * @return The major.minor version of the database.
     */
    public final MigrationVersion getVersion() {
        return version;
    }

    protected final void ensureDatabaseIsRecentEnough(String oldestSupportedVersion) {
        if (!version.isAtLeast(oldestSupportedVersion)) {
            throw new FlywayDbUpgradeRequiredException(databaseType, computeVersionDisplayName(version),
                    computeVersionDisplayName(MigrationVersion.fromVersion(oldestSupportedVersion)));
        }
    }

    /**
     * Ensures this database it at least at recent as this version otherwise suggest upgrade to this higher edition of
     * Flyway.
     *
     * @param oldestSupportedVersionInThisEdition The oldest supported version of the database by this edition of Flyway.
     * @param editionWhereStillSupported          The edition of Flyway that still supports this version of the database,
     *                                            in case it's too old.
     */
    protected final void ensureDatabaseNotOlderThanOtherwiseRecommendUpgradeToFlywayEdition(String oldestSupportedVersionInThisEdition,
                                                                                            Edition editionWhereStillSupported) {
        if (!version.isAtLeast(oldestSupportedVersionInThisEdition)) {
            throw new FlywayEditionUpgradeRequiredException(
                    editionWhereStillSupported,
                    databaseType,
                    computeVersionDisplayName(version));
        }
    }

    protected final void recommendFlywayUpgradeIfNecessary(String newestSupportedVersion) {
        if (version.isNewerThan(newestSupportedVersion)) {
            LOG.warn("Flyway upgrade recommended: " + databaseType + " " + computeVersionDisplayName(version)
                    + " is newer than this version of Flyway and support has not been tested.");
        }
    }

    protected final void recommendFlywayUpgradeIfNecessaryForMajorVersion(String newestSupportedVersion) {
        if (version.isMajorNewerThan(newestSupportedVersion)) {
            LOG.warn("Flyway upgrade recommended: " + databaseType + " " + computeVersionDisplayName(version)
                    + " is newer than this version of Flyway and support has not been tested.");
        }
    }

    /**
     * Compute the user-friendly display name for this database version.
     *
     * @return The user-friendly display name.
     */
    protected String computeVersionDisplayName(MigrationVersion version) {
        return version.getVersion();
    }

    /**
     * Creates a new SqlScriptExecutor for this specific database.
     * <p>




     * @return The new SqlScriptExecutor.
     */
    public SqlScriptExecutor createSqlScriptExecutor(JdbcTemplate jdbcTemplate



    ) {
        return new DefaultSqlScriptExecutor(jdbcTemplate



        );
    }

    /**
     * @return The default delimiter for this database.
     */
    public Delimiter getDefaultDelimiter() {
        return Delimiter.SEMICOLON;
    }

    /**
     * @return The current database user.
     */
    public final String getCurrentUser() {
        try {
            return doGetCurrentUser();
        } catch (SQLException e) {
            throw new FlywaySqlException("Error retrieving the database user", e);
        }
    }

    protected String doGetCurrentUser() throws SQLException {
        return jdbcMetaData.getUserName();
    }

    /**
     * Checks whether DDL transactions are supported by this database.
     *
     * @return {@code true} if DDL transactions are supported, {@code false} if not.
     */
    public abstract boolean supportsDdlTransactions();

    /**
     * @return {@code true} if this database supports changing a connection's current schema. {@code false if not}.
     */
    public abstract boolean supportsChangingCurrentSchema();



























    /**
     * @return The representation of the value {@code true} in a boolean column.
     */
    public abstract String getBooleanTrue();

    /**
     * @return The representation of the value {@code false} in a boolean column.
     */
    public abstract String getBooleanFalse();

    /**
     * Quote these identifiers for use in sql queries. Multiple identifiers will be quoted and separated by a dot.
     *
     * @param identifiers The identifiers to quote.
     * @return The fully qualified quoted identifiers.
     */
    public final String quote(String... identifiers) {
        StringBuilder result = new StringBuilder();

        boolean first = true;
        for (String identifier : identifiers) {
            if (!first) {
                result.append(".");
            }
            first = false;
            result.append(doQuote(identifier));
        }

        return result.toString();
    }

    /**
     * Quote this identifier for use in sql queries.
     *
     * @param identifier The identifier to quote.
     * @return The fully qualified quoted identifier.
     */
    protected abstract String doQuote(String identifier);

    /**
     * @return {@code true} if this database use a catalog to represent a schema. {@code false} if a schema is simply a schema.
     */
    public abstract boolean catalogIsSchema();

    /**
     * @return Whether to only use a single connection for both schema history table management and applying migrations.
     */
    public boolean useSingleConnection() {
        return false;
    }

    public DatabaseMetaData getJdbcMetaData() {
        return jdbcMetaData;
    }

    /**
     * @return The main connection, used to manipulate the schema history.
     */
    public final C getMainConnection() {
        if (mainConnection == null) {
            initConnection(mainJdbcConnection, configuration.getInitSql());
            this.mainConnection = getConnection(mainJdbcConnection



            );
        }
        return mainConnection;
    }

    /**
     * @return The migration connection, used to apply migrations.
     */
    public final C getMigrationConnection() {
        if (migrationConnection == null) {
            if (useSingleConnection()) {
                this.migrationConnection = mainConnection;
            } else {
                java.sql.Connection migrationJdbcConnection =
                        JdbcUtils.openConnection(configuration.getDataSource(), configuration.getConnectRetries());
                initConnection(migrationJdbcConnection, configuration.getInitSql());
                this.migrationConnection = getConnection(migrationJdbcConnection



                );
            }
        }
        return migrationConnection;
    }

    /**
     * Initializes this connection with these sql statements.
     *
     * @param connection The connection.
     * @param initSql    The sql statements.
     */
    private void initConnection(java.sql.Connection connection, String initSql) {
        if (initSql == null) {
            return;
        }
        StringResource resource = new StringResource(initSql);

        SqlScript sqlScript = createSqlScript(resource, true



        );
        new DefaultSqlScriptExecutor(new JdbcTemplate(connection)



        ).execute(sqlScript);
    }

    /**
     * @return The major and minor version of the database.
     */
    protected MigrationVersion determineVersion() {
        try {
            return MigrationVersion.fromVersion(jdbcMetaData.getDatabaseMajorVersion() + "." + jdbcMetaData.getDatabaseMinorVersion());
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to determine the major version of the database", e);
        }
    }

    public final SqlScript getCreateScript(Table table) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("schema", table.getSchema().getName());
        placeholders.put("table", table.getName());
        placeholders.put("table_quoted", table.toString());

        return getCreateScript(placeholders);
    }

    protected abstract SqlScript getCreateScript(Map<String, String> placeholders);

    protected abstract LoadableResource getRawCreateScript();

    public String getInsertStatement(Table table) {
        return "INSERT INTO " + table
                + " (" + quote("installed_rank")
                + "," + quote("version")
                + "," + quote("description")
                + "," + quote("type")
                + "," + quote("script")
                + "," + quote("checksum")
                + "," + quote("installed_by")
                + "," + quote("execution_time")
                + "," + quote("success")
                + ")"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getSelectStatement(Table table, int maxCachedInstalledRank) {
        return "SELECT " + quote("installed_rank")
                + "," + quote("version")
                + "," + quote("description")
                + "," + quote("type")
                + "," + quote("script")
                + "," + quote("checksum")
                + "," + quote("installed_on")
                + "," + quote("installed_by")
                + "," + quote("execution_time")
                + "," + quote("success")
                + " FROM " + table
                + " WHERE " + quote("installed_rank") + " > " + maxCachedInstalledRank
                + " ORDER BY " + quote("installed_rank");
    }

    public void close() {
        if (!useSingleConnection() && migrationConnection != null) {
            migrationConnection.close();
        }
        if (mainConnection != null) {
            mainConnection.close();
        }
    }
}