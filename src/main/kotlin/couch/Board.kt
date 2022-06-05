package couch

sealed class Item {
	object OBSTACLE : Item()
	object EMPTY : Item()
	object WALL : Item()
}

data class Couch(
	val color: UByte,
	val position: CouchPosition,
) : Item()

data class Goal(
	val color: UByte,
	val position: CouchPosition,
)

typealias Dimension = Position

data class Board(
	val dimensions: Dimension,
	val playerPosition: Position,
	val obstacles: Set<Position>,
	val couches: List<Couch>,
	val goals: List<Goal>,
) {
	companion object {
		fun fromString(input: String): Board {
			var dimensions: Dimension? = null
			var playerPosition: Position? = null
			val obstacles = mutableSetOf<Position>()
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

	operator fun contains(point: Position): Boolean =
		point.x in 0u until dimensions.x && point.y in 0u until dimensions.y

	fun findItemAt(position: Position): Item =
		when (position) {
			!in this -> Item.WALL
			in obstacles -> Item.OBSTACLE
			else -> couches.find { it.position.start == position || it.position.end == position }
				?: Item.EMPTY
		}
}