# PrivSense - Privacy-Aware Database Scanning & PII Detection

## Description

PrivSense is a comprehensive solution for database introspection and personal identifiable information (PII) detection. The system connects to various relational databases, extracts their structure, samples data, and identifies potential PII using multi-stage detection strategies.

## Modules

The application consists of several interconnected modules:

1. **privsense-common**: Core utilities, models, and shared components
2. **privsense-dbscanner**: Database connection and metadata extraction
3. **privsense-piidetector**: PII detection and analysis engine
4. **privsense-web**: Integration layer and web interface

For detailed architecture information, see each module's documentation:
- [Common Module Architecture](privsense-common/docs/ARCHITECTURE.md)
- [DB Scanner Architecture](privsense-dbscanner/docs/ARCHITECTURE.md)
- [PII Detector Architecture](privsense-piidetector/docs/ARCHITECTURE.md)
- [Web Module Architecture](privsense-web/docs/ARCHITECTURE.md)

## Features

- **Flexible Database Connectivity**
  - Dynamic connection to MySQL, PostgreSQL, Oracle, and other databases
  - Runtime driver discovery and loading
  - Connection pooling and efficient resource management

- **Comprehensive Database Scanning**
  - Table and column metadata extraction
  - Foreign key relationship mapping
  - Optimized data sampling
  - Schema analysis

- **Advanced PII Detection**
  - Multi-stage detection pipeline
  - Heuristic, pattern-based, and machine learning detection strategies
  - Confidence scoring and threshold filtering
  - Context-aware detection enhancement

- **Rich API & Integration**
  - RESTful API with OpenAPI/Swagger documentation
  - Structured JSON responses
  - Modular architecture for easy extension
  - Comprehensive error handling

## Prerequisites

- Java 21 or later
- Maven 3.6+
- Compatible database systems for testing (MySQL, PostgreSQL, Oracle)

## Installation

1. **Clone the Repository**
   ```bash
   git clone https://fr-gitlab-forge-gto.wse.ent.cginet/bodori/privsense.git
   cd privsense
   ```

2. **Build the Project**
   ```bash
   ./mvnw clean install
   ```
   Or on Windows:
   ```cmd
   mvnw.cmd clean install
   ```

3. **Run the Application**
   ```bash
   java -jar privsense-web/target/privsense-web-0.0.1-SNAPSHOT.jar
   ```

## Configuration

The application is configured through YAML files:

- `application.yml`: Default configuration
- `application-prod.yml`: Production overrides

Key configuration sections:

```yaml
privsense:
  dbscanner:
    drivers:
      directory: ./drivers
      downloadMissing: true
    sampling:
      defaultLimit: 1000
      maxFetchSize: 500
  piidetector:
    confidence:
      threshold: 0.75
    sampling:
      adaptiveEnabled: true
      minSampleSize: 100
```

## Usage

### 1. Start the Application

Start the application using the instructions in the Installation section. The application will be available at `http://localhost:8080`.

### 2. Access the Swagger UI

Navigate to `http://localhost:8080/swagger-ui.html` to view the API documentation and interact with the endpoints.

### 3. Register a Database Connection

```bash
curl -X POST http://localhost:8080/api/scanner/connections \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-mysql-db",
    "dbType": "mysql",
    "host": "localhost",
    "port": 3306,
    "database": "sample_db",
    "username": "user",
    "password": "password"
  }'
```

### 4. Scan Database Structure

```bash
# List all tables
curl -X GET http://localhost:8080/api/scanner/connections/my-mysql-db/tables

# Get specific table details
curl -X GET http://localhost:8080/api/scanner/connections/my-mysql-db/tables/users

# Get table relationships
curl -X GET http://localhost:8080/api/scanner/connections/my-mysql-db/tables/users/relationships
```

### 5. Detect PII

```bash
# Analyze a table for PII
curl -X POST http://localhost:8080/api/pii/detect \
  -H "Content-Type: application/json" \
  -d '{
    "connectionId": "my-mysql-db",
    "tableName": "users",
    "sampleSize": 500
  }'

# Get detection report
curl -X GET http://localhost:8080/api/pii/reports/latest
```

## Development

### Module Structure

- **privsense-common**: Shared utilities and models
- **privsense-dbscanner**: Database connection and scanning
- **privsense-piidetector**: PII detection algorithms
- **privsense-web**: Web interface and integration

### Adding Support for a New Database

1. Create a new scanner implementation in `privsense-dbscanner`:
   ```java
   @Component
   @DatabaseType("new-db-type")
   public class NewDatabaseScanner extends AbstractDatabaseScanner {
       // Implement required methods
   }
   ```

2. Register the driver information in the configuration

### Adding a New PII Detection Strategy

1. Implement the `PIIDetectionStrategy` interface:
   ```java
   @Component
   public class NewDetectionStrategy implements PIIDetectionStrategy {
       // Implement detection logic
   }
   ```

2. Register with the strategy factory



## Acknowledgments

- Spring Boot framework
- Hikari Connection Pool
- Caffeine Cache
- OpenAPI/Swagger