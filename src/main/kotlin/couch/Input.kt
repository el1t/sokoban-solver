package couch

enum class Input(internal val delta: PositionDelta) {
	// must be declared paired (left-right, up-down)
	LEFT(PositionDelta(-1, 0)),
	RIGHT(PositionDelta(1, 0)),
	UP(PositionDelta(0, -1)),
	DOWN(PositionDelta(0, 1));

	companion object {
		val values = enumValues<Input>().toList()
	}

	val opposite: Input
		get() = when (this) {
			Input.LEFT -> Input.RIGHT
			Input.RIGHT -> Input.LEFT
			Input.UP -> Input.DOWN
			Input.DOWN -> Input.UP
		}

	override fun toString(): String = when (this) {
		Input.LEFT -> "⬅"
		Input.RIGHT -> "➡"
		Input.UP -> "⬆"
		Input.DOWN -> "⬇"
	}

	operator fun plus(input: Input): PositionDelta = PositionDelta(
		delta.x + input.delta.x,
		delta.y + input.delta.y,
	)
}

operator fun Position.plus(input: Input): Position = this + input.delta
operator fun Position.minus(input: Input): Position = this - input.delta
