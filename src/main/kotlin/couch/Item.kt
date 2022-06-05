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
