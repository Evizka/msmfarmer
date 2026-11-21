@file:OptIn(ExperimentalMaterial3Api::class)

package com.msmfarmer.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msmfarmer.app.core.CoinDetector
import com.msmfarmer.app.core.LogExporter
import com.msmfarmer.app.model.Detection
import com.msmfarmer.app.model.FarmerSettings
import com.msmfarmer.app.model.MacroCodec
import com.msmfarmer.app.model.MacroStep
import com.msmfarmer.app.state.FarmerState
import kotlin.math.roundToInt

@Composable
fun AppRoot(
    onEnableAccessibility: () -> Unit,
    onRequestCapture: () -> Unit,
    onStop: () -> Unit,
    onReplay: () -> Unit,
    onStartLearning: () -> Unit,
    onRequestOverlay: () -> Unit,
    onStopLearning: () -> Unit,
    onMarkLearned: () -> Unit,
    onToggleHud: () -> Unit,
    onSave: () -> Unit,
) {
    val running by FarmerState.running.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onSave() }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Как пользоваться", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Включите спец. возможности для MSM Farmer в настройках Android.")
                    Text("2. Разрешите запись экрана и доступ поверх других окон.")
                    Text("3. Нажмите «Начать сбор», сверните приложение и откройте MSM.")
                    Text("4. Для обхода островов обучите маршруты во вкладке «Настройки».")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Понятно") }
            },
        )
    }

    Scaffold(
        containerColor = AppColors.Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Canvas),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppColors.PrimarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("M", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MSM Farmer", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (running) "Сбор активен" else "Готов к запуску",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextMuted,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Справка", tint = AppColors.TextMuted)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.Surface,
                contentColor = AppColors.Primary,
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Главная") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.ShowChart, contentDescription = null) },
                    label = { Text("Статистика") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                0 -> HomeTab(
                    running,
                    onEnableAccessibility,
                    onRequestCapture,
                    onRequestOverlay,
                    onStop,
                    onToggleHud,
                    onSave,
                )
                1 -> StatsTab()
                2 -> SettingsTab(onReplay, onStartLearning, onStopLearning, onMarkLearned, onSave)
            }
        }
    }
}

@Composable
private fun HomeTab(
    running: Boolean,
    onEnableAccessibility: () -> Unit,
    onRequestCapture: () -> Unit,
    onRequestOverlay: () -> Unit,
    onStop: () -> Unit,
    onToggleHud: () -> Unit,
    onSave: () -> Unit,
) {
    val hudVisible by FarmerState.hudVisible.collectAsStateWithLifecycle()
    val hudStatus by FarmerState.hudStatus.collectAsStateWithLifecycle()
    val accessibility by FarmerState.accessibilityReady.collectAsStateWithLifecycle()
    val capturing by FarmerState.capturing.collectAsStateWithLifecycle()
    val overlayReady by FarmerState.overlayReady.collectAsStateWithLifecycle()
    val stats by FarmerState.stats.collectAsStateWithLifecycle()
    val settings by FarmerState.settings.collectAsStateWithLifecycle()
    val frame by FarmerState.lastFrame.collectAsStateWithLifecycle()
    val detections by FarmerState.lastDetections.collectAsStateWithLifecycle()

    fun update(transform: (FarmerSettings) -> FarmerSettings) {
        FarmerState.settings.value = transform(FarmerState.settings.value)
        onSave()
    }

    HeroCard(
        running = running,
        status = hudStatus,
        hudVisible = hudVisible,
        enabled = accessibility || running,
        onStart = if (running) onStop else onRequestCapture,
        onToggleHud = onToggleHud,
    )

    SoftCard("Подготовка") {
        PermissionRow(
            title = "Спец. возможности",
            subtitle = "Для автоматизации нажатий",
            done = accessibility,
            onAction = onEnableAccessibility,
        )
        PermissionRow(
            title = "Запись экрана",
            subtitle = "Для поиска монет",
            done = capturing,
            onAction = onRequestCapture,
        )
        PermissionRow(
            title = "Поверх других окон",
            subtitle = "Для панели поверх приложений",
            done = overlayReady,
            onAction = onRequestOverlay,
        )
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            icon = Icons.Outlined.Bolt,
            value = stats.taps.toString(),
            label = "Собрано",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Outlined.Layers,
            value = stats.islandsVisited.toString(),
            label = "Островов",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Outlined.Timer,
            value = stats.scans.toString(),
            label = "Сканов",
            modifier = Modifier.weight(1f),
        )
    }

    SoftCard("Настройки") {
        val speed = (1000L - settings.scanIntervalMs) / 880f
        SliderRow(
            label = "Скорость поиска",
            valueText = "${settings.scanIntervalMs} мс",
            value = speed.coerceIn(0f, 1f),
            startLabel = "Точно",
            endLabel = "Быстро",
        ) {
            update { s -> s.copy(scanIntervalMs = (1000L - (it * 880f).roundToInt()).coerceIn(120L, 1000L)) }
        }
        SliderRow(
            label = "Пауза после монеты",
            valueText = "${(settings.tapCooldownMs / 100f).roundToInt() / 10f} с",
            value = settings.tapCooldownMs.toFloat(),
            range = 300f..3000f,
            startLabel = "0.3 с",
            endLabel = "3.0 с",
        ) { update { s -> s.copy(tapCooldownMs = it.roundToInt().toLong()) } }
    }

    SoftCard("Предпросмотр") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Область поиска монет",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (settings.debugMarkers) {
                Text(
                    "${detections.size} целей",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        PreviewImage(frame, detections, settings.debugMarkers)
    }
}

@Composable
private fun StatsTab() {
    val context = LocalContext.current
    val stats by FarmerState.stats.collectAsStateWithLifecycle()
    val logs by FarmerState.logs.collectAsStateWithLifecycle()
    val detectorDebug by FarmerState.detectorDebug.collectAsStateWithLifecycle()
    val uptime = if (stats.startedAt > 0L) {
        (System.currentTimeMillis() - stats.startedAt) / 1000
    } else 0L

    SoftCard("Диагностика") {
        Text(detectorDebug, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        Text(
            "Если сканы идут, а тапов нет — отправьте этот отчёт разработчику.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { LogExporter.share(context) }) { Text("Поделиться") }
            OutlinedButton(onClick = { LogExporter.openGithubIssue(context) }) { Text("GitHub issue") }
        }
    }

    SoftCard("Сводка") {
        Row(Modifier.fillMaxWidth()) {
            DetailStat("Сканирований", stats.scans.toString(), Modifier.weight(1f))
            DetailStat("Нажатий", stats.taps.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            DetailStat("Островов", stats.islandsVisited.toString(), Modifier.weight(1f))
            DetailStat("Время", formatDuration(uptime), Modifier.weight(1f))
        }
    }

    SoftCard("Логи") {
        if (logs.isEmpty()) {
            Text("Логов пока нет", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(logs) { entry ->
                    val color = when (entry.level) {
                        FarmerState.Level.OK -> AppColors.Success
                        FarmerState.Level.WARN -> AppColors.Danger
                        FarmerState.Level.INFO -> AppColors.Text
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            entry.time,
                            color = AppColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(entry.message, color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(onClick = { FarmerState.logs.value = emptyList() }) { Text("Очистить") }
        }
    }
}

@Composable
private fun SettingsTab(
    onReplay: () -> Unit,
    onStartLearning: () -> Unit,
    onStopLearning: () -> Unit,
    onMarkLearned: () -> Unit,
    onSave: () -> Unit,
) {
    val settings by FarmerState.settings.collectAsStateWithLifecycle()
    val frame by FarmerState.lastFrame.collectAsStateWithLifecycle()
    val detections by FarmerState.lastDetections.collectAsStateWithLifecycle()
    val macro by FarmerState.macro.collectAsStateWithLifecycle()
    val learnedRoutes by FarmerState.learnedRoutes.collectAsStateWithLifecycle()
    val learning by FarmerState.learning.collectAsStateWithLifecycle()

    fun update(transform: (FarmerSettings) -> FarmerSettings) {
        FarmerState.settings.value = transform(FarmerState.settings.value)
        onSave()
    }

    SoftCard("Распознавание") {
        SliderRow(
            label = "Чувствительность",
            valueText = "${(settings.sensitivity * 100).roundToInt()}%",
            value = settings.sensitivity,
        ) { update { s -> s.copy(sensitivity = it) } }
        SliderRow(
            label = "Максимум тапов за кадр",
            valueText = "${settings.tapsPerScan}",
            value = settings.tapsPerScan.toFloat(),
            range = 1f..12f,
        ) { update { s -> s.copy(tapsPerScan = it.roundToInt()) } }
        OutlinedButton(
            onClick = {
                val bitmap = frame
                if (bitmap != null) {
                    val tuned = CoinDetector.autoCalibrate(bitmap, FarmerState.settings.value)
                    update { s -> s.copy(sensitivity = tuned) }
                    FarmerState.log("Чувствительность обучена: ${(tuned * 100).roundToInt()}%", FarmerState.Level.OK)
                } else {
                    FarmerState.log("Нет кадра для обучения", FarmerState.Level.WARN)
                }
            },
            enabled = frame != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Обучить по текущему кадру") }
        SwitchRow("Показывать найденные монеты", settings.debugMarkers) {
            update { s -> s.copy(debugMarkers = it) }
        }
    }

    SoftCard("Обход островов") {
        SwitchRow("Переключать при простое", settings.autoIslandSwitch) {
            update { s -> s.copy(autoIslandSwitch = it) }
        }
        SliderRow(
            label = "Количество островов",
            valueText = "${settings.islandsCount}",
            value = settings.islandsCount.toFloat(),
            range = 1f..30f,
        ) { update { s -> s.copy(islandsCount = it.roundToInt()) } }
        SliderRow(
            label = "Ждать перед переходом",
            valueText = "${settings.idleMsBeforeSwitch / 1000} с",
            value = settings.idleMsBeforeSwitch.toFloat(),
            range = 2000f..20000f,
            startLabel = "2 с",
            endLabel = "20 с",
        ) { update { s -> s.copy(idleMsBeforeSwitch = it.roundToInt().toLong()) } }
        SwitchRow("Зацикливать", settings.loopIslands) {
            update { s -> s.copy(loopIslands = it) }
        }
    }

    SoftCard("Самообучение") {
        Text(
            "Совершите переход в игре вручную, затем отметьте прибытие кнопкой «✓ Остров». " +
                "Приложение запомнит жесты и будет повторять освоенные маршруты.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartLearning, enabled = !learning) { Text("Обучать") }
            OutlinedButton(onClick = onMarkLearned, enabled = learning) { Text("✓ Остров") }
            OutlinedButton(onClick = onStopLearning, enabled = learning) { Text("Стоп") }
        }
        SwitchRow("Использовать выученные маршруты", settings.preferLearnedRoutes) {
            update { s -> s.copy(preferLearnedRoutes = it) }
        }
        SwitchRow("Подстраивать после перехода", settings.autoCalibrateOnIsland) {
            update { s -> s.copy(autoCalibrateOnIsland = it) }
        }
        if (learnedRoutes.isEmpty()) {
            Text("Маршрутов пока нет", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        } else {
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                itemsIndexed(learnedRoutes) { index, route ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Маршрут ${index + 1} · ${route.steps.size} действий${if (route.source.isNotEmpty()) " · экран узнан" else ""}", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val next = FarmerState.learnedRoutes.value.toMutableList()
                            next.removeAt(index)
                            FarmerState.learnedRoutes.value = next
                            onSave()
                        }) { Text("Удалить") }
                    }
                    Divider()
                }
            }
        }
    }

    SoftCard("Свой макрос") {
        MacroPreview(
            frame = frame,
            detections = detections,
            showMarkers = settings.debugMarkers,
            onAddStep = { step ->
                FarmerState.macro.value = FarmerState.macro.value + step
                onSave()
            },
        )
        if (macro.isEmpty()) {
            Text("Шагов пока нет", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        } else {
            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                itemsIndexed(macro) { index, step ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. ${MacroCodec.describe(step)}", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val next = FarmerState.macro.value.toMutableList()
                            next.removeAt(index)
                            FarmerState.macro.value = next
                            onSave()
                        }) { Text("Удалить") }
                    }
                    Divider()
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                FarmerState.macro.value = FarmerState.macro.value + MacroStep.Wait(1500L)
                onSave()
            }) { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            OutlinedButton(onClick = onReplay) { Text("Тест") }
            OutlinedButton(onClick = {
                FarmerState.macro.value = emptyList()
                onSave()
            }) { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun MacroPreview(
    frame: Bitmap?,
    detections: List<Detection>,
    showMarkers: Boolean,
    onAddStep: (MacroStep) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Режим", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { mode = 0 }, modifier = Modifier.padding(end = 6.dp)) { Text("Тап") }
        OutlinedButton(onClick = { mode = 1 }) { Text("Свайп") }
    }

    if (frame == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text("Запустите сбор, чтобы увидеть экран", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val bitmap = frame
    val pointerModifier = if (mode == 0) {
        Modifier.pointerInput(bitmap) {
            detectTapGestures { offset ->
                val p = mapToScreen(offset, size, bitmap)
                onAddStep(MacroStep.Tap(p.x, p.y))
            }
        }
    } else {
        Modifier.pointerInput(bitmap) {
            var start = Offset.Zero
            var end = Offset.Zero
            detectDragGestures(
                onDragStart = { start = it; end = it },
                onDrag = { _, amount -> end = end + amount },
                onDragEnd = {
                    val a = mapToScreen(start, size, bitmap)
                    val b = mapToScreen(end, size, bitmap)
                    onAddStep(MacroStep.Swipe(a.x, a.y, b.x, b.y))
                },
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.SurfaceMuted),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Кадр экрана",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .then(pointerModifier),
        )
        if (showMarkers) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sx = size.width / bitmap.width
                val sy = size.height / bitmap.height
                detections.forEach { d ->
                    drawCircle(
                        color = AppColors.Gold,
                        radius = d.radius * kotlin.math.min(sx, sy) * 1.5f,
                        center = Offset(d.x * sx, d.y * sy),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(bitmap: Bitmap?, detections: List<Detection>, showMarkers: Boolean) {
    if (bitmap == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, tint = AppColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Нет кадра — запустите сбор",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        }
        return
    }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Предпросмотр экрана",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
        )
        if (showMarkers) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sx = size.width / bitmap.width
                val sy = size.height / bitmap.height
                detections.forEach { d ->
                    drawCircle(
                        color = AppColors.Gold,
                        radius = d.radius * kotlin.math.min(sx, sy) * 1.5f,
                        center = Offset(d.x * sx, d.y * sy),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    running: Boolean,
    status: String,
    hudVisible: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onToggleHud: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MSM FARMER",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Canvas.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (running) "● В работе" else "○ Готов к запуску",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Canvas,
                )
            }
            Text(status, style = MaterialTheme.typography.titleLarge, color = AppColors.Canvas)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStart,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Surface,
                        contentColor = AppColors.Primary,
                        disabledContainerColor = AppColors.Surface.copy(alpha = 0.5f),
                        disabledContentColor = AppColors.Primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "Остановить" else "Начать сбор", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = onToggleHud) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = "Панель поверх приложений",
                        tint = if (hudVisible) AppColors.Canvas else AppColors.Canvas.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    done: Boolean,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (done) AppColors.PrimarySoft else AppColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (title) {
                    "Спец. возможности" -> Icons.Outlined.Accessibility
                    "Запись экрана" -> Icons.Outlined.ScreenShare
                    else -> Icons.Outlined.Layers
                },
                contentDescription = null,
                tint = if (done) AppColors.Primary else AppColors.TextMuted,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
        if (done) {
            Icon(Icons.Outlined.Check, contentDescription = "Разрешено", tint = AppColors.Success)
        } else {
            TextButton(onClick = onAction) { Text("Разрешить") }
        }
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = AppColors.Primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    startLabel: String? = null,
    endLabel: String? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = AppColors.Primary)
        }
        Slider(
            value = value.coerceIn(range),
            valueRange = range,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                activeTrackColor = AppColors.Primary,
                inactiveTrackColor = AppColors.Border,
                thumbColor = AppColors.Primary,
            ),
        )
        if (startLabel != null && endLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(startLabel, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                Text(
                    endLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.Primary,
                uncheckedTrackColor = AppColors.Border,
            ),
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

private fun mapToScreen(offset: Offset, size: IntSize, bitmap: Bitmap): Offset = Offset(
    offset.x / size.width.coerceAtLeast(1) * bitmap.width,
    offset.y / size.height.coerceAtLeast(1) * bitmap.height,
)
