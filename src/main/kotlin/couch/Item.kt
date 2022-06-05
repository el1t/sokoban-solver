package couch

sealed class Item {
	object OBSTACLE : Item()
	object EMPTY : Item()
	object WALL : Item()
}

data class Couch(
	val color: CouchColor,
	val position: CouchPosition,
) : Item(), Comparable<Couch> {
	override fun compareTo(other: Couch): Int =
		when (val comparison = color.compareTo(other.color)) {
			0 -> position.compareTo(other.position)
			else -> comparison
		}
}
