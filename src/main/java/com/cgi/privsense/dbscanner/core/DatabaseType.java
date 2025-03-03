package com.cgi.privsense.dbscanner.core;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DatabaseType {
    String value();
}
