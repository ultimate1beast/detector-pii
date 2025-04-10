package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * Thread configuration properties.
 */
@Data
public class ThreadProperties {
    private int corePoolSize = 4;
    private int maxPoolSize = 12;
    private int keepAliveTime = 60;
    private boolean virtualThreads = true;
}