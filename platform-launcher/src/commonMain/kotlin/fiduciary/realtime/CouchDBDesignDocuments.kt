package fiduciary.realtime

import borg.trikeshed.couchdb.*
import borg.trikeshed.lib.*
import kotlinx.serialization.json.*

/**
 * CouchDB Design Documents for Fiduciary Realtime System
 * 
 * Provides views, indexes, and filters for:
 * - Attention-based document prioritization
 * - Document type filtering
 * - Processing status tracking
 * - Relationship mapping
 * - Real-time change feeds
 */

object FiduciaryDesignDocuments {
    
    /**
     * Main design document for fiduciary documents
     */
    val fiduciaryDocuments = DesignDocument(
        id = "_design/fiduciary_documents",
        language = "javascript",
        views = mapOf(
            // Attention score view for prioritization
            "by_attention_score" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.type && doc.attentionScore !== undefined) {
                            emit(doc.attentionScore, {
                                id: doc._id,
                                type: doc.type,
                                processingStatus: doc.processingStatus,
                                createdAt: doc.createdAt,
                                updatedAt: doc.updatedAt,
                                tags: doc.tags || []
                            });
                        }
                    }
                """.trimIndent(),
                reduce = """
                    function(keys, values, rereduce) {
                        if (rereduce) {
                            return values.reduce(function(acc, val) {
                                return {
                                    count: acc.count + val.count,
                                    avgScore: (acc.avgScore * acc.count + val.avgScore * val.count) / (acc.count + val.count),
                                    highPriority: acc.highPriority + val.highPriority
                                };
                            });
                        } else {
                            var count = values.length;
                            var totalScore = values.reduce(function(sum, val) { return sum + val.attentionScore; }, 0);
                            var avgScore = totalScore / count;
                            var highPriority = values.filter(function(val) { return val.attentionScore > 0.7; }).length;
                            
                            return {
                                count: count,
                                avgScore: avgScore,
                                highPriority: highPriority
                            };
                        }
                    }
                """.trimIndent()
            ),
            
            // Document type view for filtering
            "by_type" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.type) {
                            emit(doc.type, {
                                id: doc._id,
                                attentionScore: doc.attentionScore || 0,
                                processingStatus: doc.processingStatus,
                                createdAt: doc.createdAt,
                                updatedAt: doc.updatedAt
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // Processing status view for workflow tracking
            "by_processing_status" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.processingStatus) {
                            emit(doc.processingStatus, {
                                id: doc._id,
                                type: doc.type,
                                attentionScore: doc.attentionScore || 0,
                                createdAt: doc.createdAt,
                                updatedAt: doc.updatedAt
                            });
                        }
                    }
                """.trimIndent(),
                reduce = """
                    function(keys, values, rereduce) {
                        if (rereduce) {
                            return values.reduce(function(acc, val) {
                                return {
                                    count: acc.count + val.count,
                                    avgAttentionScore: (acc.avgAttentionScore * acc.count + val.avgAttentionScore * val.count) / (acc.count + val.count)
                                };
                            });
                        } else {
                            var count = values.length;
                            var totalScore = values.reduce(function(sum, val) { return sum + (val.attentionScore || 0); }, 0);
                            var avgScore = totalScore / count;
                            
                            return {
                                count: count,
                                avgAttentionScore: avgScore
                            };
                        }
                    }
                """.trimIndent()
            ),
            
            // Creation time view for temporal analysis
            "by_created_at" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.createdAt) {
                            emit(doc.createdAt, {
                                id: doc._id,
                                type: doc.type,
                                attentionScore: doc.attentionScore || 0,
                                processingStatus: doc.processingStatus
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // Update time view for change tracking
            "by_updated_at" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.updatedAt) {
                            emit(doc.updatedAt, {
                                id: doc._id,
                                type: doc.type,
                                attentionScore: doc.attentionScore || 0,
                                processingStatus: doc.processingStatus
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // Tags view for categorization
            "by_tags" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.tags && Array.isArray(doc.tags)) {
                            doc.tags.forEach(function(tag) {
                                emit(tag, {
                                    id: doc._id,
                                    type: doc.type,
                                    attentionScore: doc.attentionScore || 0,
                                    processingStatus: doc.processingStatus
                                });
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // Relationships view for graph construction
            "by_relationships" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.relationships && Array.isArray(doc.relationships)) {
                            doc.relationships.forEach(function(rel) {
                                emit([rel.sourceId, rel.targetId, rel.relationshipType], {
                                    id: doc._id,
                                    weight: rel.weight || 1.0,
                                    metadata: rel.metadata || {}
                                });
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // Multicore scanning status view
            "by_scan_status" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.processingStatus === 'SCANNING' || doc.processingStatus === 'SCANNED') {
                            emit(doc.processingStatus, {
                                id: doc._id,
                                type: doc.type,
                                attentionScore: doc.attentionScore || 0,
                                chunkId: doc.chunkId || null,
                                scanTimestamp: doc.scanTimestamp || null
                            });
                        }
                    }
                """.trimIndent()
            ),
            
            // LLM processing status view
            "by_llm_status" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.processingStatus === 'LLM_PROCESSING' || doc.processingStatus === 'ANALYZED') {
                            emit(doc.processingStatus, {
                                id: doc._id,
                                type: doc.type,
                                attentionScore: doc.attentionScore || 0,
                                analysisConfidence: doc.analysisConfidence || 0,
                                llmModel: doc.llmModel || 'unknown'
                            });
                        }
                    }
                """.trimIndent()
            )
        ),
        
        // Filters for change feeds
        filters = mapOf(
            "attention_threshold" to """
                function(doc, req) {
                    var threshold = parseFloat(req.query.threshold) || 0.5;
                    return doc.attentionScore >= threshold;
                }
            """.trimIndent(),
            
            "document_type" to """
                function(doc, req) {
                    var allowedTypes = req.query.types ? req.query.types.split(',') : [];
                    return allowedTypes.length === 0 || allowedTypes.includes(doc.type);
                }
            """.trimIndent(),
            
            "processing_status" to """
                function(doc, req) {
                    var allowedStatuses = req.query.statuses ? req.query.statuses.split(',') : [];
                    return allowedStatuses.length === 0 || allowedStatuses.includes(doc.processingStatus);
                }
            """.trimIndent(),
            
            "high_priority" to """
                function(doc, req) {
                    return doc.attentionScore >= 0.7 || doc.processingStatus === 'ERROR';
                }
            """.trimIndent()
        ),
        
        // Update functions for processing
        updates = mapOf(
            "update_attention_score" to """
                function(doc, req) {
                    if (req.body && req.body.attentionScore !== undefined) {
                        doc.attentionScore = req.body.attentionScore;
                        doc.updatedAt = new Date().toISOString();
                        return [doc, {json: {success: true, attentionScore: doc.attentionScore}}];
                    }
                    return [null, {json: {error: 'Missing attentionScore'}}];
                }
            """.trimIndent(),
            
            "update_processing_status" to """
                function(doc, req) {
                    if (req.body && req.body.processingStatus) {
                        doc.processingStatus = req.body.processingStatus;
                        doc.updatedAt = new Date().toISOString();
                        
                        if (req.body.scanTimestamp) {
                            doc.scanTimestamp = req.body.scanTimestamp;
                        }
                        if (req.body.llmTimestamp) {
                            doc.llmTimestamp = req.body.llmTimestamp;
                        }
                        
                        return [doc, {json: {success: true, processingStatus: doc.processingStatus}}];
                    }
                    return [null, {json: {error: 'Missing processingStatus'}}];
                }
            """.trimIndent(),
            
            "add_relationships" to """
                function(doc, req) {
                    if (req.body && req.body.relationships) {
                        if (!doc.relationships) {
                            doc.relationships = [];
                        }
                        doc.relationships = doc.relationships.concat(req.body.relationships);
                        doc.updatedAt = new Date().toISOString();
                        return [doc, {json: {success: true, relationshipCount: doc.relationships.length}}];
                    }
                    return [null, {json: {error: 'Missing relationships'}}];
                }
            """.trimIndent(),
            
            "add_tags" to """
                function(doc, req) {
                    if (req.body && req.body.tags) {
                        if (!doc.tags) {
                            doc.tags = [];
                        }
                        req.body.tags.forEach(function(tag) {
                            if (doc.tags.indexOf(tag) === -1) {
                                doc.tags.push(tag);
                            }
                        });
                        doc.updatedAt = new Date().toISOString();
                        return [doc, {json: {success: true, tagCount: doc.tags.length}}];
                    }
                    return [null, {json: {error: 'Missing tags'}}];
                }
            """.trimIndent()
        ),
        
        // Validation function
        validate_doc_update = """
            function(newDoc, oldDoc, userCtx, secObj) {
                // Require authentication for write operations
                if (userCtx.roles.indexOf('_admin') === -1 && userCtx.roles.indexOf('fiduciary_writer') === -1) {
                    throw({forbidden: 'Insufficient privileges'});
                }
                
                // Validate required fields
                if (!newDoc.type) {
                    throw({forbidden: 'Document type is required'});
                }
                
                if (!newDoc.content) {
                    throw({forbidden: 'Document content is required'});
                }
                
                // Validate attention score range
                if (newDoc.attentionScore !== undefined && (newDoc.attentionScore < 0 || newDoc.attentionScore > 1)) {
                    throw({forbidden: 'Attention score must be between 0 and 1'});
                }
                
                // Validate processing status
                var validStatuses = ['PENDING', 'SCANNING', 'LLM_PROCESSING', 'ANALYZED', 'ATTENTION_SCORED', 'RELATIONSHIP_MAPPED', 'COMPLETED', 'ERROR'];
                if (newDoc.processingStatus && validStatuses.indexOf(newDoc.processingStatus) === -1) {
                    throw({forbidden: 'Invalid processing status'});
                }
                
                return true;
            }
        """.trimIndent()
    )
    
    /**
     * Design document for multicore scanning operations
     */
    val multicoreScanning = DesignDocument(
        id = "_design/multicore_scanning",
        language = "javascript",
        views = mapOf(
            // Chunk processing status
            "by_chunk_status" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.chunkId !== undefined) {
                            emit([doc.chunkId, doc.processingStatus], {
                                id: doc._id,
                                documentCount: doc.documentCount || 1,
                                processingTime: doc.processingTime || 0,
                                scanTimestamp: doc.scanTimestamp || null
                            });
                        }
                    }
                """.trimIndent(),
                reduce = """
                    function(keys, values, rereduce) {
                        if (rereduce) {
                            return values.reduce(function(acc, val) {
                                return {
                                    totalDocuments: acc.totalDocuments + val.totalDocuments,
                                    totalTime: acc.totalTime + val.totalTime,
                                    avgTime: (acc.totalTime + val.totalTime) / (acc.totalDocuments + val.totalDocuments)
                                };
                            });
                        } else {
                            var totalDocs = values.reduce(function(sum, val) { return sum + val.documentCount; }, 0);
                            var totalTime = values.reduce(function(sum, val) { return sum + val.processingTime; }, 0);
                            var avgTime = totalTime / totalDocs;
                            
                            return {
                                totalDocuments: totalDocs,
                                totalTime: totalTime,
                                avgTime: avgTime
                            };
                        }
                    }
                """.trimIndent()
            ),
            
            // Core utilization tracking
            "by_core_utilization" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.coreId !== undefined) {
                            emit(doc.coreId, {
                                id: doc._id,
                                documentsProcessed: doc.documentsProcessed || 0,
                                processingTime: doc.processingTime || 0,
                                throughput: doc.throughput || 0,
                                timestamp: doc.timestamp || null
                            });
                        }
                    }
                """.trimIndent()
            )
        )
    )
    
    /**
     * Design document for LLM processing operations
     */
    val llmProcessing = DesignDocument(
        id = "_design/llm_processing",
        language = "javascript",
        views = mapOf(
            // LLM model performance
            "by_model_performance" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.llmModel && doc.processingStatus === 'ANALYZED') {
                            emit(doc.llmModel, {
                                id: doc._id,
                                processingTime: doc.processingTime || 0,
                                confidence: doc.analysisConfidence || 0,
                                success: doc.success !== false,
                                error: doc.error || null
                            });
                        }
                    }
                """.trimIndent(),
                reduce = """
                    function(keys, values, rereduce) {
                        if (rereduce) {
                            return values.reduce(function(acc, val) {
                                return {
                                    totalRequests: acc.totalRequests + val.totalRequests,
                                    successfulRequests: acc.successfulRequests + val.successfulRequests,
                                    totalTime: acc.totalTime + val.totalTime,
                                    avgConfidence: (acc.avgConfidence * acc.totalRequests + val.avgConfidence * val.totalRequests) / (acc.totalRequests + val.totalRequests)
                                };
                            });
                        } else {
                            var totalRequests = values.length;
                            var successfulRequests = values.filter(function(val) { return val.success; }).length;
                            var totalTime = values.reduce(function(sum, val) { return sum + val.processingTime; }, 0);
                            var avgConfidence = values.reduce(function(sum, val) { return sum + val.confidence; }, 0) / totalRequests;
                            
                            return {
                                totalRequests: totalRequests,
                                successfulRequests: successfulRequests,
                                successRate: successfulRequests / totalRequests,
                                totalTime: totalTime,
                                avgTime: totalTime / totalRequests,
                                avgConfidence: avgConfidence
                            };
                        }
                    }
                """.trimIndent()
            ),
            
            // Processing queue status
            "by_queue_status" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.processingStatus === 'PENDING' || doc.processingStatus === 'LLM_PROCESSING') {
                            emit([doc.processingStatus, doc.attentionScore], {
                                id: doc._id,
                                type: doc.type,
                                queuePosition: doc.queuePosition || 0,
                                estimatedWaitTime: doc.estimatedWaitTime || 0
                            });
                        }
                    }
                """.trimIndent()
            )
        )
    )
    
    /**
     * Design document for realtime change feeds
     */
    val realtimeChanges = DesignDocument(
        id = "_design/realtime_changes",
        language = "javascript",
        views = mapOf(
            // Change frequency analysis
            "by_change_frequency" to ViewDefinition(
                map = """
                    function(doc) {
                        if (doc.updatedAt) {
                            var hour = new Date(doc.updatedAt).getHours();
                            emit(hour, {
                                id: doc._id,
                                type: doc.type,
                                changeType: doc.changeType || 'update'
                            });
                        }
                    }
                """.trimIndent(),
                reduce = """
                    function(keys, values, rereduce) {
                        if (rereduce) {
                            return values.reduce(function(acc, val) {
                                return {
                                    totalChanges: acc.totalChanges + val.totalChanges,
                                    byType: Object.assign({}, acc.byType, val.byType)
                                };
                            });
                        } else {
                            var totalChanges = values.length;
                            var byType = {};
                            values.forEach(function(val) {
                                byType[val.changeType] = (byType[val.changeType] || 0) + 1;
                            });
                            
                            return {
                                totalChanges: totalChanges,
                                byType: byType
                            };
                        }
                    }
                """.trimIndent()
            )
        ),
        
        // Filters for realtime subscriptions
        filters = mapOf(
            "attention_alerts" to """
                function(doc, req) {
                    var threshold = parseFloat(req.query.threshold) || 0.8;
                    return doc.attentionScore >= threshold;
                }
            """.trimIndent(),
            
            "status_changes" to """
                function(doc, req) {
                    var oldStatus = req.query.oldStatus;
                    var newStatus = req.query.newStatus;
                    return doc.processingStatus === newStatus;
                }
            """.trimIndent(),
            
            "error_alerts" to """
                function(doc, req) {
                    return doc.processingStatus === 'ERROR';
                }
            """.trimIndent()
        )
    )
}

/**
 * Helper functions for working with design documents
 */
object DesignDocumentHelpers {
    
    /**
     * Create all design documents in a database
     */
    suspend fun createDesignDocuments(couchClient: CouchClient, database: String) {
        val designDocs = listOf(
            FiduciaryDesignDocuments.fiduciaryDocuments,
            FiduciaryDesignDocuments.multicoreScanning,
            FiduciaryDesignDocuments.llmProcessing,
            FiduciaryDesignDocuments.realtimeChanges
        )
        
        designDocs.forEach { designDoc ->
            try {
                couchClient.putDesignDocument(database, designDoc)
            } catch (e: Exception) {
                // Design document might already exist, ignore error
            }
        }
    }
    
    /**
     * Get high attention documents using view
     */
    suspend fun getHighAttentionDocuments(
        couchClient: CouchClient,
        database: String,
        threshold: Double = 0.7,
        limit: Int = 100
    ): Indexed<FiduciaryRealtimeDocument> {
        val response = couchClient.queryView(
            dbName = database,
            designDoc = "fiduciary_documents",
            viewName = "by_attention_score",
            params = ViewQueryParams(
                startkey = threshold,
                descending = true,
                limit = limit,
                includeDocs = true
            )
        )
        
        return \1 j { \2: Int ->
            val row = response.rows[i]
            FiduciaryRealtimeDocument.fromCouchDocument(row.doc ?: CouchDocument())
        }
    }
    
    /**
     * Get documents by processing status
     */
    suspend fun getDocumentsByStatus(
        couchClient: CouchClient,
        database: String,
        status: ProcessingStatus,
        limit: Int = 100
    ): Indexed<FiduciaryRealtimeDocument> {
        val response = couchClient.queryView(
            dbName = database,
            designDoc = "fiduciary_documents",
            viewName = "by_processing_status",
            params = ViewQueryParams(
                key = status.name,
                limit = limit,
                includeDocs = true
            )
        )
        
        return \1 j { \2: Int ->
            val row = response.rows[i]
            FiduciaryRealtimeDocument.fromCouchDocument(row.doc ?: CouchDocument())
        }
    }
    
    /**
     * Get documents by type
     */
    suspend fun getDocumentsByType(
        couchClient: CouchClient,
        database: String,
        type: DocumentType,
        limit: Int = 100
    ): Indexed<FiduciaryRealtimeDocument> {
        val response = couchClient.queryView(
            dbName = database,
            designDoc = "fiduciary_documents",
            viewName = "by_type",
            params = ViewQueryParams(
                key = type.name,
                limit = limit,
                includeDocs = true
            )
        )
        
        return \1 j { \2: Int ->
            val row = response.rows[i]
            FiduciaryRealtimeDocument.fromCouchDocument(row.doc ?: CouchDocument())
        }
    }
} 