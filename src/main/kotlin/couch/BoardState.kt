package couch

data class BoardState(
	val playerPosition: Position,
	val couches: List<Couch>,
	private val satisfiedGoals: Int,
) : Comparable<BoardState> {
	companion object {
		fun validateCouch(couch: Couch, goals: GoalList): Boolean = goals.find { goal ->
			goal.color == couch.color && goal.position == couch.position
		} != null
	}

	override fun compareTo(other: BoardState): Int =
		when {
			playerPosition != other.playerPosition -> playerPosition.compareTo(
				other.playerPosition
			)
			satisfiedGoals != other.satisfiedGoals -> satisfiedGoals.compareTo(other.satisfiedGoals)
			couches.size != other.couches.size -> couches.size.compareTo(other.couches.size)
			couches.containsAll(other.couches) -> 0
			else -> -1
//			else -> {
//				var ret = 0
//				couches.indices.forEach { i ->
//					ret = couches[i].compareTo(other.couches[i])
//					if (ret != 0) return@forEach
//				}
//				ret
//			}
			// look away
//			else -> couches.hashCode().compareTo(other.couches.hashCode())
		}

	fun findItemAt(position: Position, metadata: BoardMetadata): Item =
		when (position) {
			!in metadata.dimensions -> Item.WALL
			in metadata.obstacles -> Item.OBSTACLE
			else -> couches.find { it.position.start == position || it.position.end == position }
				?: Item.EMPTY
		}

	fun update(
		playerPosition: Position,
		oldToNewCouch: Pair<Couch, Couch>,
		goals: GoalList,
	): BoardState {
		val (oldCouch, newCouch) = oldToNewCouch
		val oldDelta = if (validateCouch(oldCouch, goals)) -1 else 0
		val newDelta = if (validateCouch(newCouch, goals)) 1 else 0

		return copy(
			playerPosition = playerPosition,
			couches = couches.map { if (it == oldCouch) newCouch else it },
			satisfiedGoals = satisfiedGoals + oldDelta + newDelta,
		)
	}

	fun isSolved(goals: GoalList) = satisfiedGoals == goals.size

	fun isCouchStuck(couch: Couch, metadata: BoardMetadata): Boolean {
		if (validateCouch(couch, metadata.goals)) {
			return false
		}

		// check if couch is stuck on wall
		if (metadata.isFlatAgainstWall(couch.position)) {
			return !metadata.isCouchOnCorrectWall(couch)
		}

		return false
	}

	operator fun <T> Array<Array<T>>.set(index: Position, value: T) {
		this[index.y.toInt()][index.x.toInt()] = value
	}

	fun toReadableString(metadata: BoardMetadata): String {
		val dimensions = metadata.dimensions
		val board =
			Array<Array<String>>(dimensions.y.toInt()) { Array<String>(dimensions.x.toInt()) { " " } }
		board[playerPosition] = "p"
		metadata.obstacles.forEach {
			board[it] = "o"
		}
		couches.forEach {
			board[it.position.start] = it.color.toString()
			board[it.position.end] = it.color.toString()
		}
		metadata.goals.forEach {
			board[it.position.start] = it.color.toString()
			board[it.position.end] = it.color.toString()
		}
		return "—".repeat(dimensions.x.toInt() * 2 + 1) + "\n" +
				board.joinToString("\n") { it.joinToString(" ", "|", "|") } + "\n" +
				"—".repeat(dimensions.x.toInt() * 2 + 1)
	}
}
