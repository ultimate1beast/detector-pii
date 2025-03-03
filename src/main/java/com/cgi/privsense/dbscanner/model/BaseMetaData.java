package com.cgi.privsense.dbscanner.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public abstract class BaseMetaData {
    protected String name;
    protected String comment;
    protected Map<String, Object> additionalInfo = new HashMap<>();
}
