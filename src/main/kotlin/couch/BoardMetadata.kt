package couch

data class BoardMetadata private constructor(
	val dimensions: Dimension,
	val obstacles: Set<Position>,
	val goals: GoalList,
) {
	constructor(settings: BoardSettings) : this(
		dimensions = settings.dimensions,
		obstacles = settings.obstacles,
		goals = settings.goals,
	)

	private val safeCouchPositions: Map<Pair<CouchColor, CouchPosition.Orientation>, Set<Position>> =
		mutableMapOf<Pair<CouchColor, CouchPosition.Orientation>, MutableSet<Position>>().apply {
			val wallGoals = goals.filter { isFlatAgainstWall(it.position) }
			wallGoals.forEach { goal ->
				val safePositions =
					getOrPut(goal.color to goal.position.orientation) { mutableSetOf() }
				if (goal.position.isHorizontal) {
					val startX = dimensions.x - goal.position.start.x
					for (i in 0u until startX) {
						val nextPosition = goal.position.start.copy(x = goal.position.start.x + i)
						if (nextPosition in obstacles) break
						safePositions += nextPosition
					}
					for (i in 1u..goal.position.start.x) {
						val nextPosition = goal.position.start.copy(x = goal.position.start.x - i)
						if (nextPosition in obstacles) break
						safePositions += nextPosition
					}
				} else if (goal.position.isVertical) {
					val startY = dimensions.y - goal.position.start.y
					for (i in 0u until startY) {
						val nextPosition = goal.position.start.copy(y = goal.position.start.y + i)
						if (nextPosition in obstacles) break
						safePositions += nextPosition
					}
					for (i in 1u..goal.position.start.y) {
						val nextPosition = goal.position.start.copy(y = goal.position.start.y - i)
						if (nextPosition in obstacles) break
						safePositions += nextPosition
					}
				}
			}
		}

	fun isFlatAgainstWall(couchPosition: CouchPosition): Boolean = when {
		couchPosition.isHorizontal -> {
			val position = couchPosition.start
			position.y + 1u == dimensions.y || position.y == 0u
		}

		couchPosition.isVertical -> {
			val position = couchPosition.start
			position.x + 1u == dimensions.x || position.x == 0u
		}

		else -> false
	}

	fun isCouchOnCorrectWall(couch: Couch): Boolean =
		safeCouchPositions[couch.color to couch.position.orientation]?.contains(
			couch.position.start
		) == true
}
