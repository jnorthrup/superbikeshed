package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import borg.trikeshed.scanner.*
import borg.trikeshed.graph.*
import borg.trikeshed.lib.*

/**
 * KSP Symbol Provider powered by Dense Bitgraph
 * 
 * This custom KSP symbol provider bridges the dense bitgraph data
 * into KSP's symbol processing pipeline, enabling AI-enhanced code generation
 * with full evidence chain tracking.
 */
class BitgraphKSPProvider(
    private val entityGraph: KotlinEntityGraph,
    private val resolver: Resolver
) : SymbolProvider {
    
    private val entityIndex = entityGraph.entities.associateBy { it.name.name }
    private val evidenceTracker = mutableMapOf<String, CompleteEvidenceChain>()
    
    /**
     * Bridge entity graph to KSP symbol provider
     */
    fun bridgeToKSP(entityGraph: KotlinEntityGraph): BitgraphKSPProvider {
        return BitgraphKSPProvider(entityGraph, resolver)
    }
    
    /**
     * Find class declarations from bitgraph data
     */
    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
        val entity = entityIndex[name.asString()] ?: return null
        
        return if (entity.type == EntityType.CLASS || 
                   entity.type == EntityType.INTERFACE ||
                   entity.type == EntityType.ENUM) {
            BitgraphClassDeclaration(entity, this)
        } else null
    }
    
    /**
     * Find function declarations from bitgraph data
     */
    override fun getFunctionDeclarationsByName(
        name: KSName,
        includeTopLevel: Boolean
    ): Sequence<KSFunctionDeclaration> {
        return entityGraph.entities
            .filter { it.type == EntityType.FUNCTION && it.name.name == name.asString() }
            .map { BitgraphFunctionDeclaration(it, this) }
            .asSequence()
    }
    
    /**
     * Find property declarations from bitgraph data
     */
    override fun getPropertyDeclarationByName(
        name: KSName,
        includeTopLevel: Boolean
    ): KSPropertyDeclaration? {
        val entity = entityIndex[name.asString()] ?: return null
        
        return if (entity.type == EntityType.PROPERTY) {
            BitgraphPropertyDeclaration(entity, this)
        } else null
    }
    
    /**
     * Get all symbols from bitgraph
     */
    override fun getSymbolsWithAnnotation(
        annotationName: String,
        inDepth: Boolean
    ): Sequence<KSAnnotated> {
        // Find all entities with annotation relationships
        val annotatedEntityIds = entityGraph.relationships
            .filter { it.type == EntityRelationType.ANNOTATED_BY }
            .map { it.sourceId }
            .toSet()
        
        return entityGraph.entities
            .filter { annotatedEntityIds.contains(it.id) }
            .map { createKSSymbol(it) }
            .filterNotNull()
            .asSequence()
    }
    
    /**
     * Track evidence chain for generated code
     */
    fun trackEvidence(symbolName: String, evidenceChain: CompleteEvidenceChain) {
        evidenceTracker[symbolName] = evidenceChain
    }
    
    /**
     * Get evidence chain for a symbol
     */
    fun getEvidenceChain(symbolName: String): CompleteEvidenceChain? {
        return evidenceTracker[symbolName]
    }
    
    private fun createKSSymbol(entity: KotlinEntity): KSAnnotated? {
        return when (entity.type) {
            EntityType.CLASS, EntityType.INTERFACE, EntityType.ENUM -> 
                BitgraphClassDeclaration(entity, this)
            EntityType.FUNCTION -> 
                BitgraphFunctionDeclaration(entity, this)
            EntityType.PROPERTY -> 
                BitgraphPropertyDeclaration(entity, this)
            else -> null
        }
    }
}

/**
 * Bitgraph-backed KSP Class Declaration
 */
class BitgraphClassDeclaration(
    private val entity: KotlinEntity,
    private val provider: BitgraphKSPProvider
) : KSClassDeclaration {
    
    override val simpleName: KSName = KSNameImpl.getCached(entity.name.name)
    override val qualifiedName: KSName? = simpleName
    override val packageName: KSName = KSNameImpl.getCached("") // TODO: Extract from entity
    
    override val classKind: ClassKind = when (entity.type) {
        EntityType.CLASS -> ClassKind.CLASS
        EntityType.INTERFACE -> ClassKind.INTERFACE
        EntityType.ENUM -> ClassKind.ENUM_CLASS
        EntityType.OBJECT -> ClassKind.OBJECT
        else -> ClassKind.CLASS
    }
    
    override val modifiers: Set<Modifier> = entity.modifiers.map { 
        when (it) {
            EntityModifier.ABSTRACT -> Modifier.ABSTRACT
            EntityModifier.FINAL -> Modifier.FINAL
            EntityModifier.OPEN -> Modifier.OPEN
            EntityModifier.DATA -> Modifier.DATA
            EntityModifier.SEALED -> Modifier.SEALED
            EntityModifier.INLINE -> Modifier.INLINE
            else -> null
        }
    }.filterNotNull().toSet()
    
    override val isCompanionObject: Boolean = 
        entity.modifiers.contains(EntityModifier.COMPANION)
    
    // Delegate other properties to default implementations
    override val typeParameters: List<KSTypeParameter> = emptyList()
    override val primaryConstructor: KSFunctionDeclaration? = null
    override val superTypes: Sequence<KSTypeReference> = emptySequence()
    override val declarations: Sequence<KSDeclaration> = emptySequence()
    override val annotations: Sequence<KSAnnotation> = emptySequence()
    override val containingFile: KSFile? = null
    override val location: Location = NonExistLocation
    override val origin: Origin = Origin.SYNTHETIC
    override val parent: KSNode? = null
    
    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }
    
    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> = emptySequence()
    override fun getAllProperties(): Sequence<KSPropertyDeclaration> = emptySequence()
    override fun asStarProjectedType(): KSType = TODO()
    override fun asType(typeArguments: List<KSTypeArgument>): KSType = TODO()
    override fun findActuals(): Sequence<KSDeclaration> = emptySequence()
    override fun findExpects(): Sequence<KSDeclaration> = emptySequence()
    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> = emptySequence()
    override fun toString(): String = "BitgraphClass($simpleName)"
}

/**
 * Bitgraph-backed KSP Function Declaration
 */
class BitgraphFunctionDeclaration(
    private val entity: KotlinEntity,
    private val provider: BitgraphKSPProvider
) : KSFunctionDeclaration {
    
    override val simpleName: KSName = KSNameImpl.getCached(entity.name.name)
    override val qualifiedName: KSName? = simpleName
    override val packageName: KSName = KSNameImpl.getCached("") // TODO: Extract from entity
    
    override val modifiers: Set<Modifier> = entity.modifiers.map {
        when (it) {
            EntityModifier.ABSTRACT -> Modifier.ABSTRACT
            EntityModifier.FINAL -> Modifier.FINAL
            EntityModifier.OPEN -> Modifier.OPEN
            EntityModifier.OVERRIDE -> Modifier.OVERRIDE
            EntityModifier.INLINE -> Modifier.INLINE
            EntityModifier.SUSPEND -> Modifier.SUSPEND
            EntityModifier.OPERATOR -> Modifier.OPERATOR
            EntityModifier.INFIX -> Modifier.INFIX
            EntityModifier.EXTERNAL -> Modifier.EXTERNAL
            else -> null
        }
    }.filterNotNull().toSet()
    
    override val isAbstract: Boolean = entity.modifiers.contains(EntityModifier.ABSTRACT)
    override val functionKind: FunctionKind = FunctionKind.MEMBER // TODO: Determine from context
    
    // Delegate other properties
    override val typeParameters: List<KSTypeParameter> = emptyList()
    override val parameters: List<KSValueParameter> = emptyList()
    override val returnType: KSTypeReference? = null
    override val extensionReceiver: KSTypeReference? = null
    override val annotations: Sequence<KSAnnotation> = emptySequence()
    override val containingFile: KSFile? = null
    override val location: Location = NonExistLocation
    override val origin: Origin = Origin.SYNTHETIC
    override val parent: KSNode? = null
    
    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitFunctionDeclaration(this, data)
    }
    
    override fun findActuals(): Sequence<KSDeclaration> = emptySequence()
    override fun findExpects(): Sequence<KSDeclaration> = emptySequence()
    override fun findOverridee(): KSDeclaration? = null
    override fun asMemberOf(containing: KSType): KSFunction = TODO()
    override fun toString(): String = "BitgraphFunction($simpleName)"
}

/**
 * Bitgraph-backed KSP Property Declaration
 */
class BitgraphPropertyDeclaration(
    private val entity: KotlinEntity,
    private val provider: BitgraphKSPProvider
) : KSPropertyDeclaration {
    
    override val simpleName: KSName = KSNameImpl.getCached(entity.name.name)
    override val qualifiedName: KSName? = simpleName
    override val packageName: KSName = KSNameImpl.getCached("") // TODO: Extract from entity
    
    override val modifiers: Set<Modifier> = entity.modifiers.map {
        when (it) {
            EntityModifier.ABSTRACT -> Modifier.ABSTRACT
            EntityModifier.FINAL -> Modifier.FINAL
            EntityModifier.OPEN -> Modifier.OPEN
            EntityModifier.OVERRIDE -> Modifier.OVERRIDE
            EntityModifier.INLINE -> Modifier.INLINE
            EntityModifier.EXTERNAL -> Modifier.EXTERNAL
            else -> null
        }
    }.filterNotNull().toSet()
    
    override val isMutable: Boolean = true // TODO: Determine from entity metadata
    override val isAbstract: Boolean = entity.modifiers.contains(EntityModifier.ABSTRACT)
    override val extensionReceiver: KSTypeReference? = null
    
    // Delegate other properties
    override val type: KSTypeReference = TODO()
    override val hasBackingField: Boolean = true
    override val setter: KSPropertySetter? = null
    override val getter: KSPropertyGetter? = null
    override val typeParameters: List<KSTypeParameter> = emptyList()
    override val annotations: Sequence<KSAnnotation> = emptySequence()
    override val containingFile: KSFile? = null
    override val location: Location = NonExistLocation
    override val origin: Origin = Origin.SYNTHETIC
    override val parent: KSNode? = null
    
    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }
    
    override fun findActuals(): Sequence<KSDeclaration> = emptySequence()
    override fun findExpects(): Sequence<KSDeclaration> = emptySequence()
    override fun findOverridee(): KSPropertyDeclaration? = null
    override fun asMemberOf(containing: KSType): KSType = TODO()
    override fun isDelegated(): Boolean = false
    override fun toString(): String = "BitgraphProperty($simpleName)"
}

/**
 * Non-existent location for synthetic symbols
 */
object NonExistLocation : Location {
    override val filePath: String = "<bitgraph>"
}

/**
 * Extension to convert list to sequence
 */
private fun <T> List<T>.asSequence(): Sequence<T> = this.asIterable().asSequence()