package com.cgi.privsense.dbscanner.scanner;

import com.cgi.privsense.dbscanner.core.AbstractDatabaseScanner;
import com.cgi.privsense.dbscanner.core.DatabaseType;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
@DatabaseType("mysql")
public class MySQLDatabaseScanner extends AbstractDatabaseScanner {

    public MySQLDatabaseScanner(DataSource dataSource) {
        super(dataSource, "mysql");
    }

    @Override
    public List<TableMetadata> scanTables() {
        return executeQuery("scanTables", jdbc -> {
            String sql = """
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
            return jdbc.query(sql, this::mapTableMetadata);
        });
    }

    private TableMetadata mapTableMetadata(ResultSet rs, int rowNum) throws SQLException {
        TableMetadata metadata = new TableMetadata();
        metadata.setName(rs.getString("TABLE_NAME"));
        metadata.setCatalog(rs.getString("TABLE_CATALOG"));
        metadata.setSchema(rs.getString("SCHEMA"));
        metadata.setType(rs.getString("TYPE"));
        metadata.setComment(rs.getString("REMARKS"));
        metadata.setApproximateRowCount(rs.getLong("ROW_COUNT"));

        /*
         Ajout des métadonnées supplémentaires
         */
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
        metadata.setColumns(scanColumns(metadata.getName()));

        return metadata;
    }

    @Override
    public List<ColumnMetadata> scanColumns(String tableName) {
        validateTableName(tableName);
        return executeQuery("scanColumns", jdbc -> {
            String sql = """
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
                    END as IS_FOREIGN_KEY
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
            return jdbc.query(sql, (rs, rowNum) -> mapColumnMetadata(rs, tableName, rowNum), tableName);
        });
    }

    private ColumnMetadata mapColumnMetadata(ResultSet rs, String tableName, int rowNum) throws SQLException {
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
        return metadata;
    }

    @Override
    public TableMetadata scanTable(String tableName) {
        validateTableName(tableName);
        return executeQuery("scanTable", jdbc ->
                scanTables().stream()
                        .filter(table -> table.getName().equals(tableName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Table not found: " + tableName))
        );
    }

    @Override
    public DataSample sampleTableData(String tableName, int limit) {
        validateTableName(tableName);
        return executeQuery("sampleTableData", jdbc -> {
            String sql = "SELECT * FROM " + tableName + " LIMIT ?";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, limit);
            return new DataSample(tableName, rows);
        });
    }

    @Override
    public List<Object> sampleColumnData(String tableName, String columnName, int limit) {
        validateTableName(tableName);
        String sql = "SELECT " + columnName + " FROM " + tableName + " LIMIT ?";
        return jdbcTemplate.queryForList(sql, Object.class, limit);
    }

    @Override
    public Map<String, String> getTableRelations(String tableName) {
        String sql = """
            SELECT 
                COLUMN_NAME,
                REFERENCED_TABLE_NAME
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = ?
            AND REFERENCED_TABLE_NAME IS NOT NULL
        """;

        return jdbcTemplate.query(sql, (rs) -> {
            Map<String, String> relations = new HashMap<>();
            while (rs.next()) {
                relations.put(rs.getString("COLUMN_NAME"), rs.getString("REFERENCED_TABLE_NAME"));
            }
            return relations;
        }, tableName);
    }

    public List<String> getAllTables() {
        return jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                String.class
        );
    }

    public List<Map<String, Object>> getTableMetadata(String tableName) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()";

        return jdbcTemplate.queryForList(sql, tableName);
    }
}
