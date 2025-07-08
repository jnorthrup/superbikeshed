# Superproject Key Legend

This document catalogs all `CoroutineContext.Key` usages in the project, their associated API/context types, and whether those types are multiplatform (`expect`/`actual`) or not. This legend is updated as new keys are added or refactored.

| Key Name / Symbol                | Type Parameter / API           | Is `expect`? | Notes / API Description                |
|----------------------------------|-------------------------------|:------------:|----------------------------------------|
| ExecutionId.Key                  | ExecutionId                   | No           | CCEK context element                   |
| ExecutionPhase.Key               | ExecutionPhase                | No           | CCEK context element                   |
| TransformationRules.Key          | TransformationRules           | No           | CCEK context element                   |
| ValidationConstraints.Key        | ValidationConstraints         | No           | CCEK context element                   |
| IoPreference.Key                 | IoPreference                  | No           | I/O preference context                 |
| ProtocolChannels.Key             | ProtocolChannels              | No           | Protocol channel registry              |
| HandlerRegistry.Key              | HandlerRegistry<*, *>         | No           | Handler registry context               |
| ChannelService.Key               | ChannelService                | No           | Channel API context                    |
| RecordingService.Key             | RecordingService              | No           | Channel recording context              |
| SocksIngressChannelKey           | SocksIngressChannelElement    | No           | SOCKS ingress channel context          |
| SocksEgressChannelKey            | SocksEgressChannelElement     | No           | SOCKS egress channel context           |
| Socks5ChannelKey                 | Socks5ChannelElement          | No           | SOCKS5 channel context                 |
| IpfsClientContext.Key            | IpfsClientContext             | No           | IPFS client context                    |
| IpfsServerContext.Key            | IpfsServerContext             | No           | IPFS server context                    |
| DHTServiceContext.Key            | DHTServiceContext             | No           | DHT service context                    |
| IpfsStorageContext.Key           | IpfsStorageContext            | No           | IPFS storage context                   |
| IpfsPubSubContext.Key            | IpfsPubSubContext             | No           | IPFS pubsub context                    |
| IpfsConfigContext.Key            | IpfsConfigContext             | No           | IPFS config context                    |
| IpfsPeerContext.Key              | IpfsPeerContext               | No           | IPFS peer context                      |
| IpfsContentContext.Key           | IpfsContentContext            | No           | IPFS content context                   |
| IpfsNetworkContext.Key           | IpfsNetworkContext            | No           | IPFS network context                   |
| LatencyProviderKey               | LatencyProvider               | No           | Moneyfan latency provider context      |
| URingContext.Key                 | URingContext                  | No           | io_uring context (superbikeshed-mcp)   |
| ProtocolContext.Key              | ProtocolContext               | No           | Protocol context (superbikeshed-mcp)   |
| BufferContext.Key                | BufferContext                 | No           | Buffer context (superbikeshed-mcp)     |
| PerformanceContext.Key           | PerformanceContext            | No           | Performance context (superbikeshed-mcp)|
| SecurityContext.Key              | SecurityContext               | No           | Security context (superbikeshed-mcp)   |
| FlowControlContext.Key           | FlowControlContext            | No           | Flow control context (superbikeshed-mcp)|
| MonitoringContext.Key            | MonitoringContext             | No           | Monitoring context (superbikeshed-mcp) |
| ResilienceContext.Key            | ResilienceContext             | No           | Resilience context (superbikeshed-mcp) |
| DealService.Key                  | DealService                   | No           | Request factory service                |
| ProcessExecutionService.Key      | ProcessExecutionService       | No           | Nexus process execution service        |
| EvolutionEngine.Key              | EvolutionEngine               | No           | Nexus DGM service                      |
| SolutionProposerService.Key      | SolutionProposerService       | No           | Nexus DGM service                      |
| WorkspaceService.Key             | WorkspaceService              | No           | Nexus DGM service                      |
| BenchmarkValidationService.Key   | BenchmarkValidationService    | No           | Nexus DGM service                      |
| DgmStateService.Key              | DgmStateService               | No           | Nexus DGM service                      |
| HostKeyVerifierKey               | CoroutineContext.Element      | No           | SSH host key verifier                  |
| LoadBalancingService.Key         | LoadBalancingService          | No           | Reactor CCEK service                   |
| CircuitBreakerService.Key        | CircuitBreakerService         | No           | Reactor CCEK service                   |
| CachingService.Key               | CachingService                | No           | Reactor CCEK service                   |
| AuthenticationService.Key        | AuthenticationService         | No           | Reactor CCEK service                   |
| AuthCredentials.Key              | AuthCredentials               | No           | Reactor CCEK service                   |
| AuthToken.Key                    | AuthToken                     | No           | Reactor CCEK service                   |
| HttpOverTcpStack.Key             | HttpOverTcpStack              | No           | Reactor protocol stack                 |
| WebSocketOverHttpStack.Key       | WebSocketOverHttpStack        | No           | Reactor protocol stack                 |
| ConsensusLayer.Key               | ConsensusLayer                | No           | Reactor distributed coordination       |
| DistributedLockService.Key       | DistributedLockService        | No           | Reactor distributed coordination       |
| EventualConsistencyLayer.Key     | EventualConsistencyLayer      | No           | Reactor distributed coordination       |
| BackpressureLayer.Key            | BackpressureLayer             | No           | Reactor reactive streams               |
| StreamProcessingLayer.Key        | StreamProcessingLayer         | No           | Reactor reactive streams               |
| ReactiveEventBus.Key             | ReactiveEventBus              | No           | Reactor reactive streams               |
| FlowControlLayer.Key             | FlowControlLayer              | No           | Reactor reactive streams               |
| TailcallOptimizationLayer.Key    | TailcallOptimizationLayer     | No           | Reactor terminal operations            |
| CompletionLayer.Key              | CompletionLayer               | No           | Reactor terminal operations            |
| IoUringLayer.Key                 | IoUringLayer                  | No           | Reactor kernel integration             |
| EbpfProgramLayer.Key             | EbpfProgramLayer              | No           | Reactor kernel integration             |
| KqueueLayer.Key                  | KqueueLayer                   | No           | Reactor kernel integration             |
| CCEKChannelization.Key           | CCEKChannelization            | No           | Reactor channelization hierarchy       |
| CRDTChannelEngine.Key            | CRDTChannelEngine             | No           | CCEK CRDT integration                  |
| CRDTRequestFactory.Key           | CRDTRequestFactory            | No           | CCEK CRDT integration                  |
| ChannelizedRequestFactory.Key    | ChannelizedRequestFactory     | No           | CCEK channelized request factory       |
| MobilityCapability.Key           | MobilityCapability            | No           | CCEK mobility capability               |
| HTTPProtocolAdapter.Key          | HTTPProtocolAdapter           | No           | CCEK meta context composition          |
| QUICProtocolAdapter.Key          | QUICProtocolAdapter           | No           | CCEK meta context composition          |
| StateElement.Key                 | StateElement                  | No           | CCEK meta composition patterns         |
| PipelineStage.Key                | PipelineStage                 | No           | CCEK meta composition patterns         |
| IoUringContext.Key               | IoUringContext                | No           | CCEK meta composition patterns         |
| EbpfContext.Key                  | EbpfContext                   | No           | CCEK meta composition patterns         |
| CcekContextKey                   | CcekContext                   | No           | CCEK context (trikeshed-ccek)          |
| UringBatchKey                    | UringBatchContext             | No           | CCEK context (trikeshed-ccek)          |
| ChannelChainKey                  | ChannelChainContext           | No           | CCEK context (trikeshed-ccek)          |
| AsyncChannelKey                  | AsyncChannelContext           | No           | CCEK context (trikeshed-ccek)          |
| CursorContext.Key                | CursorContext                 | No           | CCEK cursor context                    |
| ...                              | ...                           | ...          | ...                                    |

**Legend:**
- `Key Name / Symbol`: The object or companion used as the key.
- `Type Parameter / API`: The type parameter for the key (the API or context element).
- `Is expect?`: Whether the type is multiplatform (`expect`/`actual`).
- `Notes / API Description`: Brief description of the API/context.

> **Note:** This table is a living document. Update as new keys are introduced or refactored to multiplatform (`expect`) types. 