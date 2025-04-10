package com.cgi.privsense.dbscanner.scanner;

import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.core.scanner.AbstractDatabaseScanner;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseType;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.RelationshipMetadata;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Scanner implementation for MySQL databases.
 * Optimized implementation for scanning and introspecting MySQL databases.
 */
@Component
@DatabaseType("mysql")
public class MySQLDatabaseScanner extends AbstractDatabaseScanner {
    // SQL constants for reuse with optimized queries
    private static final String SQL_SCAN_TABLES = """
        SELECT 
            t.TABLE_NAME,
            t.TABLE_SCHEMA as TABLE_CATALOG,
            t.TABLE_SCHEMA as `SCHEMA`,
            t.TABLE_TYPE as TYPE,
            t.TABLE_COMMENT as REMARKS,
            t.TABLE_ROWS as ROW_COUNT,
            t.AUTO_INCREMENT,
            t.CREATE_TIME,
            t.UPDATE_TIME,
            t.ENGINE,
            GROUP_CONCAT(DISTINCT c.COLUMN_NAME) as COLUMNS,
            GROUP_CONCAT(DISTINCT k.REFERENCED_TABLE_NAME) as REFERENCED_TABLES
        FROM information_schema.TABLES t
        LEFT JOIN information_schema.COLUMNS c 
            ON t.TABLE_SCHEMA = c.TABLE_SCHEMA 
            AND t.TABLE_NAME = c.TABLE_NAME
        LEFT JOIN information_schema.KEY_COLUMN_USAGE k
            ON t.TABLE_SCHEMA = k.TABLE_SCHEMA 
            AND t.TABLE_NAME = k.TABLE_NAME
            AND k.REFERENCED_TABLE_NAME IS NOT NULL
        WHERE t.TABLE_SCHEMA = database()
        GROUP BY t.TABLE_NAME, t.TABLE_SCHEMA, t.TABLE_TYPE, 
                t.TABLE_COMMENT, t.TABLE_ROWS, t.AUTO_INCREMENT,
                t.CREATE_TIME, t.UPDATE_TIME, t.ENGINE
    """;

    // Specific query for a single table
    private static final String SQL_SCAN_TABLE = """
        SELECT 
            t.TABLE_NAME,
            t.TABLE_SCHEMA as TABLE_CATALOG,
            t.TABLE_SCHEMA as `SCHEMA`,
            t.TABLE_TYPE as TYPE,
            t.TABLE_COMMENT as REMARKS,
            t.TABLE_ROWS as ROW_COUNT,
            t.AUTO_INCREMENT,
            t.CREATE_TIME,
            t.UPDATE_TIME,
            t.ENGINE,
            GROUP_CONCAT(DISTINCT c.COLUMN_NAME) as COLUMNS,
            GROUP_CONCAT(DISTINCT k.REFERENCED_TABLE_NAME) as REFERENCED_TABLES
        FROM information_schema.TABLES t
        LEFT JOIN information_schema.COLUMNS c 
            ON t.TABLE_SCHEMA = c.TABLE_SCHEMA 
            AND t.TABLE_NAME = c.TABLE_NAME
        LEFT JOIN information_schema.KEY_COLUMN_USAGE k
            ON t.TABLE_SCHEMA = k.TABLE_SCHEMA 
            AND t.TABLE_NAME = k.TABLE_NAME
            AND k.REFERENCED_TABLE_NAME IS NOT NULL
        WHERE t.TABLE_SCHEMA = database() AND t.TABLE_NAME = ?
        GROUP BY t.TABLE_NAME, t.TABLE_SCHEMA, t.TABLE_TYPE, 
                t.TABLE_COMMENT, t.TABLE_ROWS, t.AUTO_INCREMENT,
                t.CREATE_TIME, t.UPDATE_TIME, t.ENGINE
    """;

    // Query for columns with foreign key information
    private static final String SQL_SCAN_COLUMNS = """
        SELECT 
            c.COLUMN_NAME,
            c.DATA_TYPE,
            c.CHARACTER_MAXIMUM_LENGTH,
            c.COLUMN_COMMENT,
            c.IS_NULLABLE,
            c.COLUMN_KEY,
            c.COLUMN_DEFAULT,
            c.ORDINAL_POSITION,
            CASE 
                WHEN k.REFERENCED_TABLE_NAME IS NOT NULL THEN true 
                ELSE false 
            END as IS_FOREIGN_KEY,
            k.REFERENCED_TABLE_NAME,
            k.REFERENCED_COLUMN_NAME
        FROM information_schema.COLUMNS c
        LEFT JOIN information_schema.KEY_COLUMN_USAGE k 
            ON c.TABLE_SCHEMA = k.TABLE_SCHEMA 
            AND c.TABLE_NAME = k.TABLE_NAME 
            AND c.COLUMN_NAME = k.COLUMN_NAME 
            AND k.REFERENCED_TABLE_NAME IS NOT NULL
        WHERE c.TABLE_SCHEMA = database() 
        AND c.TABLE_NAME = ?
        ORDER BY c.ORDINAL_POSITION
    """;

    // Improved UNION query for both outgoing and incoming relationships
    private static final String SQL_GET_ALL_RELATIONSHIPS = """
        -- Outgoing relationships (FKs from this table to others)
        SELECT 
            tc.CONSTRAINT_NAME,
            'OUTGOING' as RELATIONSHIP_DIRECTION,
            tc.CONSTRAINT_TYPE,
            tc.TABLE_NAME as SOURCE_TABLE,
            kcu.REFERENCED_TABLE_NAME as TARGET_TABLE,
            kcu.REFERENCED_TABLE_SCHEMA as TARGET_SCHEMA,
            rc.UPDATE_RULE,
            rc.DELETE_RULE,
            kcu.COLUMN_NAME as SOURCE_COLUMN,
            kcu.REFERENCED_COLUMN_NAME as TARGET_COLUMN
        FROM information_schema.TABLE_CONSTRAINTS tc
        JOIN information_schema.KEY_COLUMN_USAGE kcu
            ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
            AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
        LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
            ON tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
            AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
        WHERE tc.TABLE_SCHEMA = database()
        AND tc.TABLE_NAME = ?
        AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
        
        UNION ALL
        
        -- Incoming relationships (FKs from other tables to this one)
        SELECT 
            tc.CONSTRAINT_NAME,
            'INCOMING' as RELATIONSHIP_DIRECTION,
            tc.CONSTRAINT_TYPE,
            tc.TABLE_NAME as SOURCE_TABLE,
            ? as TARGET_TABLE,
            database() as TARGET_SCHEMA,
            rc.UPDATE_RULE,
            rc.DELETE_RULE,
            kcu.COLUMN_NAME as SOURCE_COLUMN,
            kcu.REFERENCED_COLUMN_NAME as TARGET_COLUMN
        FROM information_schema.TABLE_CONSTRAINTS tc
        JOIN information_schema.KEY_COLUMN_USAGE kcu
            ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
            AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
        LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
            ON tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
            AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
        WHERE tc.TABLE_SCHEMA = database()
        AND kcu.REFERENCED_TABLE_SCHEMA = database()
        AND kcu.REFERENCED_TABLE_NAME = ?
        AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
    """;

    /**
     * Constructor with customized JDBC settings for MySQL.
     *
     * @param dataSource The data source
     */
    public MySQLDatabaseScanner(DataSource dataSource) {
        super(dataSource, "mysql", JdbcSettings.builder()
                .fetchSize(500)
                .maxRows(5000)
                .queryTimeoutSeconds(60)
                .build());
    }

    /**
     * Scans all tables in the database.
     *
     * @return List of table metadata
     */
    @Override
    public List<TableMetadata> scanTables() {
        return executeQuery("scanTables", jdbc -> jdbc.query(SQL_SCAN_TABLES, this::mapTableMetadata));
    }

    /**
     * Maps a result set row to table metadata.
     *
     * @param rs Result set
     * @param rowNum Row number
     * @return Table metadata
     * @throws SQLException On SQL error
     */
    private TableMetadata mapTableMetadata(ResultSet rs, int rowNum) throws SQLException {
        TableMetadata metadata = new TableMetadata();
        metadata.setName(rs.getString("TABLE_NAME"));
        metadata.setCatalog(rs.getString("TABLE_CATALOG"));
        metadata.setSchema(rs.getString("SCHEMA"));
        metadata.setType(rs.getString("TYPE"));
        metadata.setComment(rs.getString("REMARKS"));
        metadata.setApproximateRowCount(rs.getLong("ROW_COUNT"));

        // Additional metadata
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("engine", rs.getString("ENGINE"));
        additionalInfo.put("autoIncrement", rs.getLong("AUTO_INCREMENT"));
        additionalInfo.put("createTime", rs.getTimestamp("CREATE_TIME"));
        additionalInfo.put("updateTime", rs.getTimestamp("UPDATE_TIME"));
        additionalInfo.put("referencedTables",
                Optional.ofNullable(rs.getString("REFERENCED_TABLES"))
                        .map(s -> Arrays.asList(s.split(",")))
                        .orElse(Collections.emptyList()));

        metadata.setAdditionalInfo(additionalInfo);

        return metadata;
    }

    /**
     * Scans columns of a table.
     *
     * @param tableName Table name
     * @return List of column metadata
     */
    @Override
    public List<ColumnMetadata> scanColumns(String tableName) {
        DatabaseUtils.validateTableName(tableName);
        return executeQuery("scanColumns", jdbc ->
                jdbc.query(SQL_SCAN_COLUMNS, (rs, rowNum) -> mapColumnMetadata(rs, tableName), tableName)
        );
    }

    /**
     * Maps a result set row to column metadata.
     *
     * @param rs Result set
     * @param tableName Table name
     * @return Column metadata
     * @throws SQLException On SQL error
     */
    private ColumnMetadata mapColumnMetadata(ResultSet rs, String tableName) throws SQLException {
        ColumnMetadata metadata = new ColumnMetadata();
        metadata.setName(rs.getString("COLUMN_NAME"));
        metadata.setType(rs.getString("DATA_TYPE"));
        metadata.setMaxLength(rs.getLong("CHARACTER_MAXIMUM_LENGTH"));
        metadata.setComment(rs.getString("COLUMN_COMMENT"));
        metadata.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
        metadata.setPrimaryKey("PRI".equals(rs.getString("COLUMN_KEY")));
        metadata.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
        metadata.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
        metadata.setTableName(tableName);
        metadata.setForeignKey(rs.getBoolean("IS_FOREIGN_KEY"));
        metadata.setReferencedTable(rs.getString("REFERENCED_TABLE_NAME"));
        metadata.setReferencedColumn(rs.getString("REFERENCED_COLUMN_NAME"));
        return metadata;
    }

    /**
     * Scans a specific table.
     *
     * @param tableName Table name
     * @return Table metadata
     */
    @Override
    public TableMetadata scanTable(String tableName) {
        DatabaseUtils.validateTableName(tableName);
        return executeQuery("scanTable", jdbc -> {
            List<TableMetadata> tables = jdbc.query(
                    SQL_SCAN_TABLE,
                    this::mapTableMetadata,
                    tableName
            );

            if (tables.isEmpty()) {
                throw DatabaseOperationException.scannerError("Table not found: " + tableName);
            }

            TableMetadata table = tables.getFirst();
            table.setColumns(scanColumns(tableName));
            return table;
        });
    }

    /**
     * Gets detailed relationships for a table, including both outgoing and incoming relationships.
     * Uses a single, optimized UNION query.
     *
     * @param tableName Table name
     * @return List of relationship metadata
     */
    @Override
    public List<RelationshipMetadata> getTableRelationships(String tableName) {
        DatabaseUtils.validateTableName(tableName);
        logger.info("Retrieving relationships for table: {}", tableName);

        return executeQuery("getTableRelationships", jdbc -> {
            // Execute the UNION query with parameters for both parts
            List<Map<String, Object>> results = jdbc.queryForList(
                    SQL_GET_ALL_RELATIONSHIPS,
                    tableName, // For outgoing relationships
                    tableName, // For incoming relationships (as target)
                    tableName  // For incoming relationships (as target)
            );

            logger.info("Found {} relationships for table {}", results.size(), tableName);

            // Process results into relationship metadata objects
            Map<String, RelationshipMetadata> relationshipsMap = new HashMap<>();

            for (Map<String, Object> row : results) {
                String constraintName = (String) row.get("CONSTRAINT_NAME");
                String direction = (String) row.get("RELATIONSHIP_DIRECTION");
                String directionKey = direction + "_" + constraintName;

                logger.debug("Processing {} relationship: {}", direction.toLowerCase(), constraintName);

                // Get existing relationship or create new one
                RelationshipMetadata rel = relationshipsMap.computeIfAbsent(directionKey, k -> {
                    RelationshipMetadata newRel = new RelationshipMetadata();
                    newRel.setName(constraintName);
                    newRel.setConstraintType((String) row.get("CONSTRAINT_TYPE"));
                    newRel.setSourceTable((String) row.get("SOURCE_TABLE"));
                    newRel.setTargetTable((String) row.get("TARGET_TABLE"));
                    newRel.setTargetSchema((String) row.get("TARGET_SCHEMA"));
                    newRel.setUpdateRule((String) row.get("UPDATE_RULE"));
                    newRel.setDeleteRule((String) row.get("DELETE_RULE"));
                    newRel.setDirection(direction);
                    return newRel;
                });

                // Add column mapping
                rel.addColumnMapping(
                        (String) row.get("SOURCE_COLUMN"),
                        (String) row.get("TARGET_COLUMN")
                );
            }

            return new ArrayList<>(relationshipsMap.values());
        });
    }

    /**
     * Implements MySQL-specific SQL for sampling table data.
     * Uses MySQL LIMIT syntax.
     *
     * @param tableName Table name
     * @return SQL string for sampling
     */
    @Override
    protected String buildSampleTableSql(String tableName) {
        return String.format("SELECT * FROM %s LIMIT ?", escapeIdentifier(tableName));
    }

    /**
     * Implements MySQL-specific SQL for sampling column data.
     * Uses MySQL LIMIT syntax.
     *
     * @param tableName  Table name
     * @param columnName Column name
     * @return SQL string for sampling column data
     */
    @Override
    protected String buildSampleColumnSql(String tableName, String columnName) {
        return String.format("SELECT %s FROM %s LIMIT ?",
                escapeIdentifier(columnName),
                escapeIdentifier(tableName));
    }

    /**
     * Optimized implementation for MySQL-specific prepared statements.
     * Uses MySQL's streaming mode for efficient large result set handling.
     */
    @Override
    protected PreparedStatement prepareSampleTableStatement(Connection connection, String tableName, int limit)
            throws SQLException {
        try {
            // MySQL-specific optimizations for sampling
            String sql = buildSampleTableSql(tableName);
            PreparedStatement stmt = connection.prepareStatement(
                    sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            );
            // Set to streaming mode for MySQL (server-side cursors)
            stmt.setFetchSize(Integer.MIN_VALUE);
            stmt.setInt(1, limit);
            return stmt;
        } catch (SQLException e) {
            throw DatabaseOperationException.scannerError("Error preparing sample statement for table: " + tableName, e);
        }
    }

    /**
     * Optimized implementation for MySQL-specific column sampling.
     */
    @Override
    protected PreparedStatement prepareSampleColumnStatement(Connection connection, String tableName,
                                                             String columnName, int limit)
            throws SQLException {
        try {
            String sql = buildSampleColumnSql(tableName, columnName);
            PreparedStatement stmt = connection.prepareStatement(
                    sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            );
            // Set to streaming mode for MySQL
            stmt.setFetchSize(Integer.MIN_VALUE);
            stmt.setInt(1, limit);
            return stmt;
        } catch (SQLException e) {
            throw DatabaseOperationException.scannerError("Error preparing sample statement for column: " + columnName, e);
        }
    }
}