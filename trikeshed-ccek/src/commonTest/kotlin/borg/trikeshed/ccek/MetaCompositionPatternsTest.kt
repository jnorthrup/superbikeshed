@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ccek

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/**
 * TDD for Meta Composition Patterns:
 * - State Machine: Each state composes the next state
 * - Pipeline: Each stage composes the next context  
 * - Kernel Terminal: Final transition to io_uring + eBPF
 */
class MetaCompositionPatternsTest {
    
    @Test
    fun testStateMachineComposition() = runTest {
        // Test state transitions through context composition
        var stateTrace = mutableListOf<String>()
        
        // Define states
        val initialState = StateElement("INIT") { 
            stateTrace.add("INIT")
        }
        val processingState = StateElement("PROCESSING") {
            stateTrace.add("PROCESSING")
        }
        val completeState = StateElement("COMPLETE") {
            stateTrace.add("COMPLETE")
        }
        
        // Execute state machine
        withStateTransition(initialState, processingState) {
            assertEquals("PROCESSING", coroutineContext[StateElement]?.name)
            
            withStateTransition(coroutineContext[StateElement]!!, completeState) {
                assertEquals("COMPLETE", coroutineContext[StateElement]?.name)
            }
        }
        
        // Verify state progression
        assertEquals(listOf("INIT", "PROCESSING", "COMPLETE"), stateTrace)
    }
    
    @Test
    fun testPipelineStageComposition() = runTest {
        // Test pipeline stages as context flows
        val results = mutableListOf<String>()
        
        // Define pipeline stages
        val ingestionStage = PipelineStage("INGESTION") { data ->
            results.add("Ingested: $data")
            "ingested-$data"
        }
        
        val transformStage = PipelineStage("TRANSFORM") { data ->
            results.add("Transformed: $data")
            "transformed-$data"
        }
        
        val outputStage = PipelineStage("OUTPUT") { data ->
            results.add("Output: $data")
            "output-$data"
        }
        
        // Execute pipeline
        val finalResult = withPipelineStage(ingestionStage) {
            val ingested = coroutineContext[PipelineStage]!!.process("raw-data")
            
            withPipelineStage(transformStage) {
                val transformed = coroutineContext[PipelineStage]!!.process(ingested)
                
                withPipelineStage(outputStage) {
                    coroutineContext[PipelineStage]!!.process(transformed)
                }
            }
        }
        
        // Verify pipeline execution
        assertEquals(
            listOf(
                "Ingested: raw-data",
                "Transformed: ingested-raw-data", 
                "Output: transformed-ingested-raw-data"
            ),
            results
        )
        assertEquals("output-transformed-ingested-raw-data", finalResult)
    }
    
    @Test
    fun testKernelTerminalTransition() = runTest {
        // Test terminal kernel operations
        val operations = mutableListOf<String>()
        
        // Mock kernel contexts
        val uring = IoUringContext { operation ->
            operations.add("io_uring: $operation")
        }
        
        val ebpf = EbpfContext { program ->
            operations.add("eBPF: $program")
        }
        
        // Execute kernel terminal
        withKernelTerminal(uring, ebpf) {
            // Simulate kernel operations
            coroutineContext[IoUringContext]!!.submit("read_file")
            coroutineContext[EbpfContext]!!.load("packet_filter")
            
            // Verify both contexts are available
            assertNotNull(coroutineContext[IoUringContext])
            assertNotNull(coroutineContext[EbpfContext])
        }
        
        // Verify kernel operations
        assertEquals(
            listOf("io_uring: read_file", "eBPF: packet_filter"),
            operations
        )
    }
    
    @Test
    fun testFullMetaCompositionFlow() = runTest {
        // Test complete flow: State Machine → Pipeline → Kernel Terminal
        val trace = mutableListOf<String>()
        
        // Initial state with channel
        val channelState = StateElement("CHANNEL") {
            trace.add("State: CHANNEL")
        }
        
        // Pipeline stage for processing
        val processingPipeline = PipelineStage("PROCESS") { data ->
            trace.add("Pipeline: $data")
            "processed-$data"
        }
        
        // Terminal kernel contexts
        val uring = IoUringContext { op ->
            trace.add("Kernel: io_uring_$op")
        }
        val ebpf = EbpfContext { prog ->
            trace.add("Kernel: eBPF_$prog")
        }
        
        // Execute full composition flow
        withStateTransition(channelState, processingPipeline) {
            // We're now in pipeline context
            val processed = coroutineContext[PipelineStage]!!.process("request")
            
            // Transition to kernel terminal
            withKernelTerminal(uring, ebpf) {
                coroutineContext[IoUringContext]!!.submit("write:$processed")
                coroutineContext[EbpfContext]!!.load("route_packet")
            }
        }
        
        // Verify complete execution trace
        assertEquals(
            listOf(
                "State: CHANNEL",
                "Pipeline: request",
                "Kernel: io_uring_write:processed-request",
                "Kernel: eBPF_route_packet"
            ),
            trace
        )
    }
    
    @Test
    fun testContextAccumulationThroughTransitions() = runTest {
        // Test that contexts accumulate through transitions
        val contexts = mutableListOf<Set<CoroutineContext.Key<*>>>()
        
        val state1 = StateElement("S1") {}
        val pipeline1 = PipelineStage("P1") { it }
        val uring = IoUringContext {}
        val ebpf = EbpfContext {}
        
        withStateTransition(state1, pipeline1) {
            contexts.add(coroutineContext.keys)
            
            withKernelTerminal(uring, ebpf) {
                contexts.add(coroutineContext.keys)
                
                // All contexts should be available
                assertNotNull(coroutineContext[StateElement])
                assertNotNull(coroutineContext[PipelineStage])
                assertNotNull(coroutineContext[IoUringContext])
                assertNotNull(coroutineContext[EbpfContext])
            }
        }
        
        // Verify context accumulation
        assertTrue(contexts[0].contains(StateElement.Key))
        assertTrue(contexts[0].contains(PipelineStage.Key))
        
        assertTrue(contexts[1].contains(StateElement.Key))
        assertTrue(contexts[1].contains(PipelineStage.Key))
        assertTrue(contexts[1].contains(IoUringContext.Key))
        assertTrue(contexts[1].contains(EbpfContext.Key))
    }
    
    @Test
    fun testTailcallOptimization() = runTest {
        // Test that terminal operations can be tailcalls
        var tailcallExecuted = false
        
        val terminalContext = TailcallContext { operation ->
            tailcallExecuted = true
            // This would be a kernel tailcall in real implementation
            "tailcall:$operation"
        }
        
        val result = withContext(terminalContext) {
            // Terminal operation as tailcall
            coroutineContext[TailcallContext]!!.execute("kernel_func")
        }
        
        assertTrue(tailcallExecuted)
        assertEquals("tailcall:kernel_func", result)
    }
}

// Extension to get all keys from a context
internal val CoroutineContext.keys: Set<CoroutineContext.Key<*>>
    get() = fold(emptySet()) { keys, element ->
        keys + element.key
    }