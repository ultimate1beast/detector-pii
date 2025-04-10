# PrivSense Web Module - Architecture Documentation

## Overview

The Web module serves as the main integration point and entry point for the PrivSense application. It coordinates the interactions between different modules, provides unified API access, and delivers the web-based user interface. This module acts as the glue that ties together the database scanning capabilities and PII detection functionalities into a cohesive application.

## System Architecture

The Web module follows a standard Spring Boot MVC architecture with clear separation of concerns:

```
┌───────────────────────────────────────────────────────────┐
│                    Web UI / Frontend                      │
└─────────────────────────────┬─────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                  REST API Controllers                     │
└─────────────────────────────┬─────────────────────────────┘
                              │
┌───────────────┬─────────────┴─────────────────┬───────────┐
│  DB Scanner   │      Integration Layer        │    PII    │
│    Module     │                               │  Detector │
└───────────────┘                               └───────────┘
       │         \                             /      │
       │          \                           /       │
       ▼           ▼                         ▼        ▼
┌──────────┐  ┌──────────┐             ┌──────────┐  ┌──────────┐
│  MySQL   │  │PostgreSQL│    ...      │ Pattern  │  │   NER    │
│ Scanner  │  │ Scanner  │             │ Detector │  │ Service  │
└──────────┘  └──────────┘             └──────────┘  └──────────┘
```

### Key Components

1. **Application Configuration (`WebConfig`, `SpringDocConfig`)**:
   - Application-wide settings
   - Module integration
   - Cross-cutting concerns
   - API documentation configuration

2. **Integration Services**:
   - Facade services to coordinate module interactions
   - Workflow orchestration
   - Transaction management
   - Cross-module event handling

3. **API Documentation**:
   - OpenAPI/Swagger integration
   - API grouping by functional area
   - Machine-readable API descriptions
   - Interactive API documentation UI

4. **Security Configuration**:
   - Authentication and authorization
   - API access control
   - Security filters
   - Credential management

5. **Global Exception Handling**:
   - Centralized error handling
   - Consistent API error responses
   - Detailed logging
   - Client-appropriate error messages

## Integration Architecture

The web module integrates multiple services:

```
┌─────────────────────────────────────────────────────────────┐
│                      Web Module                             │
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │ DB Scanner  │◄──►│ Integration │◄──►│  PII Detector   │  │
│  │   Module    │    │  Services   │    │    Module       │  │
│  └─────────────┘    └─────────────┘    └─────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Integration Patterns

1. **Spring Dependency Injection**:
   - Autowiring of module services
   - Configuration property binding
   - Bean lifecycle management
   - Component scanning

2. **REST API Integration**:
   - Internal service calls
   - Response transformation
   - API composition
   - Error propagation

3. **Shared Model Objects**:
   - Common DTOs
   - Response wrappers
   - Cross-module entities
   - Transfer objects

4. **Unified Configuration**:
   - Centralized properties
   - Environment-specific settings
   - Module-specific sections
   - Feature flags

## Application Workflow

The typical application workflow orchestrated by the web module:

1. **Database Connection**:
   - Register database connections
   - Validate connectivity
   - Store connection details
   - Manage connection lifecycle

2. **Database Scanning**:
   - Discover database structure
   - Extract table and column metadata
   - Identify relationships
   - Sample data for analysis

3. **PII Detection**:
   - Analyze samples for PII
   - Apply detection strategies
   - Generate confidence scores
   - Produce detailed PII reports

4. **Results Presentation**:
   - Format detection results
   - Generate visualization data
   - Provide downloadable reports
   - Support filtering and analysis

## API Organization

The API is organized into functional groups:

1. **DB Scanner APIs** (`/api/scanner/...`):
   - Connection management
   - Database structure discovery
   - Data sampling
   - Relationship analysis

2. **PII Detection APIs** (`/api/pii/...`):
   - Detection requests
   - Analysis results
   - Report generation
   - Configuration settings

3. **Management APIs** (`/actuator/...`):
   - Application health
   - Metrics and monitoring
   - Runtime information
   - Environment details

## Deployment Architecture

The application supports various deployment models:

1. **Standalone Application**:
   - Single JAR deployment
   - Embedded Tomcat server
   - Self-contained execution
   - Simple deployment and operations

2. **Service-Based Deployment**:
   - Component-based deployment
   - External service registration
   - Distributed configuration
   - Scalable architecture

## Configuration Management

The web module provides sophisticated configuration management:

1. **Environment-Specific Properties**:
   - `application.yml` for default settings
   - `application-{profile}.yml` for environment overrides
   - Property placeholder resolution
   - Externalized configuration

2. **Runtime Configuration**:
   - Dynamic property updates
   - Configuration endpoints
   - Feature toggles
   - Refresh scope support

3. **Logging Configuration**:
   - Logback integration
   - Log file rotation
   - Log level management
   - Contextual logging

## Security Considerations

The Web module implements security best practices:

1. **API Security**:
   - Endpoint protection
   - Input validation
   - Output encoding
   - CSRF protection

2. **Data Protection**:
   - Sensitive data handling
   - PII masking in logs
   - Secure credential storage
   - Secure defaults

3. **Audit and Compliance**:
   - Operation logging
   - Action attribution
   - Compliance reporting
   - Audit trails

## Monitoring and Operations

The web module provides comprehensive operational features:

1. **Health Checks**:
   - Database connectivity checks
   - Service dependency health
   - Application state validation
   - Custom health indicators

2. **Metrics Collection**:
   - Request timing
   - Error rates
   - System resource utilization
   - Business metrics

3. **Troubleshooting Tools**:
   - Enhanced logging
   - Thread dumps
   - Memory analysis
   - HTTP trace

## Extension Points

The web module offers various extension mechanisms:

1. **Adding New APIs**:
   - Create new controllers
   - Register with OpenAPI groups
   - Add appropriate documentation
   - Implement cross-cutting concerns

2. **Custom Integration Services**:
   - Create new integration components
   - Extend existing services
   - Add workflow orchestration
   - Implement event handling

3. **UI Customization**:
   - Add new HTML templates
   - Extend JavaScript functionality
   - Customize CSS styling
   - Extend visualization components