package com.msmfarmer.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.msmfarmer.app.BuildConfig
import com.msmfarmer.app.state.FarmerState

/** Формирует отчёт и открывает его в GitHub issue или в системном шеринге. */
object LogExporter {

    private const val REPO_ISSUES = "https://github.com/Evizka/msmfarmer/issues/new"

    fun report(): String = buildString {
        append("MSM Farmer ").append(BuildConfig.VERSION_NAME)
        append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("Устройство: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("Время: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())).append("\n\n")
        append("Диагностика детектора:\n").append(FarmerState.detectorDebug.value).append("\n\n")
        append(GestureController.summary()).append("\n\n")
        append("Настройки: ").append(FarmerState.settings.value).append('\n')
        append("Обучено маршрутов: ").append(FarmerState.learnedRoutes.value.size).append("\n")
        append("Статистика: ").append(FarmerState.stats.value).append("\n\n")
        append("Логи (последние 50):\n")
        FarmerState.logs.value.forEach { entry ->
            append(entry.time).append(' ').append(entry.level).append(' ').append(entry.message).append('\n')
        }
    }

    fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MSM Farmer ${BuildConfig.VERSION_NAME} — логи")
            putExtra(Intent.EXTRA_TEXT, report())
        }
        context.startActivity(Intent.createChooser(intent, "Отправить логи"))
    }

    fun openGithubIssue(context: Context) {
        val title = Uri.encode("MSM Farmer ${BuildConfig.VERSION_NAME}: не собираются монеты")
        val body = Uri.encode(report().take(6000))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$REPO_ISSUES?title=$title&body=$body"))
        context.startActivity(intent)
    }
}
