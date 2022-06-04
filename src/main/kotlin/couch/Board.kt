package couch

data class Couch(
	val color: UByte,
	val position: CouchPosition,
)

data class Goal(
	val color: UByte,
	val position: CouchPosition,
)

typealias Dimension = Position

data class Board(
	val dimensions: Dimension,
	val playerPosition: Position,
	val obstacles: List<Position>,
	val couches: List<Couch>,
	val goals: List<Goal>,
) {
	companion object {
		fun fromString(input: String): Board {
			var dimensions: Dimension? = null
			var playerPosition: Position? = null
			val obstacles = mutableListOf<Position>()
			val couches = mutableListOf<Couch>()
			val goals = mutableListOf<Goal>()
			input.lineSequence().forEach { line ->
				val splitLine = line.split("\\s".toRegex())
				val keyword = splitLine.first()
				val coordinates = splitLine.drop(1).map(Position::fromString)
				when (keyword.takeWhile { it.isLetter() }) {
					"board" -> dimensions = coordinates.first()
					"start" -> playerPosition = coordinates.first()
					"o" -> obstacles += coordinates.first()
					"c" -> couches += Couch(
						color = keyword.takeLastWhile { it.isDigit() }.toUByte(),
						position = CouchPosition(coordinates.first(), coordinates.last())
					)
					"g" -> goals += Goal(
						color = keyword.takeLastWhile { it.isDigit() }.toUByte(),
						position = CouchPosition(coordinates.first(), coordinates.last())
					)
				}
			}

			return Board(
				dimensions = requireNotNull(dimensions),
				playerPosition = requireNotNull(playerPosition),
				obstacles = obstacles,
				couches = couches,
				goals = goals,
			)
		}
	}
}