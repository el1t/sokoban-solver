package couch

@JvmInline
value class Position(val serializedPosition: UShort) {
	companion object {
		fun fromString(input: String): Position {
			val (x, y) = input.split(',').map(String::toUInt)
			return Position(x, y)
		}
	}

	constructor(x: UInt, y: UInt) : this(((x and 0xFFu shl 8) or (y and 0xFFu)).toUShort())

	val x: UInt get() = serializedPosition.toUInt() shr 8
	val y: UInt get() = (serializedPosition and 255u).toUInt()
}