package com.cgi.privsense.dbscanner.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString(callSuper = true)
public class TableMetadata extends BaseMetaData{

    private String catalog;
    private String schema;
    private String type;
    private Long approximateRowCount;
    private List<ColumnMetadata> columns = new ArrayList<>();
    private Map<String, Object> additionalInfo = new HashMap<>();

    public TableMetadata() {
        this.columns = new ArrayList<>();
    }

    public TableMetadata(String name) {
        this();
        this.name = name;
    }

    public TableMetadata(String name, String catalog, String schema, String type, String comment) {
        this();
        this.name = name;
        this.catalog = catalog;
        this.schema = schema;
        this.type = type;
        this.comment = comment;
    }

    public void addColumn(ColumnMetadata column) {
        this.columns.add(column);
    }
}
