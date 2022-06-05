package couch

import java.util.PriorityQueue
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

const val NUM_THREADS = 8

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

fun findPathsToCouches(board: BoardState, metadata: BoardMetadata): Collection<CouchAction> {
	val visitedPositions = mutableSetOf(board.playerPosition)
	val positionsToVisit = PriorityQueue<Action> { a1, a2 ->
		val sizeComparison = a1.inputs.size.compareTo(a2.inputs.size)
		when {
			sizeComparison != 0 -> sizeComparison
			a1.playerPosition != a2.playerPosition -> a1.playerPosition.serializedPosition
				.compareTo(a2.playerPosition.serializedPosition)
			else -> -1 // a1.inputs.hashCode().compareTo(a2.inputs.hashCode())
		}
	}
	val couchActions = mutableListOf<CouchAction>()

	positionsToVisit += Action(board.playerPosition, emptyList())

	do {
		val prevAction = positionsToVisit.remove()
		for (input in enumValues<Input>()) {
			val nextAction = prevAction + input
			when (val item = board.findItemAt(nextAction.playerPosition, metadata)) {
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
				is Couch -> couchActions += nextAction.toCouchAction(item)
			}
		}
	} while (positionsToVisit.isNotEmpty())

	return couchActions
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

	val (newStart, newEnd) = when (couchPosition.start) {
		playerPosition -> newCouchTarget to newCouchOther
		else -> newCouchOther to newCouchTarget
	}

	return couch.copy(
		position = CouchPosition(newStart, newEnd),
	)
}

class CouchSolver(private val boardSettings: BoardSettings) {
	private val metadata = BoardMetadata(boardSettings)

	fun findShortestSolution(): List<Input>? {
		val visitedBoards = ConcurrentSkipListMap<Int, MutableSet<BoardState>>()
		val pendingBoards = PriorityBlockingQueue<Pair<BoardState, List<Input>>>(10) { a, b ->
			when (val sizeComparison = a.second.size.compareTo(b.second.size)) {
				0 -> a.first.compareTo(b.first)
				else -> sizeComparison
			}
		}
		pendingBoards += boardSettings.toInitialBoardState() to emptyList()

		val solution = AtomicReference<List<Input>?>()
		val actionCount = AtomicInteger()

		fun compute(board: BoardState, inputs: List<Input>) {
			val couchActions = findPathsToCouches(board, metadata)
			actionCount.accumulateAndGet(couchActions.size, Int::plus)
			for (action in couchActions.sortedBy { it.inputs.size }) {
				val oldCouch = action.couch
				val newCouch = action.createNewCouch()
				val newlyOccupiedPosition = when {
					newCouch.position.start != oldCouch.position.start
							&& newCouch.position.start != oldCouch.position.end -> newCouch.position.start
					else -> newCouch.position.end
				}

				if (board.findItemAt(newlyOccupiedPosition, metadata) != Item.EMPTY) {
					// can't perform this action, results in invalid board
					continue
				}

				val newBoard = board.update(
					action.playerPosition,
					oldCouch to newCouch,
					metadata.goals,
				)
				val newInputs = inputs + action.inputs
				if (newBoard.isSolved(metadata.goals)) {
					solution.getAndUpdate { oldSolution ->
						if ((oldSolution?.size ?: Int.MAX_VALUE) > newInputs.size) {
							newInputs
						} else {
							oldSolution
						}
					}
					continue
				}
				if (newBoard.isCouchStuck(newCouch, metadata)) {
					// couch is stuck on a wall
					continue
				}

				val visitedList = visitedBoards.getOrPut(newBoard.hashCode()) { mutableSetOf() }
				if (newBoard in visitedList) {
					continue
				}
				visitedList += newBoard
				pendingBoards += newBoard to newInputs
			}
		}

		val pool = Executors.newFixedThreadPool(NUM_THREADS)

		val futures = (0 until NUM_THREADS).map { threadNum ->
			var iterationCount = 0
			val debugPrint = if (threadNum > 0) null
			else {
				{ inputs: List<Input> ->
					if (++iterationCount > 20_000) {
						iterationCount = 0
						println("Action count: ${actionCount.get()}; latest input size: ${inputs.size}")
						println("Latest inputs: ${inputs.joinToString("")}")
					}
				}
			}
			pool.submit {
				while (solution.get() == null) {
					var nextJob = pendingBoards.poll()
					if (nextJob == null) {
						for (_i in 0..NUM_THREADS) {
							TimeUnit.MILLISECONDS.sleep(100)
							nextJob = pendingBoards.poll()
							if (nextJob != null) {
								break
							}
						}
						if (nextJob == null) {
							// we're probably done
							return@submit
						}
					}

					if (solution.get() != null) {
						return@submit
					}
					val (board, inputs) = nextJob
					compute(board, inputs)

					debugPrint?.invoke(inputs)
				}
			}
		}

		futures.forEach { it.get() }
		pool.shutdown()

		println("Considered ${actionCount.get()} total couch actions")
		return solution.acquire
	}
}
