# Key Composition Graph with Key Names

## Complete Key Graph from main()

```mermaid
graph RL
    subgraph "Platform Bootstrap"
        MAIN["main()"] 
        MAIN --> PLATFORM["detectPlatform()"]
        PLATFORM --> CP_LINUX["IoUringChannelProvider"]
        PLATFORM --> CP_JVM["NioChannelProvider"]
        PLATFORM --> CP_WASM["FetchChannelProvider"]
    end
    
    subgraph "Service Context Keys"
        SC["serviceContext = CoroutineContext +"]
        SC --> CSK["ChannelService.Key → provider"] 
        SC --> RSK["RecordingService.Key → recorder"]
        SC --> TSK["TraceService.Key → tracer"]
        SC --> MSK["MetricsService.Key → collector"]
        SC --> ASK["AuthService.Key → authenticator"]
        SC --> SSK["SessionService.Key → store"]
        SC --> CASK["CacheService.Key → cache"]
    end
    
    subgraph "Protocol Adapter Keys"
        SC --> HPAK["HttpProtocolAdapter.Key → Http/1.1/2/3"]
        SC --> QPAK["QuicProtocolAdapter.Key → QUIC/UDP"]
        SC --> SPAK["SocksProtocolAdapter.Key → SOCKS5"]
        SC --> SSHPAK["SSHProtocolAdapter.Key → SSH"]
        SC --> TLSPAK["TLSProtocolAdapter.Key → TLS1.3"]
        SC --> BTPAK["BitTorrentAdapter.Key → BitTorrent"]
        SC --> CDBPAK["CouchDBAdapter.Key → CouchDB"]
        SC --> IPFSPAK["IPFSAdapter.Key → IPFS"]
        SC --> GRPCPAK["GRPCAdapter.Key → gRPC"]
        SC --> DNSPAK["DNSAdapter.Key → DNS"]
    end
    
    subgraph "Protocol Server Launches"
        WC["withContext(serviceContext)"]
        WC --> HS["launch { runHttpServer(8080) }"]
        WC --> H3S["launch { runHttp3Server(8443) }"]
        WC --> QS["launch { runQuicServer(4433) }"]
        WC --> SS["launch { runSocksProxy(1080) }"]
        WC --> SSHS["launch { runSSHServer(22) }"]
        WC --> BTS["launch { runBitTorrentTracker(6881) }"]
        WC --> CDBS["launch { runCouchDBServer(5984) }"]
        WC --> IPFSS["launch { runIPFSNode(4001) }"]
        WC --> GRPCS["launch { runGRPCServer(9000) }"]
        WC --> DNSS["launch { runDNSServer(53) }"]
        WC --> MA["launch { runManagementApi(9090) }"]
    end
    
    subgraph "HTTP Server Key Discovery"
        HS --> HCS["requireService<ChannelService>()"]
        HS --> HAS["currentService<AuthService>()"]
        HS --> HSS["currentService<SessionService>()"]
        HS --> HHA["requireService<HttpProtocolAdapter>()"]
        
        HCS --> CSK
        HAS --> ASK
        HSS --> SSK
        HHA --> HPAK
    end
    
    subgraph "QUIC Server Key Discovery"
        QS --> QCS["requireService<ChannelService>()"]
        QS --> QMS["currentService<MetricsService>()"]
        QS --> QTS["currentService<TraceService>()"]
        QS --> QQA["requireService<QuicProtocolAdapter>()"]
        
        QCS --> CSK
        QMS --> MSK
        QTS --> TSK
        QQA --> QPAK
    end
    
    subgraph "SOCKS Proxy Key Discovery"
        SS --> SCS["requireService<ChannelService>()"]
        SS --> SAS["currentService<AuthService>()"]
        SS --> SRS["currentService<RecordingService>()"]
        SS --> SSA["requireService<SocksProtocolAdapter>()"]
        
        SCS --> CSK
        SAS --> ASK
        SRS --> RSK
        SSA --> SPAK
    end
    
    style MAIN fill:#f9f,stroke:#333,stroke-width:4px
    style CSK fill:#bbf,stroke:#333,stroke-width:2px
    style RSK fill:#bbf,stroke:#333,stroke-width:2px
    style TSK fill:#bbf,stroke:#333,stroke-width:2px
    style MSK fill:#bbf,stroke:#333,stroke-width:2px
    style ASK fill:#bbf,stroke:#333,stroke-width:2px
    style SSK fill:#bbf,stroke:#333,stroke-width:2px
    style CASK fill:#bbf,stroke:#333,stroke-width:2px
    style HPAK fill:#fbb,stroke:#333,stroke-width:2px
    style QPAK fill:#fbb,stroke:#333,stroke-width:2px
    style SPAK fill:#fbb,stroke:#333,stroke-width:2px
```

## Key Resolution Flow

```mermaid
sequenceDiagram
    participant main as main()
    participant ctx as serviceContext
    participant http as HTTP Server
    participant quic as QUIC Server
    participant socks as SOCKS Proxy
    
    note over main: Platform Detection
    main->>main: detectPlatform()
    main->>main: selectChannelProvider()
    
    note over main: Service Composition
    main->>ctx: ChannelService.Key + provider
    main->>ctx: TraceService.Key + tracer
    main->>ctx: MetricsService.Key + collector
    main->>ctx: AuthService.Key + authenticator
    main->>ctx: HttpProtocolAdapter.Key + config
    main->>ctx: QuicProtocolAdapter.Key + config
    main->>ctx: SocksProtocolAdapter.Key + config
    
    note over main: Protocol Launch
    main->>http: launch { runHttpServer() }
    main->>quic: launch { runQuicServer() }
    main->>socks: launch { runSocksProxy() }
    
    note over http: HTTP Key Discovery
    http->>ctx: requireService<ChannelService>()
    ctx-->>http: ChannelService.Key → provider
    http->>ctx: currentService<AuthService>()
    ctx-->>http: AuthService.Key → authenticator
    http->>ctx: requireService<HttpProtocolAdapter>()
    ctx-->>http: HttpProtocolAdapter.Key → config
    
    note over quic: QUIC Key Discovery
    quic->>ctx: requireService<ChannelService>()
    ctx-->>quic: ChannelService.Key → provider
    quic->>ctx: currentService<MetricsService>()
    ctx-->>quic: MetricsService.Key → collector
    quic->>ctx: currentService<TraceService>()
    ctx-->>quic: TraceService.Key → tracer
    
    note over socks: SOCKS Key Discovery
    socks->>ctx: requireService<ChannelService>()
    ctx-->>socks: ChannelService.Key → provider
    socks->>ctx: currentService<RecordingService>()
    ctx-->>socks: RecordingService.Key → recorder
```

## Channel Operation with Keys

```mermaid
graph LR
    subgraph "Protocol Request"
        REQ["HTTP Request"]
        REQ --> PARSE["adapter.parseRequest()"]
    end
    
    subgraph "Key Lookups"
        PARSE --> CS["context[ChannelService.Key]"]
        PARSE --> AS["context[AuthService.Key]"]
        PARSE --> TS["context[TraceService.Key]"]
    end
    
    subgraph "Service Instances"
        CS --> CSI["IoUringChannelProvider"]
        AS --> ASI["JwtAuthenticator"]
        TS --> TSI["JaegerTracer"]
    end
    
    subgraph "Operations"
        CSI --> CH["channel.read/write"]
        ASI --> AUTH["authenticate()"]
        TSI --> TRACE["trace()"]
    end
    
    style CS fill:#bbf
    style AS fill:#bbf
    style TS fill:#bbf
```

## Key Dependencies Matrix

| Protocol | ChannelService.Key | AuthService.Key | TraceService.Key | MetricsService.Key | RecordingService.Key |
|----------|-------------------|-----------------|------------------|-------------------|---------------------|
| HTTP     | ✅ Required       | 🟡 Optional     | 🟡 Optional      | 🟡 Optional       | ❌ Not used         |
| QUIC     | ✅ Required       | ❌ Not used     | 🟡 Optional      | 🟡 Optional       | ❌ Not used         |
| SOCKS    | ✅ Required       | 🟡 Optional     | ❌ Not used      | ❌ Not used       | 🟡 Optional         |

## Runtime Key Resolution

```kotlin
// Key discovery happens at protocol startup
suspend fun runHttpServer(port: Int) {
    // These resolve to actual instances via key lookup
    val channelService = requireService<ChannelService>()          // ChannelService.Key
    val authService = currentService<AuthService>()               // AuthService.Key?
    val httpAdapter = requireService<HttpProtocolAdapter>()       // HttpProtocolAdapter.Key
    
    // Key-resolved services used throughout request handling
    val serverChannel = channelService.openChannel(config)
    // ...
}
```