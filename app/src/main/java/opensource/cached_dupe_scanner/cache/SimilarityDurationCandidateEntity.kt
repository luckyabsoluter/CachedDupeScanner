package opensource.cached_dupe_scanner.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "similarity_duration_candidates",
    primaryKeys = ["experimentId", "normalizedPath"],
    indices = [
        Index(
            value = ["experimentId", "durationMillis", "normalizedPath"],
            name = "index_similarity_duration_candidates_experimentId_durationMillis_normalizedPath"
        )
    ]
)
data class SimilarityDurationCandidateEntity(
    val experimentId: String,
    val normalizedPath: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val updatedAtMillis: Long
)
