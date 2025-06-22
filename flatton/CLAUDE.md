# Flatton Project Instructions

## CouchDB Futon Implementation in Kotlin

Flatton is a comprehensive Kotlin implementation of CouchDB's Futon utility, providing database management, querying, and administration capabilities.

## Core Components

### **CouchClient Interface**
- Full CouchDB client with database CRUD operations
- Document management (create, read, update, delete, copy)
- Bulk operations with `Indexed<CouchDocument>` collections
- Design document and view query support
- Security and replication management

### **Futon Template Engine**
- Mimics CouchDB Futon UI functionality 
- Renders database lists, document views, and administration interfaces
- Uses TrikeShed `Indexed<T>` for efficient data iteration
- Supports template loops and property replacement

### **CouchDB Data Structures**
- Native Kotlin types for all CouchDB entities
- Type-safe database names, document IDs, revision IDs
- View responses with proper key-value typing
- Replication configuration and status tracking

## CouchDB Integration Patterns

### **Data Structure Usage**
- Use `Indexed<T>` for document collections and view results
- Use `Join<A,B>` for database-to-metadata mappings
- Prefer TrikeShed types over standard Kotlin collections
- Follow TrikeShed functional programming patterns

### **Performance Considerations** 
- for loops are optimal for bulk document processing
- Avoid `List<T>` and `Pair<A,B>` in CouchDB operations
- Use primitive arrays for high-volume replication data
- Leverage TrikeShed's cache-friendly data structures

## JSON Processing

**JSON Scanner Integration**: The `SimdJsonScanner.kt` provides efficient JSON parsing for CouchDB responses without full deserialization. Consider upgrading to `kotlinx-serialization-scanner`'s `BitmapJsonDecoder` for enhanced performance.

## Development Guidelines

- Follow TrikeShed architectural patterns for all data processing
- Use museum preservation rules for CouchDB client algorithms  
- Prefer functional data structures for immutable document handling
- Defer to superbikeshed gradle for version management
- Support WASM compilation for browser-based administration
- Maintain compatibility with CouchDB 3.x API standards

## Database Administration Features

### **Database Management**
- Create, delete, and inspect databases
- Database info retrieval with document counts and sizing
- Security configuration and user management

### **Document Operations**
- Full document lifecycle management
- Bulk document operations for efficiency
- Revision tracking and conflict resolution
- Attachment handling capabilities

### **View System**
- Map-reduce view definitions and execution
- View query parameter handling
- Result pagination and filtering
- Design document management

### **Replication Support**
- Replication configuration setup
- Continuous and one-time replication modes
- Conflict resolution strategies
- Replication status monitoring

## Web Interface (WASM)

Flatton compiles to WASM for browser-based CouchDB administration, providing a modern alternative to the original Futon interface with enhanced performance and TrikeShed architectural benefits.