package com.cgi.privsense.dbscanner.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class ColumnMetadata extends BaseMetaData{
    private String type;
    private Long maxLength;
    private boolean nullable;
    private boolean primaryKey;
    private String defaultValue;
    private int ordinalPosition;
    private String tableName;
    private boolean foreignKey;
}
