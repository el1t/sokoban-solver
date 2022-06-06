package couch

@JvmInline
value class CouchPosition(val serializedPosition: UInt) : Comparable<CouchPosition> {
	enum class Orientation {
		NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST,
	}

	/**
	 * @param start The left seat when the couch is facing south
	 * @param end the right seat when the couch is facing south
	 */
	constructor(
		start: Position,
		end: Position,
	) : this(((start.serializedPosition.toUInt() shl 16) or (end.serializedPosition.toUInt())))

	val start: Position get() = Position((serializedPosition shr 16).toUShort())
	val end: Position get() = Position((serializedPosition and 0xFFFFu).toUShort())

	// start.y == end.y
	val isHorizontal: Boolean
		get() = (((serializedPosition shr 16) xor serializedPosition)) and 0xFFu == 0u
	// start.x == end.x
	val isVertical: Boolean
		get() = (((serializedPosition shr 16) xor serializedPosition)) shr 8 == 0u
	val isDiagonal: Boolean
		get() = !isHorizontal && !isVertical

	val orientation: Orientation
		get() {
			val s = start
			val e = end
			val xDiff = s.x.compareTo(e.x)
			val yDiff = s.y.compareTo(e.y);
			return when {
				xDiff < 0 -> when {
					yDiff < 0 -> Orientation.SOUTH_WEST
					yDiff == 0 -> Orientation.SOUTH
					else -> Orientation.SOUTH_EAST
				}
				xDiff == 0 -> when {
					yDiff < 0 -> Orientation.WEST
					yDiff > 0 -> Orientation.EAST
					else -> throw IllegalStateException("$s and $e are identical")
				}
				else -> when {
					yDiff < 0 -> Orientation.NORTH_WEST
					yDiff == 0 -> Orientation.NORTH
					else -> Orientation.NORTH_EAST
				}
			}
		}

	override fun toString(): String = "CouchPosition(" +
			"start=[${start.x}, ${start.y}], " +
			"end=[${end.x}, ${end.y}])"

	override fun compareTo(other: CouchPosition): Int =
		serializedPosition.compareTo(other.serializedPosition)
}