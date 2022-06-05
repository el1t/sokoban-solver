package couch

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicReference

enum class Input(val delta: PositionDelta) {
	LEFT(PositionDelta(-1, 0)),
	RIGHT(PositionDelta(1, 0)),
	UP(PositionDelta(0, -1)),
	DOWN(PositionDelta(0, 1));

	override fun toString(): String = when (this) {
		Input.LEFT -> "⬅"
		Input.RIGHT -> "➡"
		Input.UP -> "⬆"
		Input.DOWN -> "⬇"
	}
}

data class Action(
	val playerPosition: Position,
	val inputs: List<Input>,
)

operator fun Action.plus(input: Input): Action = Action(
	playerPosition = playerPosition + input.delta,
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
	val visitedPositions = mutableSetOf(board.playerPosition)
	val positionsToVisit = PriorityQueue<Action> { a1, a2 ->
		val sizeComparison = a1.inputs.size.compareTo(a2.inputs.size)
		if (sizeComparison != 0) sizeComparison
		else a1.hashCode().compareTo(a2.hashCode())
	}
	val visitedCouches = mutableMapOf<Pair<Position, Input>, CouchAction>()

	positionsToVisit += Action(board.playerPosition, emptyList())

	do {
		val prevAction = positionsToVisit.remove()
		for (input in enumValues<Input>()) {
			val nextAction = prevAction + input
			when (val item = board.findItemAt(nextAction.playerPosition)) {
				Item.OBSTACLE, Item.WALL -> continue
				Item.EMPTY -> {
					if (nextAction.playerPosition in visitedPositions) {
						// shorter path exists
						continue
					}
					// queue next action
					visitedPositions += nextAction.playerPosition
					positionsToVisit += nextAction
				}
				is Couch -> {
					val positionWithDirection = nextAction.playerPosition to input
					if (positionWithDirection in visitedCouches) {
						// shorter path exists
						continue
					}
					visitedCouches[positionWithDirection] = nextAction.toCouchAction(item)
				}
			}
		}
	} while (positionsToVisit.isNotEmpty())

	return visitedCouches.values
}

fun getNewCouchPosition(
	couchTarget: Position,
	couchOther: Position,
	delta: PositionDelta,
	prevPlayerPosition: Position,
): Pair<Position, Position> = when {
	couchTarget + delta == couchOther -> couchOther to couchOther + delta
	prevPlayerPosition.x == couchOther.x -> Position(couchTarget.x, couchOther.y) to couchOther
	prevPlayerPosition.y == couchOther.y -> Position(couchOther.x, couchTarget.y) to couchOther
	else -> couchTarget + delta to couchOther
}

fun CouchAction.createNewCouch(): Couch {
	val direction = inputs.last()
	val couchPosition = couch.position
	val (couchTarget, couchOther) = when (val start = couchPosition.start) {
		playerPosition -> start to couchPosition.end
		else -> couchPosition.end to start
	}

	val (newCouchTarget, newCouchOther) = getNewCouchPosition(
		couchTarget,
		couchOther,
		direction.delta,
		playerPosition - direction.delta,
	)

	val (newStart, newEnd) = when (val start = couchPosition.start) {
		playerPosition -> newCouchTarget to newCouchOther
		else -> newCouchOther to newCouchTarget
	}

	return couch.copy(
		position = CouchPosition(newStart, newEnd),
	)
}

class CouchSolver(private val initialState: Board) {
	fun findShortestSolution(): List<Input>? {
		val visitedBoards = ConcurrentSkipListSet<BoardState>()
		val pendingBoards = ConcurrentSkipListSet<Pair<Board, List<Input>>> { a, b ->
			val sizeComparison = a.second.size.compareTo(b.second.size)
			if (sizeComparison != 0) sizeComparison
			else a.hashCode().compareTo(b.hashCode())
		}
		pendingBoards += initialState to emptyList()

		val solution = AtomicReference<List<Input>?>()

		fun compute(board: Board, inputs: List<Input>) {
			val couchActions = findPathsToCouches(board)
			for (action in couchActions) {
				val oldCouch = action.couch
				val newCouch = action.createNewCouch()
				val newlyOccupiedPosition = when {
					newCouch.position.start != oldCouch.position.start
							&& newCouch.position.start != oldCouch.position.end -> newCouch.position.start
					else -> newCouch.position.end
				}

				if (board.findItemAt(newlyOccupiedPosition) != Item.EMPTY) {
					// can't perform this action, results in invalid board
					continue
				}

				val newBoard = board.update(
					action.playerPosition,
					oldCouch to newCouch,
				)
				val newInputs = inputs + action.inputs
				if (newBoard.isSolved) {
					solution.getAndUpdate { oldSolution ->
						if ((oldSolution?.size ?: Int.MAX_VALUE) > newInputs.size) {
							newInputs
						} else {
							oldSolution
						}
					}
					continue
				}
				if (newBoard.isCouchStuck(newCouch)) {
					// couch is stuck on a wall
					continue
				}

				val newBoardState = BoardState(newBoard)
				if (newBoardState in visitedBoards) {
					continue
				}
				visitedBoards += newBoardState
				pendingBoards += newBoard to newInputs
			}
		}

		do {
			val (board, inputs) = pendingBoards.pollFirst()
			compute(board, inputs)
//			println(board.toReadableString())
		} while (pendingBoards.isNotEmpty() && solution.get() == null)

		return solution.acquire
	}
}
