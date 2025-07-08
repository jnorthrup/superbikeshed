# CouchDB Test Plan

## Overview

This document outlines the comprehensive test plan for CouchDB functionality in the Nexus framework. The tests follow Test-Driven Development (TDD) principles and cover all major CouchDB operations through the IPFS bridge.

## Test Categories

### 1. Database Operations

#### 1.1 Database Creation
- **Test**: `should create database successfully`
- **Description**: Verify database creation with valid name
- **Expected**: Database exists, doc_count = 0, doc_del_count = 0
- **TDD**: Write test first, then implement `createDatabase()`

#### 1.2 Database Creation Validation
- **Test**: `should fail to create database with invalid name`
- **Description**: Test validation of database names
- **Cases**: Empty string, invalid characters (/,\,*), single character
- **Expected**: `IllegalArgumentException` thrown

#### 1.3 Database Deletion
- **Test**: `should delete database successfully`
- **Description**: Create database, delete it, verify it's gone
- **Expected**: Database no longer exists, all documents marked as deleted

#### 1.4 Database Deletion Validation
- **Test**: `should fail to delete non-existent database`
- **Description**: Attempt to delete non-existent database
- **Expected**: `DatabaseNotFoundException` thrown

#### 1.5 Database Information
- **Test**: `should return correct database information`
- **Description**: Verify database info includes correct counts and metadata
- **Expected**: Accurate doc_count, doc_del_count, update_seq

### 2. Document CRUD Operations

#### 2.1 Document Creation
- **Test**: `should create document with auto-generated ID`
- **Description**: Create document without specifying ID
- **Expected**: ID generated, revision created, document stored

#### 2.2 Document Creation with ID
- **Test**: `should create document with specified ID`
- **Description**: Create document with specific ID
- **Expected**: Document stored with specified ID, revision created

#### 2.3 Document Retrieval
- **Test**: `should retrieve document successfully`
- **Description**: Create document, retrieve it, verify content
- **Expected**: Document content matches original, revision correct

#### 2.4 Document Retrieval Error
- **Test**: `should fail to retrieve non-existent document`
- **Description**: Attempt to retrieve non-existent document
- **Expected**: `DocumentNotFoundException` thrown

#### 2.5 Document Update
- **Test**: `should update document with correct revision`
- **Description**: Update existing document with correct revision
- **Expected**: New revision created, content updated

#### 2.6 Document Update Error
- **Test**: `should fail to update document with wrong revision`
- **Description**: Update document with incorrect revision
- **Expected**: `ConflictException` thrown

#### 2.7 Document Deletion
- **Test**: `should delete document successfully`
- **Description**: Delete document with correct revision
- **Expected**: Document marked as deleted, new revision created

#### 2.8 Document Deletion Error
- **Test**: `should fail to delete document with wrong revision`
- **Description**: Delete document with incorrect revision
- **Expected**: `DocumentNotFoundException` thrown

### 3. Bulk Operations

#### 3.1 Bulk Document Creation
- **Test**: `should handle bulk document operations`
- **Description**: Create multiple documents in single operation
- **Expected**: All documents created successfully

#### 3.2 Bulk Mixed Operations
- **Test**: `should handle bulk operations with mixed create and update`
- **Description**: Mix of new documents and updates in bulk operation
- **Expected**: All operations succeed with appropriate revisions

#### 3.3 Bulk Operation Errors
- **Test**: `should handle bulk operation errors gracefully`
- **Description**: Some documents in bulk operation fail
- **Expected**: Successful operations complete, failed operations reported

### 4. Changes Feed

#### 4.1 Changes Feed Basic
- **Test**: `should track changes feed correctly`
- **Description**: Verify changes feed tracks document operations
- **Expected**: Changes include all document operations

#### 4.2 Changes Feed with Since Parameter
- **Test**: `should handle changes feed with since parameter`
- **Description**: Get changes since specific sequence
- **Expected**: Only changes after specified sequence returned

#### 4.3 Changes Feed Deletions
- **Test**: `should include deletions in changes feed`
- **Description**: Verify deleted documents appear in changes
- **Expected**: Deleted documents marked with `deleted: true`

### 5. Query Operations

#### 5.1 All Documents Query
- **Test**: `should handle all documents query`
- **Description**: Query all documents in database
- **Expected**: All documents returned with metadata

#### 5.2 All Documents with Include Docs
- **Test**: `should handle all documents with include_docs parameter`
- **Description**: Query with full document content
- **Expected**: Documents include full content

### 6. Conflict Handling

#### 6.1 Document Conflicts
- **Test**: `should handle document conflicts correctly`
- **Description**: Simulate concurrent updates to same document
- **Expected**: Conflict resolution or error as appropriate

#### 6.2 Conflict Resolution
- **Test**: `should provide conflict resolution mechanisms`
- **Description**: Test conflict resolution strategies
- **Expected**: Conflicts can be resolved programmatically

### 7. Error Handling

#### 7.1 Invalid Database Names
- **Test**: `should handle invalid database names`
- **Description**: Test various invalid database name patterns
- **Expected**: Appropriate validation errors

#### 7.2 Invalid Document IDs
- **Test**: `should handle invalid document IDs`
- **Description**: Test various invalid document ID patterns
- **Expected**: Appropriate validation errors

#### 7.3 Network Errors
- **Test**: `should handle network errors gracefully`
- **Description**: Simulate network failures during operations
- **Expected**: Appropriate error responses and retry mechanisms

### 8. Performance Tests

#### 8.1 Large Bulk Operations
- **Test**: `should handle large bulk operations efficiently`
- **Description**: Test with 100+ documents in bulk operation
- **Expected**: Operation completes within reasonable time

#### 8.2 Large Document Handling
- **Test**: `should handle large documents efficiently`
- **Description**: Test with documents containing large data
- **Expected**: Large documents processed correctly

### 9. Network Synchronization

#### 9.1 Multi-Node Synchronization
- **Test**: `should handle network synchronization via PubSub`
- **Description**: Test document synchronization across multiple nodes
- **Expected**: Documents propagate to all nodes

#### 9.2 Conflict Resolution Across Nodes
- **Test**: `should resolve conflicts across network nodes`
- **Description**: Test conflict resolution in distributed environment
- **Expected**: Conflicts resolved consistently across network

## Implementation Status

### ✅ Completed Tests
- Basic database operations (create, delete, info)
- Basic document CRUD operations
- Bulk document operations
- Changes feed functionality
- Error handling for common cases

### 🔄 In Progress
- Network synchronization tests
- Performance benchmarks
- Advanced conflict resolution

### 📋 Planned Tests
- Complex query operations
- Advanced error scenarios
- Stress testing
- Security testing

## Test Execution Status

### ✅ Passing Tests (2024-12-19)

#### 1. **Database Operations** - ✅ **All Passing**
- **Test**: `should create database successfully` - ✅ **PASS**
- **Test**: `should fail to create database with invalid name` - ✅ **PASS**
- **Test**: `should delete database successfully` - ✅ **PASS**
- **Test**: `should fail to delete non-existent database` - ✅ **PASS**
- **Test**: `should return correct database information` - ✅ **PASS**

#### 2. **Document CRUD Operations** - ✅ **All Passing**
- **Test**: `should create document with auto-generated ID` - ✅ **PASS**
- **Test**: `should create document with specified ID` - ✅ **PASS**
- **Test**: `should retrieve document successfully` - ✅ **PASS**
- **Test**: `should fail to retrieve non-existent document` - ✅ **PASS**
- **Test**: `should update document with correct revision` - ✅ **PASS**
- **Test**: `should fail to update document with wrong revision` - ✅ **PASS**
- **Test**: `should delete document successfully` - ✅ **PASS**
- **Test**: `should fail to delete document with wrong revision` - ✅ **PASS**

#### 3. **Bulk Operations** - ✅ **All Passing**
- **Test**: `should handle bulk document operations` - ✅ **PASS**
- **Test**: `should handle bulk operations with mixed create and update` - ✅ **PASS**
- **Test**: `should handle bulk operation errors gracefully` - ✅ **PASS**

#### 4. **Changes Feed** - ✅ **All Passing**
- **Test**: `should track changes feed correctly` - ✅ **PASS**
- **Test**: `should handle changes feed with since parameter` - ✅ **PASS**
- **Test**: `should include deletions in changes feed` - ✅ **PASS**

### 🔄 Failing Tests (2024-12-19)

#### 1. **Query Operations** - 🔄 **Partially Failing**
- **Test**: `should handle all documents query` - 🔄 **FAIL** (Not implemented)
- **Test**: `should handle all documents with include_docs parameter` - 🔄 **FAIL** (Not implemented)

#### 2. **Conflict Handling** - 🔄 **Partially Failing**
- **Test**: `should handle document conflicts correctly` - 🔄 **FAIL** (Basic implementation, needs improvement)
- **Test**: `should provide conflict resolution mechanisms` - 🔄 **FAIL** (Not implemented)

#### 3. **Performance Tests** - 🔄 **Not Implemented**
- **Test**: `should handle large bulk operations efficiently` - 🔄 **NOT IMPLEMENTED**
- **Test**: `should handle large documents efficiently` - 🔄 **NOT IMPLEMENTED**

#### 4. **Network Tests** - 🔄 **Not Implemented**
- **Test**: `should handle network synchronization via PubSub` - 🔄 **NOT IMPLEMENTED**
- **Test**: `should resolve conflicts across network nodes` - 🔄 **NOT IMPLEMENTED**

### 📊 Test Statistics

| Category | Total Tests | Passing | Failing | Not Implemented |
|----------|-------------|---------|---------|-----------------|
| Database Operations | 5 | 5 | 0 | 0 |
| Document CRUD | 8 | 8 | 0 | 0 |
| Bulk Operations | 3 | 3 | 0 | 0 |
| Changes Feed | 3 | 3 | 0 | 0 |
| Query Operations | 2 | 0 | 2 | 0 |
| Conflict Handling | 2 | 0 | 2 | 0 |
| Performance Tests | 2 | 0 | 0 | 2 |
| Network Tests | 2 | 0 | 0 | 2 |
| **Total** | **27** | **19** | **4** | **4** |

**Success Rate**: 70% (19/27 tests passing)

### 🔧 Implementation Gaps

#### 1. **Query Operations** - High Priority
- **Missing**: All documents query implementation
- **Missing**: Include docs parameter handling
- **Effort**: Medium
- **Impact**: Core CouchDB functionality

#### 2. **Conflict Resolution** - Medium Priority
- **Missing**: Advanced conflict detection
- **Missing**: Conflict resolution strategies
- **Effort**: High
- **Impact**: Multi-node synchronization

#### 3. **Performance Tests** - Low Priority
- **Missing**: Large dataset handling
- **Missing**: Performance benchmarks
- **Effort**: Medium
- **Impact**: Production readiness

#### 4. **Network Tests** - Low Priority
- **Missing**: Multi-node synchronization
- **Missing**: Cross-node conflict resolution
- **Effort**: High
- **Impact**: Distributed functionality

## Test Infrastructure

### Test Classes
1. `SimpleCouchDbTest` - Basic functionality tests
2. `CouchDbTestSuite` - Comprehensive test suite
3. `CouchDbPerformanceTest` - Performance benchmarks
4. `CouchDbNetworkTest` - Network synchronization tests

### Test Utilities
- `TestIpfsPubSubService` - Mock IPFS service for testing
- `defaultNexusAgent` - DSL for creating test agents
- `CouchDbTestData` - Test data generators

## Running Tests

### Individual Test
```bash
./gradlew :nexus:jvmTest --tests "nexus.couchdb.SimpleCouchDbTest.should create and manage databases"
```

### All CouchDB Tests
```bash
./gradlew :nexus:jvmTest --tests "nexus.couchdb.*"
```

### Performance Tests
```bash
./gradlew :nexus:jvmTest --tests "nexus.couchdb.CouchDbPerformanceTest.*"
```

## TDD Workflow

1. **Write Test First**: Start with failing test
2. **Implement Minimum**: Write minimal code to make test pass
3. **Refactor**: Clean up implementation
4. **Repeat**: Move to next test

### Example TDD Cycle

```kotlin
// 1. Write failing test
@Test
fun `should create database successfully`() = runTest {
    val ipfsBridge = createTestBridge()
    ipfsBridge.createDatabase("testdb")
    val dbInfo = ipfsBridge.getDatabaseInfo("testdb")
    assertEquals("testdb", dbInfo.db_name)
}

// 2. Implement minimum code
suspend fun createDatabase(name: String) {
    // Minimal implementation
}

// 3. Refactor and add more tests
```

## Success Criteria

- All tests pass consistently
- Performance benchmarks meet requirements
- Error handling is comprehensive
- Network synchronization works reliably
- Code coverage > 90%

## Next Steps

1. Fix build dependencies
2. Implement missing functionality
3. Add performance benchmarks
4. Create integration tests
5. Document API usage examples 