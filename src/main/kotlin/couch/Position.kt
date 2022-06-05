package couch

@JvmInline
value class Position(val serializedPosition: UShort) : Comparable<Position> {
	companion object {
		fun fromString(input: String): Position {
			val (x, y) = input.split(',').map(String::toUInt)
			return Position(x, y)
		}
	}

	constructor(x: UInt, y: UInt) : this(((x and 0xFFu shl 8) or (y and 0xFFu)).toUShort())

	val x: UInt get() = serializedPosition.toUInt() shr 8
	val y: UInt get() = (serializedPosition and 255u).toUInt()

	fun copy(x: UInt = this.x, y: UInt = this.y): Position = Position(x, y)

	operator fun contains(point: Position): Boolean =
		point.x in 0u until x && point.y in 0u until y

	operator fun plus(delta: PositionDelta): Position = Position(
		x = (x.toInt() + delta.x).toUInt(),
		y = (y.toInt() + delta.y).toUInt(),
	)

	operator fun minus(delta: PositionDelta): Position = Position(
		x = (x.toInt() - delta.x).toUInt(),
		y = (y.toInt() - delta.y).toUInt(),
	)

	override fun compareTo(other: Position): Int =
		serializedPosition.compareTo(other.serializedPosition)

	override fun toString(): String = "Position(x=$x, y=$y)"
}
