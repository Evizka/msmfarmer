package com.msmfarmer.app.model

/**
 * Выученный переход между экранами/островами.
 *
 * [source] и [destination] — компактные отпечатки экрана (base64),
 * по которым приложение понимает, где оно находится и какой маршрут применить.
 */
data class LearnedRoute(
    val id: Long = System.currentTimeMillis(),
    val steps: List<MacroStep> = emptyList(),
    val source: String = "",
    val destination: String = "",
)
