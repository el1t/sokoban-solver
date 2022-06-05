package couch

typealias GoalList = List<Goal>
typealias CouchColor = UByte

data class Goal(
	val color: CouchColor,
	val position: CouchPosition,
)
