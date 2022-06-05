package couch

data class BoardState(
	val playerPosition: Position,
	val couches: List<Couch>,
) : Comparable<BoardState> {
	constructor(board: Board) : this(
		playerPosition = board.playerPosition,
		couches = board.couches,
	)

	override fun compareTo(other: BoardState): Int =
		when {
			playerPosition != other.playerPosition -> playerPosition.serializedPosition.compareTo(
				other.playerPosition.serializedPosition
			)
			couches.size != other.couches.size -> couches.size.compareTo(other.couches.size)
			// look away
			else -> couches.hashCode().compareTo(other.couches.hashCode())
		}
}
