package com.bernaferrari.bravedns.util

/** Reconstructs captured Java/Go frames instead of grouping failures at the reporting callsite. */
internal object ExceptionParser {
    private val javaFrame = Regex("""^\s*at\s+([^\s(]+)\(([^)]*)\)""")
    private val goLocation = Regex("""^\s*(.+\.go):(\d+)(?:\s.*)?$""")
    private val goroutine = Regex("""^(?:\(#\d+\))?goroutine\s+\d+\s+\[.*""")
    private val level = Regex("""^[VDIWEF]\s+""")
    private const val MAX_FRAMES = 256

    fun parse(raw: String, context: String = "Captured crash"): RuntimeException {
        val lines = raw.lineSequence().map { it.trimEnd().replaceFirst(level, "") }.toList()
        val frames = javaFrames(lines).ifEmpty { goFrames(lines) }
        val headline = lines.firstOrNull { it.isNotBlank() }?.take(1500) ?: context
        return RuntimeException("$context: $headline").apply {
            // An unrecognized dump has no invented reporting-site stack.
            stackTrace = frames.take(MAX_FRAMES).toTypedArray()
        }
    }

    private fun javaFrames(lines: List<String>): List<StackTraceElement> = lines.mapNotNull { line ->
        val match = javaFrame.find(line) ?: return@mapNotNull null
        val function = match.groupValues[1]
        val location = match.groupValues[2]
        val source = location.substringBeforeLast(':')
        val number = location.substringAfterLast(':', "").toIntOrNull() ?: -1
        StackTraceElement(function.substringBeforeLast('.', "Java"), function.substringAfterLast('.'), source, number)
    }

    private fun goFrames(lines: List<String>): List<StackTraceElement> {
        val frames = mutableListOf<StackTraceElement>()
        val start = lines.indexOfFirst { goroutine.matches(it.trimStart()) }
        val block = if (start >= 0) lines.drop(start + 1) else lines
        val selected = block.takeWhile { !goroutine.matches(it.trimStart()) }.take(MAX_FRAMES * 2)
        var function: String? = null
        for (line in selected) {
            val location = goLocation.matchEntire(line)
            if (location != null) {
                function?.let {
                    frames.add(StackTraceElement("Go", it, location.groupValues[1], location.groupValues[2].toIntOrNull() ?: -1))
                }
                function = null
            } else if (isGoFunction(line.trimStart())) {
                function = line.trimStart().substringBefore('(').trim()
            }
        }
        return frames
    }

    private fun isGoFunction(line: String): Boolean =
        line.isNotBlank() && !line.startsWith("created by ") && line.contains('(')
}
