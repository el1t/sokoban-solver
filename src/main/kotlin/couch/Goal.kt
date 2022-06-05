package couch

typealias GoalList = List<Goal>

data class Goal(
	val color: UByte,
	val position: CouchPosition,
)
