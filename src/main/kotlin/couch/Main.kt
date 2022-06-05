package couch

import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
	while (true) {
		print("File name (q to quit): ")
		val fileName = readLine()
		if (fileName.equals("q", true)) {
			break
		}
		println()
		val text = try {
			File(fileName).readText()
		} catch (e: FileNotFoundException) {
			println("Invalid file name.")
			continue
		}

		val board = Board.fromString(text)

		println("Solving...")
		val solution = CouchSolver(board).findShortestSolution()
		if (solution == null) {
			println("No solution found :(")
			continue
		}

		println("\n")
		println("—".repeat(solution.size * 2))
		println("Found solution of length ${solution.size}")
		println(solution
			.zipWithNext { a, b -> if (a == b) "$a" else "$a  " }
			.joinToString("") + solution.last())
		println("—".repeat(solution.size * 2))
		println()
	}
}
