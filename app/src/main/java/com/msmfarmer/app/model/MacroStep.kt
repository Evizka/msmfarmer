package com.msmfarmer.app.model

/** Одно действие макроса перехода между островами. */
sealed class MacroStep {
    abstract val afterMs: Long

    data class Tap(
        val x: Float,
        val y: Float,
        override val afterMs: Long = 600L,
    ) : MacroStep()

    data class Swipe(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val durationMs: Long = 320L,
        override val afterMs: Long = 700L,
    ) : MacroStep()

    data class Wait(
        val durationMs: Long = 1500L,
        override val afterMs: Long = 0L,
    ) : MacroStep()
}

object MacroCodec {
    fun encode(steps: List<MacroStep>): String = buildString {
        steps.forEach { step ->
            val line = when (step) {
                is MacroStep.Tap -> "TAP ${step.x.f()} ${step.y.f()} ${step.afterMs}"
                is MacroStep.Swipe ->
                    "SWIPE ${step.fromX.f()} ${step.fromY.f()} ${step.toX.f()} ${step.toY.f()} ${step.durationMs} ${step.afterMs}"
                is MacroStep.Wait -> "WAIT ${step.durationMs}"
            }
            append(line).append('\n')
        }
    }

    fun decode(text: String): List<MacroStep> = text.lineSequence().mapNotNull { line ->
        val p = line.trim().split(Regex("\\s+"))
        when (p.firstOrNull()) {
            "TAP" -> p.getFloat(1)?.let { x -> p.getFloat(2)?.let { y -> MacroStep.Tap(x, y, p.getLong(3, 600L)) } }
            "SWIPE" -> {
                val a = p.getFloat(1); val b = p.getFloat(2)
                val c = p.getFloat(3); val d = p.getFloat(4)
                if (a != null && b != null && c != null && d != null) {
                    MacroStep.Swipe(a, b, c, d, p.getLong(5, 320L), p.getLong(6, 700L))
                } else null
            }
            "WAIT" -> p.getLong(1)?.let { MacroStep.Wait(it) }
            else -> null
        }
    }.toList()

    fun describe(step: MacroStep): String = when (step) {
        is MacroStep.Tap -> "Нажать (${step.x.toInt()}, ${step.y.toInt()})"
        is MacroStep.Swipe ->
            "Свайп (${step.fromX.toInt()}, ${step.fromY.toInt()}) → (${step.toX.toInt()}, ${step.toY.toInt()})"
        is MacroStep.Wait -> "Ждать ${step.durationMs} мс"
    }

    private fun Float.f(): String = String.format(java.util.Locale.US, "%.1f", this)
    private fun List<String>.getFloat(i: Int): Float? = getOrNull(i)?.toFloatOrNull()
    private fun List<String>.getLong(i: Int, default: Long): Long = getOrNull(i)?.toLongOrNull() ?: default
    private fun List<String>.getLong(i: Int): Long? = getOrNull(i)?.toLongOrNull()
}
