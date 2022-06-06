package couch

data class BoardState(
	val playerPosition: Position,
	val couches: List<Couch>,
	private val satisfiedGoals: Int,
) : Comparable<BoardState> {
	companion object {
		fun validateCouch(couch: Couch, goals: GoalList): Boolean = goals.any { goal ->
			goal.color == couch.color && goal.position == couch.position
		}
	}

	override fun compareTo(other: BoardState): Int =
		when {
			playerPosition != other.playerPosition -> playerPosition.compareTo(
				other.playerPosition
			)
			satisfiedGoals != other.satisfiedGoals -> satisfiedGoals.compareTo(other.satisfiedGoals)
			couches.size != other.couches.size -> couches.size.compareTo(other.couches.size)
			couches.containsAll(other.couches) -> 0
			else -> 1
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

	fun findItemAt(position: Position, metadata: BoardMetadata): Item = metadata.findItemAt(position)

	private fun BoardMetadata.findItemAt(position: Position): Item =
		when (position) {
			!in dimensions -> Item.WALL
			in obstacles -> Item.OBSTACLE
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

		if (metadata.isFlatAgainstWall(couch.position)) {
			return !metadata.isCouchOnCorrectWall(couch)
		}

		return metadata.isCouchDead(couch)
	}

	private fun BoardMetadata.findItemsAroundPoint(point: Position) =
		Input.values.associateWith { input -> findItemAt(point + input) }

	private fun BoardMetadata.isPositionDead(position: Position): Boolean {
		val items = findItemsAroundPoint(position)
		val emptyItems = items.filter { it.value == Item.EMPTY }
		if (emptyItems.size > 2) {
			// guaranteed a pair of directions is empty
			return false
		}
		if (emptyItems.any { items[it.key.opposite] == Item.EMPTY || items[it.key.opposite] is Couch }) {
			// a pair of directions is empty
			return false
		}
		return true
	}

	private fun BoardMetadata.isCouchDead(couch: Couch): Boolean {
		return isPositionDead(couch.position.start) && isPositionDead(couch.position.end)
	}

	operator fun <T> Array<Array<T>>.set(index: Position, value: T) {
		this[index.y.toInt()][index.x.toInt()] = value
	}

	fun longHashCode(): ULong {
		val position = playerPosition.serializedPosition.toULong()
		val positionHash = position + 0x9e3779b9uL + (position shl 6) + (position shr 2)
		return couches.fold(positionHash) { acc, couch ->
			acc xor ((couch.color.toULong() shl 32) + couch.position.serializedPosition.toULong() +
					0x9e3779b9uL + (acc shl 6) + (acc shr 2))
		}
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
