// package borg.trikeshed.reactor.socks

// import borg.trikeshed.lib.*

// /**
//  * SOCKS5 Protocol Evolution Demo
//  * 
//  * Demonstrates the complete SOCKS5 protocol flow using ByteIndexedBuffer
//  * with minimal forward scans and protocol fragment transformation.
//  */
// object Socks5Demo {

//     /**
//      * Run complete SOCKS5 protocol evolution demo
//      */
//     fun runDemo() {
//         println("=== SOCKS5 Protocol Evolution Demo ===")
//         println()
        
//         // Demo 1: Handshake Evolution
//         demoHandshake()
//         println()
        
//         // Demo 2: Request Evolution  
//         demoRequest()
//         println()
        
//         // Demo 3: Complete Protocol Flow
//         demoCompleteFlow()
//         println()
        
//         // Demo 4: Performance Benchmark
//         Socks5Evolution.benchmarkSocks5Parsing(10000)
//     }

//     /**
//      * Demo SOCKS5 handshake evolution
//      */
//     private fun demoHandshake() {
//         println("1. SOCKS5 Handshake Evolution")
//         println("   Input: 0x05 0x02 0x00 0x02")
        
//         val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
//         val buffer = ByteIndexedBuffer(handshakeData.toIndexed())
        
//         val request = Socks5Evolution.parseHandshakeRequest(buffer)
//         if (request != null) {
//             println("   Parsed:")
//             println("     Version: 0x%02X".format(request.version.toInt() and 0xFF))
//             println("     Methods: ${request.methods.toDebugString()}")
//             println("     Scan Count: ${request.scanCount}")
            
//             val response = Socks5Evolution.createHandshakeResponse(Socks5Evolution.AUTH_METHOD_NO_AUTH)
//             println("   Response: ${response.toDebugString()}")
//         }
//     }

//     /**
//      * Demo SOCKS5 request evolution
//      */
//     private fun demoRequest() {
//         println("2. SOCKS5 Request Evolution")
//         println("   Input: 0x05 0x01 0x00 0x01 0x7F 0x00 0x00 0x01 0x00 0x50")
        
//         val requestData = byteArrayOf(
//             0x05, 0x01, 0x00, 0x01,  // Version, CONNECT, Reserved, IPv4
//             0x7F, 0x00, 0x00, 0x01,  // 127.0.0.1
//             0x00, 0x50               // Port 80
//         )
//         val buffer = ByteIndexedBuffer(requestData.toIndexed())
        
//         val request = Socks5Evolution.parseSocksRequest(buffer)
//         if (request != null) {
//             println("   Parsed:")
//             println("     Version: 0x%02X".format(request.version.toInt() and 0xFF))
//             println("     Command: 0x%02X (${getCommandName(request.command)})".format(request.command.toInt() and 0xFF))
//             println("     Address Type: 0x%02X (${getAddressTypeName(request.addressType)})".format(request.addressType.toInt() and 0xFF))
//             println("     Destination: ${request.destinationAddress.toIpAddress()}:${request.destinationPort}")
//             println("     Scan Count: ${request.scanCount}")
            
//             val response = Socks5Evolution.createSocksResponse(
//                 reply = Socks5Evolution.REPLY_SUCCESS,
//                 addressType = request.addressType,
//                 boundAddress = request.destinationAddress,
//                 boundPort = request.destinationPort
//             )
//             println("   Response: ${response.toDebugString()}")
//         }
//     }

//     /**
//      * Demo complete SOCKS5 protocol flow
//      */
//     private fun demoCompleteFlow() {
//         println("3. Complete SOCKS5 Protocol Flow")
        
//         val context = SocksContext()
//         var totalScans = 0
        
//         // Step 1: Handshake
//         println("   Step 1: Handshake")
//         val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
//         val handshakeBuffer = ByteIndexedBuffer(handshakeData.toIndexed())
        
//         val handshakeRequest = Socks5Evolution.parseHandshakeRequest(handshakeBuffer)
//         if (handshakeRequest != null) {
//             totalScans += handshakeRequest.scanCount
//             println("     Handshake Request: ${handshakeRequest.scanCount} scans")
            
//             val handshakeResponse = Socks5Evolution.createHandshakeResponse(Socks5Evolution.AUTH_METHOD_NO_AUTH)
//             println("     Handshake Response: ${handshakeResponse.toDebugString()}")
//         }
        
//         // Step 2: Request
//         println("   Step 2: Request")
//         val requestData = byteArrayOf(
//             0x05, 0x01, 0x00, 0x01,  // Version, CONNECT, Reserved, IPv4
//             0x7F, 0x00, 0x00, 0x01,  // 127.0.0.1
//             0x00, 0x50               // Port 80
//         )
//         val requestBuffer = ByteIndexedBuffer(requestData.toIndexed())
        
//         val socksRequest = Socks5Evolution.parseSocksRequest(requestBuffer)
//         if (socksRequest != null) {
//             totalScans += socksRequest.scanCount
//             println("     Socks Request: ${socksRequest.scanCount} scans")
            
//             val socksResponse = Socks5Evolution.createSocksResponse(
//                 reply = Socks5Evolution.REPLY_SUCCESS,
//                 addressType = socksRequest.addressType,
//                 boundAddress = socksRequest.destinationAddress,
//                 boundPort = socksRequest.destinationPort
//             )
//             println("     Socks Response: ${socksResponse.toDebugString()}")
//         }
        
//         println("   Total Scans: $totalScans")
//         println("   Average Scans per Step: ${totalScans / 2}")
//     }

//     /**
//      * Demo different address types
//      */
//     fun demoAddressTypes() {
//         println("4. SOCKS5 Address Types Demo")
        
//         // IPv4 Address
//         val ipv4Data = byteArrayOf(
//             0x05, 0x01, 0x00, 0x01,  // Version, CONNECT, Reserved, IPv4
//             0x7F, 0x00, 0x00, 0x01,  // 127.0.0.1
//             0x00, 0x50               // Port 80
//         )
//         demoAddressType("IPv4", ipv4Data)
        
//         // Domain Name
//         val domainData = byteArrayOf(
//             0x05, 0x01, 0x00, 0x03,  // Version, CONNECT, Reserved, Domain
//             0x09,                    // Domain length
//             0x65, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x2E, 0x63, 0x6F, 0x6D,  // "example.com"
//             0x00, 0x50               // Port 80
//         )
//         demoAddressType("Domain", domainData)
        
//         // IPv6 Address
//         val ipv6Data = byteArrayOf(
//             0x05, 0x01, 0x00, 0x04,  // Version, CONNECT, Reserved, IPv6
//             0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // ::1
//             0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
//             0x00, 0x50               // Port 80
//         )
//         demoAddressType("IPv6", ipv6Data)
//     }

//     private fun demoAddressType(type: String, data: ByteArray) {
//         println("   $type Address:")
//         println("     Input: ${data.joinToString(", ") { "0x%02X".format(it.toInt() and 0xFF) }}")
        
//         val buffer = ByteIndexedBuffer(data.toIndexed())
//         val request = Socks5Evolution.parseSocksRequest(buffer)
        
//         if (request != null) {
//             val address = when (request.addressType) {
//                 Socks5Evolution.ATYP_IPV4 -> request.destinationAddress.toIpAddress()
//                 Socks5Evolution.ATYP_DOMAIN -> request.destinationAddress.toDomainName()
//                 Socks5Evolution.ATYP_IPV6 -> request.destinationAddress.toIpAddress()
//                 else -> "Unknown"
//             }
//             println("     Parsed: $address:${request.destinationPort} (${request.scanCount} scans)")
//         }
//     }

//     // === UTILITY FUNCTIONS ===

//     private fun getCommandName(command: Byte): String {
//         return when (command) {
//             Socks5Evolution.CMD_CONNECT -> "CONNECT"
//             Socks5Evolution.CMD_BIND -> "BIND"
//             Socks5Evolution.CMD_UDP_ASSOCIATE -> "UDP_ASSOCIATE"
//             else -> "UNKNOWN"
//         }
//     }

//     private fun getAddressTypeName(addressType: Byte): String {
//         return when (addressType) {
//             Socks5Evolution.ATYP_IPV4 -> "IPv4"
//             Socks5Evolution.ATYP_DOMAIN -> "DOMAIN"
//             Socks5Evolution.ATYP_IPV6 -> "IPv6"
//             else -> "UNKNOWN"
//         }
//     }

//     // === PROTOCOL CONTEXT ===

//     /**
//      * SOCKS5 Protocol Context for state management
//      */
//     data class SocksContext(
//         var state: Socks5Evolution.SocksState = Socks5Evolution.SocksState.HANDSHAKE_REQUEST,
//         var selectedMethod: Byte = Socks5Evolution.AUTH_METHOD_NO_AUTH,
//         var request: Socks5Evolution.SocksRequest? = null,
//         var totalScanCount: Int = 0
//     ) {
//         fun addScans(count: Int) {
//             totalScanCount += count
//         }
//     }
// } 