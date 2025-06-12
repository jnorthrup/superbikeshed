package org.trikeshed.download.aria2c

import org.trikeshed.core.Series
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// Value classes for fundamental aria2c types
@JvmInline
value class Aria2cGid(val value: String)

@JvmInline
value class Aria2cUri(val value: String)

@JvmInline
value class Aria2cOptionName(val value: String)

@JvmInline
value class Aria2cOptionValue(val value: String)

@JvmInline
value class Aria2cMethodName(val value: String)

@JvmInline
value class Aria2cNotificationType(val value: String)

@JvmInline
value class Aria2cKey(val value: String)

@JvmInline
value class Aria2cPosition(val value: Int)

@JvmInline
value class Aria2cFileIndex(val value: Int)

@JvmInline
value class Aria2cOffset(val value: Int)

@JvmInline
value class Aria2cNum(val value: Int)

@JvmInline
value class Aria2cTotalLength(val value: Long)

@JvmInline
value class Aria2cCompletedLength(val value: Long)

@JvmInline
value class Aria2cUploadLength(val value: Long)

@JvmInline
value class Aria2cBitfield(val value: String)

@JvmInline
value class Aria2cDownloadSpeed(val value: Long)

@JvmInline
value class Aria2cUploadSpeed(val value: Long)

@JvmInline
value class Aria2cInfoHash(val value: String)

@JvmInline
value class Aria2cNumPieces(val value: Int)

@JvmInline
value class Aria2cPieceLength(val value: Int)

@JvmInline
value class Aria2cConnections(val value: Int)

@JvmInline
value class Aria2cErrorCode(val value: Int)

@JvmInline
value class Aria2cErrorMessage(val value: String)

@JvmInline
value class Aria2cDownloadDir(val value: String)

@JvmInline
value class Aria2cVerifiedLength(val value: Long)

// Type aliases for specific values
typealias Aria2cStatus = String
typealias Aria2cDownloadStatus = Pair<Aria2cGid, Pair<Aria2cStatus, Pair<Aria2cTotalLength, Pair<Aria2cCompletedLength, Pair<Aria2cUploadLength, Pair<Aria2cBitfield, Pair<Aria2cDownloadSpeed, Pair<Aria2cUploadSpeed, Pair<Aria2cInfoHash, Pair<Aria2cNumPieces, Pair<Aria2cPieceLength, Pair<Aria2cConnections, Pair<Aria2cErrorCode, Pair<Aria2cErrorMessage, Pair<Aria2cDownloadDir, Pair<Aria2cVerifiedLength, Boolean>>>>>>>>>>>>>>>

// Type aliases for collections
typealias Aria2cUriSeries = Series<Aria2cUri>
typealias Aria2cOptionSeries = Series<Pair<Aria2cOptionName, Aria2cOptionValue>>
typealias Aria2cKeySeries = Series<Aria2cKey>
typealias Aria2cGidSeries = Series<Aria2cGid>
typealias Aria2cPositionSeries = Series<Aria2cPosition>

// Type aliases for RPC structures
typealias Aria2cNotificationHandler = (Aria2cNotificationType, List<Any>) -> Unit

// Type aliases for RPC methods
typealias Aria2cMaxConnectionsPerServer = Int
typealias Aria2cSplit = Int
typealias Aria2cMinSplitSize = Int
typealias Aria2cMaxConcurrentDownloads = Int
typealias Aria2cMaxDownloadLimit = Int
typealias Aria2cMaxUploadLimit = Int
typealias Aria2cMaxOverallDownloadLimit = Int
typealias Aria2cMaxOverallUploadLimit = Int
typealias Aria2cMaxDownloadResult = Int
typealias Aria2cMaxDownloadResultLimit = Int
typealias Aria2cMaxConnectionPerServer = Int
typealias Aria2cMaxConnectionLimit = Int
typealias Aria2cMaxTries = Int
typealias Aria2cRetryWait = Int
typealias Aria2cConnectTimeout = Int
typealias Aria2cRequestTimeout = Int
typealias Aria2cTimeout = Int
typealias Aria2cAutoFileRenaming = Boolean
typealias Aria2cAllowOverwrite = Boolean
typealias Aria2dAllowPieceLengthChange = Boolean
typealias Aria2cAlwaysResume = Boolean
typealias Aria2cAsyncDns = Boolean
typealias Aria2cAutoSaveInterval = Int
typealias Aria2cBtDetachSeedOnly = Boolean
typealias Aria2cBtEnableHookAfterHashCheck = Boolean
typealias Aria2cBtEnableLpd = Boolean
typealias Aria2cBtExcludeTracker = String
typealias Aria2cBtExternalIp = String
typealias Aria2cBtForceEncryption = Boolean
typealias Aria2cBtHashCheckSeed = Boolean
typealias Aria2cBtLoadSavedMetadata = Boolean
typealias Aria2cBtMaxOpenFiles = Int
typealias Aria2cBtMaxPeers = Int
typealias Aria2cBtMetadataOnly = Boolean
typealias Aria2cBtMinCryptoLevel = String
typealias Aria2cBtPrioritizePiece = String
typealias Aria2cBtRemoveUnselectedFile = Boolean
typealias Aria2cBtRequestPeerSpeedLimit = Int
typealias Aria2cBtRequireCrypto = Boolean
typealias Aria2cBtSaveMetadata = Boolean
typealias Aria2cBtSeedUnverified = Boolean
typealias Aria2cBtStopTimeout = Int
typealias Aria2cBtTracker = String
typealias Aria2cBtTrackerConnectTimeout = Int
typealias Aria2cBtTrackerInterval = Int
typealias Aria2cBtTrackerTimeout = Int
typealias Aria2cCheckIntegrity = Boolean
typealias Aria2cChecksum = String
typealias Aria2cConditionalGet = Boolean
typealias Aria2cConfPath = String
typealias Aria2cConnectTimeout = Int
typealias Aria2cConsoleLogLevel = String
typealias Aria2cContentDispositionDefaultUtf8 = Boolean
typealias Aria2cContinue = Boolean
typealias Aria2cDaemon = Boolean
typealias Aria2cDeferredInput = Boolean
typealias Aria2cDhtEntryPoint = String
typealias Aria2cDhtEntryPoint6 = String
typealias Aria2cDhtFilePath = String
typealias Aria2cDhtFilePath6 = String
typealias Aria2cDhtListenPort = Int
typealias Aria2cDhtMessageTimeout = Int
typealias Aria2cDir = String
typealias Aria2cDisableIpv6 = Boolean
typealias Aria2cDiskCache = Int
typealias Aria2cDownloadResult = String
typealias Aria2cDryRun = Boolean
typealias Aria2cDscp = Int
typealias Aria2cRlimitNofile = Int
typealias Aria2cEnableColor = Boolean
typealias Aria2cEnableDht = Boolean
typealias Aria2cEnableDht6 = Boolean
typealias Aria2cEnableHttpKeepAlive = Boolean
typealias Aria2cEnableHttpPipelining = Boolean
typealias Aria2cEnableMmap = Boolean
typealias Aria2cEnablePeerExchange = Boolean
typealias Aria2cEnableRpc = Boolean
typealias Aria2cEventPoll = String
typealias Aria2cFileAllocation = String
typealias Aria2cFollowMetalink = Boolean
typealias Aria2cFollowTorrent = Boolean
typealias Aria2cForceSave = Boolean
typealias Aria2cFtpPasswd = String
typealias Aria2cFtpPasv = Boolean
typealias Aria2cFtpProxy = String
typealias Aria2cFtpProxyPasswd = String
typealias Aria2cFtpProxyUser = String
typealias Aria2cFtpReuseConnection = Boolean
typealias Aria2cFtpType = String
typealias Aria2cFtpUser = String
typealias Aria2cGid = String
typealias Aria2cHashCheckOnly = Boolean
typealias Aria2cHeader = String
typealias Aria2cHttpAcceptGzip = Boolean
typealias Aria2cHttpAuthChallenge = Boolean
typealias Aria2cHttpNoCache = Boolean
typealias Aria2cHttpPasswd = String
typealias Aria2cHttpProxy = String
typealias Aria2cHttpProxyPasswd = String
typealias Aria2cHttpProxyUser = String
typealias Aria2cHttpUser = String
typealias Aria2cHttpsProxy = String
typealias Aria2cHttpsProxyPasswd = String
typealias Aria2cHttpsProxyUser = String
typealias Aria2cIndexOut = String
typealias Aria2cInputFile = String
typealias Aria2cInterface = String
typealias Aria2cKeepUnfinishedDownloadResult = Boolean
typealias Aria2cListenPort = String
typealias Aria2cLoadCookies = String
typealias Aria2cLog = String
typealias Aria2cLogLevel = String
typealias Aria2cLowestSpeedLimit = Int
typealias Aria2cMaxConnectionPerServer = Int
typealias Aria2cMaxDownloadLimit = Int
typealias Aria2cMaxDownloadResult = Int
typealias Aria2cMaxFileNotFound = Int
typealias Aria2cMaxMmapLimit = Int
typealias Aria2cMaxOverallDownloadLimit = Int
typealias Aria2cMaxOverallUploadLimit = Int
typealias Aria2cMaxResumeFailureTries = Int
typealias Aria2cMaxTries = Int
typealias Aria2cMaxUploadLimit = Int
typealias Aria2cMetalinkBaseUri = String
typealias Aria2cMetalinkLanguage = String
typealias Aria2cMetalinkLocation = String
typealias Aria2cMetalinkOs = String
typealias Aria2cMetalinkPreferredProtocol = String
typealias Aria2cMetalinkVersion = String
typealias Aria2cMinSplitSize = Int
typealias Aria2cNoConf = Boolean
typealias Aria2cNoFileAllocationLimit = Int
typealias Aria2cNoNetrc = Boolean
typealias Aria2cNoProxy = String
typealias Aria2cOut = String
typealias Aria2cParameterizedUri = Boolean
typealias Aria2cPause = Boolean
typealias Aria2cPauseMetadata = Boolean
typealias Aria2cPieceLength = Int
typealias Aria2cProxyMethod = String
typealias Aria2cRealtimeChunkChecksum = Boolean
typealias Aria2cReferer = String
typealias Aria2cRemoteTime = Boolean
typealias Aria2cRemoveControlFile = Boolean
typealias Aria2cRetryWait = Int
typealias Aria2cReuseUri = Boolean
typealias Aria2cRpcAllowOriginAll = Boolean
typealias Aria2cRpcCertificate = String
typealias Aria2cRpcListenAll = Boolean
typealias Aria2cRpcListenPort = Int
typealias Aria2cRpcMaxRequestSize = Int
typealias Aria2cRpcPasswd = String
typealias Aria2cRpcPrivateKey = String
typealias Aria2cRpcSaveUploadMetadata = Boolean
typealias Aria2cRpcSecret = String
typealias Aria2cRpcUser = String
typealias Aria2cSaveNotFoun = Boolean
typealias Aria2cSaveSession = String
typealias Aria2cSaveSessionInterval = Int
typealias Aria2cSeedRatio = Double
typealias Aria2cSeedTime = Int
typealias Aria2cSelectFile = String
typealias Aria2cSplit = Int
typealias Aria2cSshHostKeyMd = String
typealias Aria2cStreamPieceSelector = String
typealias Aria2cSummaryInterval = Int
typealias Aria2cTimeout = Int
typealias Aria2cTorrentFile = String
typealias Aria2cTruncateConsoleReadout = Boolean
typealias Aria2cUriSelector = String
typealias Aria2cUseHead = Boolean
typealias Aria2cUserAgent = String 