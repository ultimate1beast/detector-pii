# PrivSense PII Detection System - Architecture Documentation

## Overview

The PrivSense PII Detection System is designed to scan various Database Management Systems (DBMS) and detect Personally Identifiable Information (PII) with high efficiency, robust logic, and clean code. The system implements a multi-stage pipeline approach that progressively applies more sophisticated (and computationally expensive) detection methods until a sufficient confidence level is reached.

## System Architecture

The system follows a layered architecture with clear separation of concerns:

```
┌───────────────────┐
│ REST Controllers  │
└───────┬───────────┘
        │
┌───────▼───────────┐
│ PIIDetector       │ ◄─┐
└───────┬───────────┘   │
        │               │
┌───────▼───────────┐   │
│ Pipeline Service  │   │ Caching
└───────┬───────────┘   │ Performance Metrics
        │               │ Error Handling
┌───────▼───────────┐   │
│ Detection         │   │
│ Strategies        │ ◄─┘
└───────────────────┘
```

### Key Components

1. **REST API Layer (`PIIDetectorController`)**: 
   - Exposes endpoints for detection operations
   - Handles request/response formatting 
   - Manages HTTP-specific concerns

2. **Core Service (`PIIDetectorImpl`)**: 
   - Implements the `PIIDetector` interface
   - Coordinates the scanning process across databases and tables
   - Manages sample sizes and optimization settings

3. **Pipeline Service (`PIIDetectionPipelineService`)**:
   - Implements the multi-stage detection approach
   - Coordinates different detection strategies
   - Manages early termination for efficiency

4. **Detection Strategies**:
   - `HeuristicNameStrategy`: Fast naming pattern analysis
   - `RegexPatternStrategy`: Pattern-based text analysis
   - `NERModelStrategy`: Machine-learning based entity recognition
   - `CompositePIIDetectionStrategy`: Combines results from multiple strategies

5. **Support Services**:
   - `PIIDetectionMetricsCollector`: Performance monitoring
   - `DetectionResultCleaner`: Post-processing and result normalization
   - `PIIDetectionCacheManager`: Caching for performance optimization
   - `DetectionResultFactory`: Creates standardized results

## Detection Pipeline Workflow

The system employs a progressive detection approach designed to balance accuracy with performance:

```
┌─────────────────┐    ┌────────────────┐    ┌───────────────┐
│ 1. Heuristic    │    │ 2. Regex       │    │ 3. NER Model  │
│ Name Analysis   │───►│ Pattern Match  │───►│ Detection     │
│ Fast, no data   │    │ Medium cost    │    │ High cost     │
└─────────────────┘    └────────────────┘    └───────────────┘
       │                      │                     │
       │                      │                     │
       ▼                      ▼                     ▼
┌─────────────────────────────────────────────────────────┐
│               Confidence Assessment                      │
│  Early termination when confidence threshold is reached  │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│               Context Enhancement                        │
│  Improve confidence using table/column context           │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│               Result Cleaning                           │
│  Remove redundant detections and normalize results      │
└─────────────────────────────────────────────────────────┘
```

### Pipeline Stages

1. **Preliminary Filtering**:
   - Skip technical columns (IDs, timestamps, flags)
   - Pre-filter samples to eliminate null values

2. **Stage 1: Heuristic Detection**:
   - Based on column naming patterns
   - Extremely fast, requires no data access
   - Examples: "email", "phone_number", "social_security"

3. **Stage 2: Pattern Matching**:
   - Apply regex patterns to column samples
   - Medium computational cost
   - Detects format-specific PII (emails, phone numbers, SSNs)

4. **Stage 3: NER Processing**:
   - Use machine learning Named Entity Recognition
   - Higher computational cost
   - More sophisticated detection for unstructured text

5. **Confidence Assessment**:
   - Each detection has a confidence score (0.0-1.0)
   - Pipeline stops when high confidence (≥0.9) detection is found
   - Configurable confidence threshold

6. **Context Enhancement**:
   - Improve confidence based on surrounding columns
   - Apply domain knowledge (e.g., address fields near each other)

7. **Result Cleaning**:
   - Eliminate redundant detections
   - Select most likely PII type for each column

## Optimization Strategies

The system employs several optimization strategies:

1. **Adaptive Sampling**: 
   - Sample size adjusted based on table size
   - Larger samples for small tables, smaller samples for large tables

2. **Multi-level Caching**:
   - Cache detection results at column, table levels
   - Cache sample data to avoid repeated database access

3. **Early Termination**:
   - Stop pipeline when high confidence detection is found
   - Skip remaining (more expensive) stages

4. **Parallel Processing**:
   - Batch column processing
   - Optimized parallel sampling

## Error Handling and Resilience

The system implements comprehensive error handling:

1. **Graceful Degradation**:
   - If a detection strategy fails, system falls back to others
   - NER service unavailability handled gracefully

2. **Detailed Error Reporting**:
   - Structured exception hierarchy
   - PIIDetectionException with error codes

3. **Extensive Logging**:
   - Performance metrics
   - Pipeline stage outcomes
   - Error details for troubleshooting

## Metrics Collection

The system collects detailed performance metrics:

1. **Pipeline Statistics**:
   - Count of columns processed/skipped
   - Pipeline stage termination points

2. **Detection Statistics**:
   - Count by detection method
   - Count by PII type

3. **Performance Timings**:
   - Total processing time
   - Time spent in each detection stage
   - Per-table processing times

## Configuration

Key configurable parameters:

1. **Confidence Threshold**: Minimum confidence to consider data as PII (0.0-1.0)
2. **Sample Size**: Number of records to sample per column
3. **Active Strategies**: Enable/disable specific detection strategies
4. **Caching**: Enable/disable and configure caching behavior

## Extension Points

The system is designed for extensibility:

1. **Adding Detection Strategies**:
   - Implement PIIDetectionStrategy interface
   - Register with PIIDetectionStrategyFactory

2. **Supporting New Database Types**:
   - Add connector in dbscanner module
   - Implement database-specific sampling logic if needed