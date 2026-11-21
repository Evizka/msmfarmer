package com.msmfarmer.app.model

/** Найденная на экране жёлтая иконка (кандидат в монету). */
data class Detection(
    val x: Float,
    val y: Float,
    val radius: Float,
    val score: Float,
)
