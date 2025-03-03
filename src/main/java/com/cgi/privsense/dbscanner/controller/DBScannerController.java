package com.cgi.privsense.dbscanner.controller;

import com.cgi.privsense.dbscanner.config.DynamicDataSourceConfig;
import com.cgi.privsense.dbscanner.config.dtoconfig.DatabaseConnectionRequest;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import com.cgi.privsense.dbscanner.service.DatabaseScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scanner")
@Tag(name = "Database Scanner", description = "API pour scanner les bases de données")
public class DBScannerController {

    private final DatabaseScannerService scannerService;
    private final DynamicDataSourceConfig dataSourceConfig;

    public DBScannerController(DatabaseScannerService scannerService, DynamicDataSourceConfig dataSourceConfig) {
        this.scannerService = scannerService;
        this.dataSourceConfig = dataSourceConfig;
    }

    @Operation(summary = "Enregistrer une nouvelle connexion")
    @PostMapping("/connections")
    public ResponseEntity<String> registerConnection(@RequestBody DatabaseConnectionRequest request) {
        DataSource dataSource = dataSourceConfig.createDataSource(request);
        dataSourceConfig.registerDataSource(request.getName(), dataSource);
        return ResponseEntity.ok("Connection registered: " + request.getName());
    }

    @Operation(summary = "Scanner toutes les tables")
    @GetMapping("/connections/{connectionName}/tables")
    public ResponseEntity<List<TableMetadata>> scanTables(
            @PathVariable String connectionName,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.scanTables(dbType, connectionName));
    }

    @Operation(summary = "Scanner une table spécifique")
    @GetMapping("/connections/{connectionName}/tables/{tableName}")
    public ResponseEntity<TableMetadata> scanTable(
            @PathVariable String connectionName,
            @PathVariable String tableName,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.scanTable(dbType, connectionName, tableName));
    }

    @Operation(summary = "Scanner les colonnes d'une table")
    @GetMapping("/connections/{connectionName}/tables/{tableName}/columns")
    public ResponseEntity<List<ColumnMetadata>> scanColumns(
            @PathVariable String connectionName,
            @PathVariable String tableName,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.scanColumns(dbType, connectionName, tableName));
    }

    @Operation(summary = "Échantillonner les données d'une table")
    @GetMapping("/connections/{connectionName}/tables/{tableName}/sample")
    public ResponseEntity<DataSample> sampleData(
            @PathVariable String connectionName,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.sampleData(dbType, connectionName, tableName, limit));
    }

    @Operation(summary = "Échantillonner les données d'une colonne")
    @GetMapping("/connections/{connectionName}/tables/{tableName}/columns/{columnName}/sample")
    public ResponseEntity<List<Object>> sampleColumnData(
            @PathVariable String connectionName,
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.sampleColumnData(dbType, connectionName, tableName, columnName, limit));
    }

    @Operation(summary = "Obtenir les relations d'une table")
    @GetMapping("/connections/{connectionName}/tables/{tableName}/relations")
    public ResponseEntity<Map<String, String>> getTableRelations(
            @PathVariable String connectionName,
            @PathVariable String tableName,
            @RequestParam String dbType) {
        return ResponseEntity.ok(scannerService.getTableRelations(dbType, connectionName, tableName));
    }
}
