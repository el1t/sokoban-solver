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
}
