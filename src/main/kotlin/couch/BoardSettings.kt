package couch

typealias Dimension = Position

data class BoardSettings private constructor(
	val dimensions: Dimension,
	val initialPlayerPosition: Position,
	val obstacles: Set<Position>,
	val initialCouches: List<Couch>,
	val goals: GoalList,
) {
	companion object {
		fun fromString(input: String): BoardSettings {
			var dimensions: Dimension? = null
			var initialPlayerPosition: Position? = null
			val obstacles = mutableSetOf<Position>()
			val initialCouches = mutableListOf<Couch>()
			val goals = mutableListOf<Goal>()
			input.lineSequence().forEach { line ->
				val splitLine = line.split("\\s".toRegex())
				val keyword = splitLine.first()
				val coordinates = splitLine.drop(1).map(Position::fromString)
				when (keyword.takeWhile { it.isLetter() }) {
					"board" -> dimensions = coordinates.first()
					"start" -> initialPlayerPosition = coordinates.first()
					"o" -> obstacles += coordinates.first()
					"c" -> initialCouches += Couch(
						color = keyword.takeLastWhile { it.isDigit() }.toUByte(),
						position = CouchPosition(coordinates.first(), coordinates.last())
					)
					"g" -> goals += Goal(
						color = keyword.takeLastWhile { it.isDigit() }.toUByte(),
						position = CouchPosition(coordinates.first(), coordinates.last())
					)
				}
			}

			return BoardSettings(
				dimensions = requireNotNull(dimensions),
				initialPlayerPosition = requireNotNull(initialPlayerPosition),
				obstacles = obstacles,
				initialCouches = initialCouches,
				goals = goals,
			)
		}
	}
}

fun BoardSettings.toInitialBoardState(): BoardState = BoardState(
	playerPosition = initialPlayerPosition,
	couches = initialCouches,
	satisfiedGoals =  initialCouches.count { BoardState.validateCouch(it, goals) },
)
