package com.cgi.privsense.dbscanner.model;

import com.cgi.privsense.common.model.BaseMetaData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the metadata of a database table.
 * Encapsulates all structural information of a table, including its columns.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class TableMetadata extends BaseMetaData {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Catalog to which the table belongs.
     */
    private String catalog;

    /**
     * Schema to which the table belongs.
     */
    private String schema;

    /**
     * Table type (TABLE, VIEW, etc.).
     */
    private String type;

    /**
     * Approximate number of rows in the table.
     */
    private Long approximateRowCount;

    /**
     * List of columns in the table.
     */
    private List<ColumnMetadata> columns = new ArrayList<>();

    /**
     * Default constructor.
     */
    public TableMetadata() {
        this.columns = new ArrayList<>();
    }

    /**
     * Constructor with table name.
     *
     * @param name Table name
     */
    public TableMetadata(String name) {
        this();
        this.name = name;
    }

    /**
     * Constructor with all basic properties.
     *
     * @param name Table name
     * @param catalog Catalog name
     * @param schema Schema name
     * @param type Table type
     * @param comment Table comment
     */
    public TableMetadata(String name, String catalog, String schema, String type, String comment) {
        this();
        this.name = name;
        this.catalog = catalog;
        this.schema = schema;
        this.type = type;
        this.comment = comment;
    }

    /**
     * Adds a column to the table.
     *
     * @param column Column metadata
     */
    public void addColumn(ColumnMetadata column) {
        this.columns.add(column);
    }

    /**
     * Gets the full name of the table including schema if available.
     *
     * @return Full table name
     */
    public String getFullName() {
        if (schema != null && !schema.isEmpty()) {
            return schema + "." + name;
        }
        return name;
    }

    /**
     * Gets a summary of the table metadata.
     *
     * @return Summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(getFullName());

        if (type != null && !type.isEmpty()) {
            sb.append(" (").append(type).append(")");
        }

        if (approximateRowCount != null && approximateRowCount > 0) {
            sb.append(", ~").append(approximateRowCount).append(" rows");
        }

        if (columns != null && !columns.isEmpty()) {
            sb.append(", ").append(columns.size()).append(" columns");
        }

        return sb.toString();
    }
}