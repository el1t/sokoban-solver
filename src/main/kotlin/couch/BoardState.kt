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

	@JvmInline
	value class SerializedState private constructor(private val data: ULong) :
		Comparable<SerializedState> {
		companion object {
			private fun serializeCouches(couches: List<Couch>): ULong =
				couches.fold(0uL) { acc, couch ->
					acc shl 11 or
							(couch.color.toULong() shl 9) or
							(couch.position.start.x.toULong() shl 6) or
							(couch.position.start.y.toULong() shl 3) or
							(couch.position.orientation.ordinal.toULong())
				}
		}

		constructor(state: BoardState) : this(
			(state.satisfiedGoals.toULong() shl 61) or
					(state.playerPosition.serializedPosition.toULong() shl 44) or
					serializeCouches(state.couches)
		)

		private val playerPosition: Position
			get() = Position(((data shr 44) and 0xFFFFuL).toUShort())
		private val couches: List<Couch>
			get() {
				val ret = mutableListOf<Couch>()
				var couchData = data and 0xFFFFFFFFFFFuL
				while (couchData != 0uL) {
					val startPosition = Position(
						x = ((couchData shr 6) and 0x7uL).toUInt(),
						y = ((couchData shr 3) and 0x7uL).toUInt(),
					)
					val endPosition =
						when (CouchPosition.Orientation.values[(couchData and 0x7uL).toInt()]) {
							CouchPosition.Orientation.NORTH -> startPosition + Input.LEFT
							CouchPosition.Orientation.NORTH_EAST -> startPosition + (Input.UP + Input.LEFT)
							CouchPosition.Orientation.NORTH_WEST -> startPosition + (Input.DOWN + Input.LEFT)
							CouchPosition.Orientation.EAST -> startPosition + Input.UP
							CouchPosition.Orientation.WEST -> startPosition + Input.DOWN
							CouchPosition.Orientation.SOUTH -> startPosition + Input.RIGHT
							CouchPosition.Orientation.SOUTH_EAST -> startPosition + (Input.UP + Input.RIGHT)
							CouchPosition.Orientation.SOUTH_WEST -> startPosition + (Input.DOWN + Input.RIGHT)
						}
					ret += Couch(
						color = ((couchData shr 9) and 0x3uL).toUByte(),
						position = CouchPosition(
							start = startPosition,
							end = endPosition,
						),
					)
					couchData = couchData shr 11
				}
				return ret
			}
		private val satisfiedGoals: Int
			get() = (data shr 61).toInt()

		fun deserialize(): BoardState = BoardState(
			playerPosition = playerPosition,
			couches = couches,
			satisfiedGoals = satisfiedGoals,
		)

		override fun compareTo(other: SerializedState): Int = data.compareTo(other.data)
	}

	override fun compareTo(other: BoardState): Int =
		when {
			playerPosition != other.playerPosition -> playerPosition.compareTo(
				other.playerPosition
			)

			satisfiedGoals != other.satisfiedGoals -> satisfiedGoals.compareTo(other.satisfiedGoals)
//			couches.size != other.couches.size -> couches.size.compareTo(other.couches.size)
//			couches.containsAll(other.couches) -> 0
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

	fun findItemAt(position: Position, metadata: BoardMetadata): Item =
		metadata.findItemAt(position)

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
		Input.values.map { input -> input to findItemAt(point + input) }

	private fun areInputsBlocked(inputsToItems: List<Pair<Input, Item>>): Boolean {
		val emptyItemIndices = inputsToItems.mapIndexedNotNull { index, (_, item) ->
			if (item == Item.EMPTY) index
			else null
		}
		if (emptyItemIndices.size > inputsToItems.size / 2) {
			// guaranteed a pair of directions is empty
			return false
		}

		for (index in emptyItemIndices) {
			// opposite direction is always adjacent
			val oppositeDirection = inputsToItems[index].first.opposite
			for (i in -1..1 step 2) {
				val checkIndex = when (val test = index + i) {
					-1 -> inputsToItems.lastIndex
					inputsToItems.size -> 0
					else -> test
				}
				val potentialOpposite = inputsToItems[checkIndex]
				if (potentialOpposite.first == oppositeDirection) {
					if (potentialOpposite.second == Item.EMPTY) {
						// a pair of directions is empty
						return false
					} else if (potentialOpposite.second is Couch) {
						// couches are not handled, assume input is allowed
						return false
					}
					break
				}
			}
		}

		return true
	}

	private fun BoardMetadata.isCouchDead(couch: Couch): Boolean = when {
		couch.position.isDiagonal -> areInputsBlocked(findItemsAroundPoint(couch.position.start))
				&& areInputsBlocked(findItemsAroundPoint(couch.position.end))

		else -> areInputsBlocked(
			findItemsAroundPoint(couch.position.start).filter { it.second != couch }
					+ findItemsAroundPoint(couch.position.end).filter { it.second != couch }
				.reversed()
		)
	}

	operator fun <T> Array<Array<T>>.set(index: Position, value: T) {
		this[index.y.toInt()][index.x.toInt()] = value
	}

//	fun longHashCode(): ULong {
//		val position = playerPosition.serializedPosition.toULong()
//		val positionHash = position + 0x9e3779b9uL + (position shl 6) + (position shr 2)
//		return couches.fold(positionHash) { acc, couch ->
//			acc xor ((couch.color.toULong() shl 32) + couch.position.serializedPosition.toULong() +
//					0x9e3779b9uL + (acc shl 6) + (acc shr 2))
//		}
//	}

	fun serialize(): SerializedState = SerializedState(this)

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
