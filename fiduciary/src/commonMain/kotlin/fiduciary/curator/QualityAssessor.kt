package fiduciary.curator

import fiduciary.agents.CurationAgent
import fiduciary.agents.CuratedContent
import kotlinx.datetime.Clock

class QualityAssessor {
    suspend fun assessQuality(content: CuratedContent, ring: Int): CurationAgent.QualityScore {
        // Ring-based quality assessment
        val baseFactor = when (ring) {
            0 -> 0.9
            1 -> 0.8
            2 -> 0.7
            else -> 0.6
        }
        
        // Assess different quality dimensions
        val accuracyScore = assessAccuracy(content) * baseFactor
        val completenessScore = assessCompleteness(content) * baseFactor
        val relevanceScore = assessRelevance(content) * baseFactor
        val timelinessScore = assessTimeliness(content) * baseFactor
        
        val overallScore = (accuracyScore + completenessScore + relevanceScore + timelinessScore) / 4.0
        
        return CurationAgent.QualityScore(
            overallScore = overallScore,
            accuracyScore = accuracyScore,
            completenessScore = completenessScore,
            relevanceScore = relevanceScore,
            timelinessScore = timelinessScore,
            assessmentDate = Clock.System.now()
        )
    }
    
    private fun assessAccuracy(content: CuratedContent): Double {
        // Check for peer review
        val peerReviewed = content.metadata["peer_reviewed"] == "true"
        // Check for author credentials
        val hasAuthor = content.metadata.containsKey("author")
        val hasInstitution = content.metadata.containsKey("institution")
        
        return when {
            peerReviewed && hasAuthor && hasInstitution -> 0.95
            hasAuthor && hasInstitution -> 0.85
            hasAuthor -> 0.75
            else -> 0.65
        }
    }
    
    private fun assessCompleteness(content: CuratedContent): Double {
        val requiredFields = listOf("author", "publication_date", "source")
        val presentFields = requiredFields.count { content.metadata.containsKey(it) }
        return (presentFields.toDouble() / requiredFields.size) * 0.9 + 0.1
    }
    
    private fun assessRelevance(content: CuratedContent): Double {
        // Simple relevance based on content length and metadata
        return when {
            content.data.length > 100 && content.metadata.size > 3 -> 0.9
            content.data.length > 50 && content.metadata.size > 2 -> 0.8
            content.data.length > 10 -> 0.7
            else -> 0.5
        }
    }
    
    private fun assessTimeliness(content: CuratedContent): Double {
        // Check publication date if available
        val publicationDate = content.metadata["publication_date"]
        return when {
            publicationDate?.contains("2024") == true -> 0.95
            publicationDate?.contains("2023") == true -> 0.85
            publicationDate?.contains("2022") == true -> 0.75
            else -> 0.65
        }
    }
}