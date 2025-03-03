package com.cgi.privsense.dbscanner.config.dtoconfig;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DatabaseConnectionRequest {
    /*
     Informations de base
     */
    private String name;  // Nom unique de la connexion
    private String dbType;  // Type de base de données (mysql, postgresql, etc.)

    /*
     Informations de connexion
     */
    private String host;  // Hôte (localhost, IP, etc.)
    private Integer port;  // Port
    private String database;  // Nom de la base de données
    private String username;  // Utilisateur
    private String password;  // Mot de passe

    /*
     Paramètres avancés
     */
    private String driverClassName;  // Classe du driver JDBC (optionnel)
    private String url;  // URL JDBC complète (optionnel, sera construit si non fourni)
    private Map<String, String> properties;  // Propriétés additionnelles de connexion

    /*
     Paramètres de pool de connexions
     */
    private Integer maxPoolSize;  // Taille maximale du pool
    private Integer minIdle;  // Nombre minimum de connexions inactives
    private Integer connectionTimeout;  // Timeout de connexion en ms
    private Boolean autoCommit;  // Mode auto-commit

    /*
     Paramètres SSL
     */
    private Boolean useSSL;  // Utiliser SSL
    private String sslMode;  // Mode SSL (disable, require, verify-ca, verify-full)
    private String trustStorePath;  // Chemin vers le truststore
    private String trustStorePassword;  // Mot de passe du truststore
}
