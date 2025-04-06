package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * NER service configuration properties.
 */
@Data
public class NerProperties {
    /**
     * NER service configuration.
     */
    private Service service = new Service();

    /**
     * Trust configuration.
     */
    private Trust trust = new Trust();

    /**
     * Gets the service URL.
     * Convenience method.
     *
     * @return The service URL
     */
    public String getServiceUrl() {
        return service.getUrl();
    }

    /**
     * Gets whether to trust all certificates.
     * Convenience method.
     *
     * @return Whether to trust all certificates
     */
    public boolean getTrustAllCerts() {
        return trust.getAll().isCerts();
    }

    @Data
    public static class Service {
        /**
         * NER service URL.
         */
        private String url = "http://localhost:5000/ner";
    }

    @Data
    public static class Trust {
        /**
         * Certificate trust configuration.
         */
        private All all = new All();

        @Data
        public static class All {
            /**
             * Whether to trust all certificates.
             */
            private boolean certs = false;
        }
    }
}