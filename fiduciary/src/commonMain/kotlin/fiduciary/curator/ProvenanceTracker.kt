package fiduciary.curator

import fiduciary.agents.CurationAgent
import fiduciary.agents.CuratedContent
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.datetime.Clock

class ProvenanceTracker {
    suspend fun trackProvenance(content: CuratedContent, agentId: NUID): CurationAgent.ProvenanceChain {
        return CurationAgent.ProvenanceChain(
            contentId = content.id,
            origin = content.source,
            transformations = listOf(
                CurationAgent.Transformation(
                    type = "curation",
                    description = "Content curated by agent",
                    agentId = agentId,
                    timestamp = Clock.System.now()
                )
            ),
            contributors = listOf(agentId),
            timestamp = Clock.System.now()
        )
    }
}