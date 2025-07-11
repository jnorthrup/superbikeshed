# JDBC2JSON Attention Integration

## Overview

The JDBC2JSON Attention Bridge integrates [jdbc2json](https://github.com/jnorthrup/jdbc2json) functionality with the fiduciary attention system, enabling intelligent database-to-blackboard synchronization with attention-based filtering and processing.

## Key Features

### 1. Attention-Based Database Processing
- **Fiduciary Relevance Classification**: Automatically categorizes database tables by fiduciary importance
- **Attention Scoring**: Assigns attention scores to tables and rows based on fiduciary relevance
- **Selective Processing**: Only processes high-attention data to optimize performance

### 2. Multiple Sync Modes
- **Full Sync**: Process all tables in the database
- **Incremental Sync**: Only process changed rows since last sync
- **Attention-Only Sync**: Only process high-attention tables and rows

### 3. CouchDB Integration
- **Bulk Operations**: Efficient batch processing with configurable batch sizes
- **Document Structure**: Structured CouchDB documents with attention metadata
- **Prefix Management**: Organized document IDs with table prefixes

## Architecture

```
JDBC Database → JDBC2JSONAttentionBridge → CouchDB Blackboard
                    ↓
            FiduciaryMemvidBridge → Attention Memory
```

### Components

#### JDBCConnectionConfig
Configuration for JDBC database connections with attention processing parameters:
```kotlin
data class JDBCConnectionConfig(
    val url: String,
    val username: String,
    val password: String,
    val schemaPattern: String? = null,
    val catalog: String? = null,
    val tableNamePattern: String? = null,
    val types: List<String> = listOf("TABLE"),
    val fetchSize: Int = 1000,
    val bulkSize: Int = 500
)
```

#### FiduciaryRelevance Classification
Tables are automatically classified by fiduciary importance:
- **HIGH**: Trust accounts, beneficiary data, legal documents
- **MEDIUM**: Financial transactions, compliance records
- **LOW**: System tables, audit logs
- **NONE**: Unrelated data

#### Attention Events
The bridge generates attention events for tracking:
- `TableDiscovery`: When new tables are discovered
- `RowProcessing`: When individual rows are processed
- `BulkInsert`: When batches are inserted to CouchDB
- `SyncOperation`: When sync operations occur

## Usage Examples

### Basic Database Processing
```kotlin
val bridge = createJDBC2JSONAttentionBridge(
    couchDbService = couchDbService,
    memvidBridge = memvidBridge,
    attentionThreshold = 0.5
)

val config = JDBCConnectionConfig(
    url = "jdbc:postgresql://localhost:5432/fiduciary_db",
    username = "fiduciary_user",
    password = "secure_password",
    bulkSize = 500
)

// Process database with attention filtering
bridge.processDatabaseWithAttention(config, "fiduciary_").collect { event ->
    when (event) {
        is JDBCAttentionEvent.TableDiscovery -> {
            println("Discovered table: ${event.tableName} (attention: ${event.attentionScore})")
        }
        is JDBCAttentionEvent.BulkInsert -> {
            println("Inserted ${event.totalRows} rows from ${event.tableName}")
        }
    }
}
```

### Incremental Sync
```kotlin
// Sync only changes since last sync
bridge.syncChangesWithAttention(
    config = config,
    couchDbPrefix = "fiduciary_",
    syncMode = SyncMode.INCREMENTAL
).collect { event ->
    when (event) {
        is JDBCAttentionEvent.SyncOperation -> {
            println("${event.operation} ${event.affectedRows} rows in ${event.tableName}")
        }
    }
}
```

### Attention-Only Processing
```kotlin
// Only process high-attention data
bridge.syncChangesWithAttention(
    config = config,
    couchDbPrefix = "fiduciary_",
    syncMode = SyncMode.ATTENTION_ONLY
).collect { event ->
    // Only high-attention events will be emitted
}
```

## CouchDB Document Structure

Documents stored in CouchDB follow this structure:
```json
{
  "_id": "fiduciary_trust_accounts_row_123",
  "tableName": "trust_accounts",
  "rowId": "row_123",
  "data": {
    "id": 123,
    "name": "Trust Account Alpha",
    "value": 12300.0,
    "status": "active",
    "created_at": 1640995200000
  },
  "attentionScore": 0.95,
  "processingTimestamp": 1640995200000,
  "type": "jdbc_row"
}
```

## Attention Integration

### Memvid Bridge Integration
The JDBC2JSON bridge integrates with the existing memvid attention system:
- JDBC events are converted to standard attention events
- Attention memory tracks database processing patterns
- Visual attention flows show database synchronization activity

### Attention Scoring Algorithm
Tables and rows are scored based on:
1. **Table Name Patterns**: Keywords like "trust", "beneficiary", "fiduciary"
2. **Schema Classification**: Fiduciary vs. system schemas
3. **Data Content**: Field values and relationships
4. **Temporal Factors**: Recent modifications and access patterns

## Performance Optimization

### Batch Processing
- Configurable batch sizes for optimal throughput
- Memory-efficient streaming of large datasets
- Parallel processing of independent tables

### Attention Filtering
- Skip low-attention tables entirely
- Process high-attention tables first
- Adaptive attention thresholds based on system load

### CouchDB Optimization
- Bulk document operations
- Efficient document ID generation
- Indexed queries for attention-based retrieval

## Security Considerations

### Data Protection
- Encrypted JDBC connections
- Secure credential management
- Audit trails for all database operations

### Access Control
- Role-based table access
- Row-level security integration
- Compliance with fiduciary duty requirements

### Privacy
- PII detection and handling
- Data anonymization for low-attention records
- Retention policy enforcement

## Monitoring and Metrics

### Attention Metrics
- Table discovery rates
- Row processing throughput
- Attention score distributions
- Sync operation success rates

### Performance Metrics
- Processing time per table
- Memory usage during bulk operations
- CouchDB write performance
- Network bandwidth utilization

### Compliance Metrics
- Data synchronization completeness
- Audit trail integrity
- Regulatory compliance status

## Future Enhancements

### Planned Features
1. **Real-time Change Detection**: Database triggers for immediate sync
2. **Advanced Attention Models**: Machine learning for attention scoring
3. **Multi-database Support**: Cross-database relationship mapping
4. **Incremental Schema Evolution**: Handle schema changes gracefully

### Integration Roadmap
1. **XACML Policy Integration**: Policy-based access control
2. **Blockchain Integration**: Immutable audit trails
3. **AI-Powered Insights**: Automated fiduciary compliance analysis
4. **Real-time Dashboards**: Live attention visualization

## Conclusion

The JDBC2JSON Attention Bridge provides a powerful foundation for integrating traditional database systems with the fiduciary attention architecture. By combining the efficiency of jdbc2json with the intelligence of attention-based processing, it enables seamless synchronization while maintaining the high standards required for fiduciary operations.

The bridge ensures that only relevant data is processed and stored, optimizing both performance and compliance while providing comprehensive audit trails for all database operations. 