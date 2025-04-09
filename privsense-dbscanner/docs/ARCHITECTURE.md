# PrivSense DB Scanner Module - Architecture Documentation

## Overview

The DB Scanner module is a specialized component designed to extract, analyze, and provide metadata from various relational database management systems. It offers an extensible framework for connecting to different database types, scanning their structure, and sampling data for further analysis by other modules such as the PII detector.

## System Architecture

The DB Scanner module follows a layered architecture with distinct separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                    REST API Controllers                     │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                    Scanner Services                         │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                    Database Scanners                        │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────────┐    │
│  │    MySQL    │   │ PostgreSQL  │   │ Other DBMS      │    │
│  └─────────────┘   └─────────────┘   └─────────────────┘    │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                Dynamic Data Source Management               │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

1. **REST API Layer (`DatabaseScannerController`)**:
   - Exposes endpoints for connectivity, scanning, and sampling operations
   - Handles request validation and response formatting
   - Maps API operations to service methods

2. **Service Layer**:
   - `DatabaseScannerService`: Coordinates scanning operations
   - `OptimizedParallelSamplingService`: Manages efficient data sampling
   - Implements caching for performance optimization
   - Handles error management and recovery

3. **Scanner Implementations**:
   - `AbstractDatabaseScanner`: Provides template methods and common behavior
   - Database-specific implementations (MySQL, PostgreSQL, Oracle, etc.)
   - Annotation-based registration via `@DatabaseType`

4. **Dynamic Data Source Management**:
   - Runtime connection management with pooling
   - Supports multiple concurrent connections to different databases
   - `RoutingDataSource` for dynamic connection switching
   - Connection lifecycle management

5. **Driver Management**:
   - `MavenDriverManager`: Downloads database drivers at runtime
   - Supports custom driver registration
   - Driver isolation and classloading

6. **Data Models**:
   - `TableMetadata`: Table structure information
   - `ColumnMetadata`: Column details and properties
   - `RelationshipMetadata`: Foreign key relationships
   - `DataSample`: Structured sample data

## Database Scanning Workflow

The system provides a comprehensive database scanning process:

```
┌───────────────┐   ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ 1. Connect    │   │ 2. Scan       │   │ 3. Extract    │   │ 4. Sample     │
│ to Database   │──►│ Structure     │──►│ Relationships │──►│ Data          │
└───────────────┘   └───────────────┘   └───────────────┘   └───────────────┘
```

### Workflow Stages

1. **Database Connection**:
   - Connection pooling with HikariCP
   - Dynamic configuration based on database type
   - Access control and credential management
   - Connection health monitoring

2. **Structure Scanning**:
   - Table metadata extraction
   - Column property discovery
   - Type mapping to normalized formats
   - Comment and description extraction

3. **Relationship Analysis**:
   - Foreign key discovery
   - Incoming and outgoing relationship mapping
   - Constraint rule extraction (CASCADE, RESTRICT, etc.)
   - Cross-schema relationship support

4. **Data Sampling**:
   - Configurable sample sizes
   - Parallel sampling for performance
   - Type-aware value extraction
   - Null handling and sanitization

## Optimization Strategies

The module employs several optimization techniques:

1. **Connection Pooling**:
   - Reuse connections to minimize overhead
   - Configurable pool sizes based on workload
   - Idle connection management

2. **Caching**:
   - Multi-level caching of metadata
   - Spring Boot Cache abstraction
   - Caffeine as high-performance caching provider
   - Cache invalidation on schema changes

3. **Query Optimization**:
   - Database-specific optimized queries
   - Fetch size tuning for large result sets
   - Query timeout management
   - Prepared statement reuse

4. **Parallel Processing**:
   - Multi-threaded sampling
   - Work queue for large sampling operations
   - Thread pool management
   - Backpressure handling

## Error Handling and Resilience

The system implements robust error handling:

1. **Connection Failures**:
   - Retry mechanisms with exponential backoff
   - Circuit breaker for persistent failures
   - Detailed connection diagnostics

2. **Query Errors**:
   - Standardized error translation
   - Database-specific error code handling
   - Context-enriched exceptions

3. **Resource Management**:
   - Connection leak prevention
   - Statement and ResultSet cleanup
   - Memory footprint management
   - Timeout enforcement

## Extension Points

The module is designed for extensibility:

1. **Adding New Database Support**:
   - Implement `DatabaseScanner` interface
   - Annotate with `@DatabaseType`
   - Provide specialized SQL queries
   - Register driver information

2. **Custom Sampling Strategies**:
   - Extend sampling services
   - Implement specialized extractors for complex types
   - Define new sampling algorithms

3. **Enhanced Metadata**:
   - Add custom properties to metadata classes
   - Use additionalInfo for database-specific attributes
   - Extend query capabilities

## Integration Points

The DB Scanner module integrates with:

1. **Database Systems**:
   - JDBC drivers
   - Connection pool implementation
   - SQL dialect handling

2. **PII Detector**:
   - Provides data samples for analysis
   - Shares metadata for context-aware detection
   - Returns relationship information for graph analysis

3. **Web Module**:
   - Exposes REST APIs
   - Integrates with global security
   - Provides Swagger/OpenAPI documentation

## Configuration

Key configurable parameters:

1. **Connection Settings**:
   - Pool sizes and timeouts
   - Connection properties
   - SSL configuration

2. **Scanner Behavior**:
   - Fetch size for result sets
   - Query timeouts
   - Maximum rows to scan

3. **Sampling Configuration**:
   - Sample sizes per table
   - Parallelism settings
   - Memory utilization limits