import kotlinx.serialization.Serializable

@Serializable
data class GameData(val id: Int, val state: String)