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
		println(board)
	}
}
