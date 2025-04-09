# PrivSense Common Module - Architecture Documentation

## Overview

The Common module serves as a foundational layer providing shared functionality, utilities, and model classes for all other modules in the PrivSense system. It ensures consistency across the application, reduces code duplication, and promotes standardization of core components.

## System Architecture

The Common module follows a utility-based architecture with clearly defined components:

```
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│  Model Classes    │     │    Utilities      │     │   Configuration   │
│  Base entities    │     │  Shared helpers   │     │   Properties      │
└───────────────────┘     └───────────────────┘     └───────────────────┘
                   \             |                  /
                    \            |                 /
                     ▼           ▼                ▼
              ┌─────────────────────────────────────────┐
              │       Common Constants and Enums        │
              └─────────────────────────────────────────┘
```

### Key Components

1. **Base Model Classes (`BaseMetaData`)**:
   - Foundation for all metadata entities
   - Implements common properties and behaviors
   - Provides extensibility mechanisms through additionalInfo
   - Ensures consistent serialization

2. **Utilities (`DatabaseUtils`, etc.)**:
   - Cross-cutting helper functions
   - Input validation and sanitization
   - Database-related utilities
   - String manipulation and formatting

3. **Configuration Framework**:
   - Centralized configuration properties
   - Environment-specific settings
   - ConfigurationFacade for simplified access
   - Type-safe configuration access

4. **Constants and Enums**:
   - Database type definitions
   - Application constants
   - Error codes
   - Standard identifiers

## Design Principles

### 1. Reusability
Components are designed to be reusable across different modules, reducing duplication and ensuring consistency.

### 2. Immutability
Core models are immutable where appropriate to ensure thread safety and prevent unexpected state changes.

### 3. Extensibility
The use of abstract base classes, interfaces, and additionalInfo maps allows for extension without modifying existing code.

### 4. Standardization
Common patterns, naming conventions, and access methods are standardized across the entire system.

## Usage Patterns

### Base Metadata Extension
```java
public class CustomMetadata extends BaseMetaData {
    // Custom fields and behavior
    private String specificAttribute;
    
    // Implementations of abstract methods
}
```

### Configuration Access
```java
@Autowired
private ConfigurationFacade configFacade;

// Access configuration
Map<String, Object> scannerConfig = configFacade.getDbScannerAsMap();
```

### Utility Functions
```java
// Database identifier sanitization
String safeIdentifier = DatabaseUtils.escapeIdentifier(userInput, dbType);

// Validation
DatabaseUtils.validateTableName(tableName);
```

## Integration Points

The Common module integrates with:

1. **Spring Framework**:
   - Configuration properties binding
   - Component scanning and autowiring

2. **Jackson/JSON Processing**:
   - Serialization/deserialization of models
   - API response formatting

3. **Logging Framework**:
   - Standardized logging approach
   - Common log formatting

4. **All Other PrivSense Modules**:
   - Dependency on common utilities
   - Extension of base models
   - Configuration consumption

## Error Handling

The module provides a consistent approach to error handling:

1. **Standard Exception Hierarchy**:
   - Defined base exception types
   - Common error coding system
   - Consistent exception formatting

2. **Validation Framework**:
   - Input validation utilities
   - Pre-condition checking
   - Bean validation annotations

## Configuration Properties

Key configurable parameters:

1. **Database Scanner Settings**:
   - Driver configuration
   - Connection pooling parameters
   - Sampling settings

2. **PII Detector Settings**:
   - Detection thresholds
   - Strategy configurations
   - Performance tuning

3. **Global Application Settings**:
   - Timeouts
   - Feature flags
   - Environment-specific configurations

## Extension Points

The system is designed for extensibility:

1. **Adding New Model Types**:
   - Extend BaseMetaData for new entity types
   - Implement common interfaces for new behaviors

2. **Extending Configuration**:
   - Add new properties to existing configuration classes
   - Create new configuration sections
   - Enhance ConfigurationFacade with new accessor methods