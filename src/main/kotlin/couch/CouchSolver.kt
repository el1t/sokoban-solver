package couch

import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

const val NUM_THREADS = 10

data class Action(
	val playerPosition: Position,
	val inputs: List<Input>,
)

operator fun Action.plus(input: Input): Action = Action(
	playerPosition = playerPosition + input,
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
		for (input in Input.values) {
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
	direction: Input,
	prevPlayerPosition: Position,
): Pair<Position, Position> = when {
	couchTarget + direction == couchOther -> couchOther to couchOther + direction
	prevPlayerPosition.x == couchOther.x -> Position(couchTarget.x, couchOther.y) to couchOther
	prevPlayerPosition.y == couchOther.y -> Position(couchOther.x, couchTarget.y) to couchOther
	else -> couchTarget + direction to couchOther
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
		direction,
		playerPosition - direction,
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
		val visitedBoards = ConcurrentSkipListMap<BoardState.SerializedState, Int>()
		val pendingBoards =
			ConcurrentSkipListSet<Pair<BoardState.SerializedState, List<Input>>> { a, b ->
				when (val sizeComparison = a.second.size.compareTo(b.second.size)) {
					0 -> a.first.compareTo(b.first)
					else -> sizeComparison
				}
			}
		val duplicateBoards = ConcurrentSkipListMap<BoardState.SerializedState, Int>()
		pendingBoards += boardSettings.toInitialBoardState().serialize() to emptyList()

		val solution = AtomicReference<List<Input>?>()

		fun compute(board: BoardState, inputs: List<Input>) {
			duplicateBoards[board.serialize()]?.let {
				when {
					it == inputs.size -> duplicateBoards.remove(board.serialize())
					it > inputs.size -> return@let
				}
				return
			}
			val couchActions = findPathsToCouches(board, metadata)
			for (action in couchActions) {
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
					continue
				}

				val boardHash = newBoard.serialize()
				val prevInputCount = visitedBoards[boardHash]
				if (prevInputCount != null) {
					if (prevInputCount <= newInputs.size) {
						continue
					}
					duplicateBoards[boardHash] = prevInputCount
				}
				visitedBoards[boardHash] = newInputs.size
				pendingBoards += newBoard.serialize() to newInputs
			}
		}

		val pool = Executors.newFixedThreadPool(NUM_THREADS)

		fun shouldContinue(): Boolean = solution.get().let {
			it == null || it.size > pendingBoards.first().second.size
		}

		val futures = (0 until NUM_THREADS).map { threadNum ->
			var iterationCount = 0
			val clearDuplicates = if (threadNum > 0) null
			else {
				{
					if (iterationCount % 3_000 == 0 && duplicateBoards.size > 50_000) {
						duplicateBoards.clear()
					}
				}
			}
			val debugPrint = if (threadNum > 0) null
			else {
				{ inputs: List<Input> ->
					if (++iterationCount > 20_000) {
						iterationCount = 0
						println(
							"Visited boards: ${visitedBoards.size}; " +
									"latest input size: ${inputs.size}; " +
									"duplicate boards: ${duplicateBoards.size}; " +
									"pending boards: ${pendingBoards.size}"
						)
						println("Latest inputs: ${inputs.joinToString("")}")
					}
				}
			}
			pool.submit {
				while (shouldContinue()) {
					var nextJob = pendingBoards.pollFirst()
					if (nextJob == null) {
						for (_i in 0..NUM_THREADS) {
							TimeUnit.MILLISECONDS.sleep(100)
							nextJob = pendingBoards.pollFirst()
							if (nextJob != null) {
								break
							}
						}
						if (nextJob == null) {
							// we're probably done
							return@submit
						}
					}

					if (!shouldContinue()) {
						return@submit
					}
					val (serializedBoard, inputs) = nextJob
					compute(serializedBoard.deserialize(), inputs)

					clearDuplicates?.invoke()
					debugPrint?.invoke(inputs)
				}
			}
		}

		futures.forEach { it.get() }
		pool.shutdown()

		println("Considered ${visitedBoards.size} total board states")

//		val collisionCount = visitedBoards.values.sumOf { it.size - 1 }
//		println("$collisionCount hash collisions")

		return solution.acquire
	}
}
