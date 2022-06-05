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
	private val satisfiedGoals: Int = couches.count { validateCouch(it, goals) },
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

		private fun validateCouch(couch: Couch, goals: List<Goal>): Boolean = goals.find { goal ->
			goal.color == couch.color && goal.position == couch.position
		} != null
	}

	val isSolved get() = satisfiedGoals == goals.size

	operator fun contains(point: Position): Boolean =
		point.x in 0u until dimensions.x && point.y in 0u until dimensions.y

	fun findItemAt(position: Position): Item =
		when (position) {
			!in this -> Item.WALL
			in obstacles -> Item.OBSTACLE
			else -> couches.find { it.position.start == position || it.position.end == position }
				?: Item.EMPTY
		}

	fun isCouchStuck(couch: Couch): Boolean {
		return false
	}

	fun update(playerPosition: Position, oldToNewCouch: Pair<Couch, Couch>): Board {
		val (oldCouch, newCouch) = oldToNewCouch
		val oldDelta = if (validateCouch(oldCouch, goals)) -1 else 0
		val newDelta = if (validateCouch(newCouch, goals)) 1 else 0

		return copy(
			playerPosition = playerPosition,
			couches = couches.map { if (it == oldCouch) newCouch else it },
			satisfiedGoals = satisfiedGoals + oldDelta + newDelta,
		)
	}

	fun withBoardState(state: BoardState): Board = copy(
		playerPosition = playerPosition,
		couches = couches,
	)

	operator fun <T> Array<Array<T>>.set(index: Position, value: T) {
		this[index.y.toInt()][index.x.toInt()] = value
	}

	fun toReadableString(): String {
		val board =
			Array<Array<String>>(dimensions.y.toInt()) { Array<String>(dimensions.x.toInt()) { " " } }
		board[playerPosition] = "p"
		obstacles.forEach {
			board[it] = "o"
		}
		couches.forEach {
			board[it.position.start] = it.color.toString()
			board[it.position.end] = it.color.toString()
		}
		goals.forEach {
			board[it.position.start] = it.color.toString()
			board[it.position.end] = it.color.toString()
		}
		return "—".repeat(dimensions.x.toInt() * 2 + 1) + "\n" +
				board.joinToString("\n") { it.joinToString(" ", "|", "|") } + "\n" +
				"—".repeat(dimensions.x.toInt() * 2 + 1)
	}
}