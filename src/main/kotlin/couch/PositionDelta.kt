package couch

data class PositionDelta(val x: Int, val y: Int) {
	operator fun plus(delta: PositionDelta): PositionDelta = PositionDelta(
		x = x + delta.x,
		y = y + delta.y,
	)

	operator fun minus(delta: PositionDelta): PositionDelta = PositionDelta(
		x = x - delta.x,
		y = y - delta.y,
	)
}