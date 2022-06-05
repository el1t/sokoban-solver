package couch

enum class Input {
	LEFT, RIGHT, UP, DOWN,
}

data class Action(
	val playerPosition: Position,
	val inputs: List<Input>,
)

fun Position.move(input: Input): Position = when (input) {
	Input.LEFT -> Position(x - 1u, y)
	Input.RIGHT -> Position(x + 1u, y)
	Input.UP -> Position(x, y - 1u)
	Input.DOWN -> Position(x, y + 1u)
}

operator fun Action.plus(input: Input): Action = Action(
	playerPosition = playerPosition.move(input),
	inputs = inputs + input,
)

fun Action.toCouchAction(couch: Couch): CouchAction = CouchAction(
	playerPosition = playerPosition,
	inputs = inputs,
	couch = couch,
)

data class CouchAction(
	val playerPosition: Position,
	val inputs: List<Input>,
	val couch: Couch,
)

fun findPathsToCouches(board: Board): Collection<CouchAction> {
	val visitedPositions =
		mutableMapOf<Position, List<Input>>(board.playerPosition to emptyList())
	val positionsToVisit = mutableListOf(
		Action(board.playerPosition, emptyList()),
	)
	val visitedCouches = mutableMapOf<Position, CouchAction>()

	do {
		val prevAction = positionsToVisit.removeFirst()
		for (input in enumValues<Input>()) {
			val nextAction = prevAction + input
			when (val item = board.findItemAt(nextAction.playerPosition)) {
				Item.OBSTACLE, Item.WALL -> continue
				Item.EMPTY -> {
					if (
						(visitedPositions[nextAction.playerPosition]?.size ?: Int.MAX_VALUE)
						< nextAction.inputs.size
					) {
						// shorter path exists
						continue
					}
					// queue next action
					positionsToVisit += nextAction
				}
				is Couch -> {
					if (
						(visitedCouches[nextAction.playerPosition]?.inputs?.size ?: Int.MAX_VALUE)
						< nextAction.inputs.size
					) {
						// shorter path exists
						continue
					}
					visitedCouches[nextAction.playerPosition] = nextAction.toCouchAction(item)
				}
			}
		}
	} while (positionsToVisit.isNotEmpty())

	return visitedCouches.values
}

class Solver(private val initialState: Board) {
}
