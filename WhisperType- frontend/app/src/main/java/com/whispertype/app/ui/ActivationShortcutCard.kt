package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.whispertype.app.ShortcutPreferences

// ── Design colour palette (scoped locally) ────────────────────────
private val DsgBg       = Color(0xFFF6EEE7)
private val DsgCard     = Color(0xFFF1E7DD)
private val DsgCard2    = Color(0xFFEBDFD3)
private val DsgInk      = Color(0xFF1B1410)
private val DsgInk2     = Color(0xFF4A3F38)
private val DsgInkMute  = Color(0xFF8A7A6E)
private val DsgInkFade  = Color(0xFFB6A99D)
private val DsgLine     = Color(0xFF1B1410).copy(alpha = 0.08f)
private val DsgLine2    = Color(0xFF1B1410).copy(alpha = 0.14f)
private val DsgBrand    = Color(0xFFC8543A)
private val DsgBrandDp  = Color(0xFFA6422C)
private val DsgBrandSft = Color(0xFFC8543A).copy(alpha = 0.12f)
private val DsgBrandMid = Color(0xFFC8543A).copy(alpha = 0.22f)
private val DsgGreen    = Color(0xFF2BA070)
private val DsgPhoneBg  = Color(0xFF1a120d)
private val DsgPhoneBd  = Color(0xFF2C2018)

// ── Volume shortcut options ───────────────────────────────────────
private data class VolOpt(
    val mode: ShortcutPreferences.ShortcutMode,
    val name: String,
    val desc: String,
    val kind: String,
)

private val VOL_OPTS = listOf(
    VolOpt(ShortcutPreferences.ShortcutMode.BOTH_VOLUME_BUTTONS,
        "Both buttons together", "Press volume up + down at the same time", "both"),
    VolOpt(ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_UP,
        "Volume up (double-tap)", "Double-tap the volume up button quickly", "up"),
    VolOpt(ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_DOWN,
        "Volume down (double-tap)", "Double-tap the volume down button quickly", "down"),
)

// ── Public entry point ────────────────────────────────────────────

@Composable
fun ActivationShortcutCard(onTestOverlay: () -> Unit) {
    val context = LocalContext.current

    var volOn by remember {
        mutableStateOf(ShortcutPreferences.isVolumeShortcutEnabled(context))
    }
    var tfOn by remember {
        mutableStateOf(ShortcutPreferences.isAutoShowIconEnabled(context))
    }
    var volMode by remember {
        mutableStateOf(ShortcutPreferences.getShortcutMode(context))
    }

    val activeCount = (if (volOn) 1 else 0) + (if (tfOn) 1 else 0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DsgCard)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Column {
            // ── Eyebrow ───────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(DsgBrand)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ACTIVATION SHORTCUT",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = DsgBrand
                )
            }

            // ── Heading ───────────────────────────────────────────
            Text(
                text = "Two ways to start.",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DsgInk,
                lineHeight = 27.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Use either or both.",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DsgBrand,
                lineHeight = 27.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Vozcribe listens through a hardware shortcut and an on-screen mic that appears when you focus a text field.",
                fontSize = 14.sp,
                color = DsgInkMute,
                lineHeight = 21.sp
            )

            // ── Hero diagram ──────────────────────────────────────
            HeroDiagram(
                volOn = volOn,
                tfOn = tfOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            )

            // ── Method rows ───────────────────────────────────────
            Spacer(Modifier.height(14.dp))

            MethodRow(
                number = 1,
                title = "Volume buttons",
                tag = "Hardware",
                desc = "Press a key combo from any app — even when the screen is off.",
                isOn = volOn,
                onToggle = {
                    volOn = it
                    ShortcutPreferences.setVolumeShortcutEnabled(context, it)
                }
            ) {
                // Sub-options: key combo picker
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HRule()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "CHOOSE KEY COMBO",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = DsgInkMute,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    VOL_OPTS.forEach { opt ->
                        val sel = volMode == opt.mode
                        VolOptRow(
                            opt = opt,
                            selected = sel,
                            onClick = {
                                volMode = opt.mode
                                ShortcutPreferences.setShortcutMode(context, opt.mode)
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            MethodRow(
                number = 2,
                title = "Floating mic icon",
                tag = "On-screen",
                desc = "A round Vozcribe icon appears on the side of your screen whenever a text field is active. Tap it to start dictating.",
                isOn = tfOn,
                onToggle = {
                    tfOn = it
                    ShortcutPreferences.setAutoShowIconEnabled(context, it)
                }
            )

            // ── Status banner ─────────────────────────────────────
            Spacer(Modifier.height(14.dp))

            StatusBanner(activeCount = activeCount)

            // ── Test button ───────────────────────────────────────
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onTestOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DsgGreen),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Test Overlay",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── Hero diagram ──────────────────────────────────────────────────

@Composable
private fun HeroDiagram(volOn: Boolean, tfOn: Boolean, modifier: Modifier = Modifier) {
    val heroGradient = Brush.verticalGradient(listOf(Color(0xFFFAF3EC), Color(0xFFF4E9DD)))
    val phoneH = 244.dp
    // Volume buttons sit ~48dp from phone top; stage has 50dp top padding.
    // Anno-1 should align with the volume buttons vertically.
    val annoVolBtnOffsetY = 48.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(heroGradient)
            .border(1.dp, DsgLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 22.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Stage: Anno-1 LEFT of phone, Anno-2 below ─────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(phoneH + 50.dp) // phone height + room for anno-2 below
            ) {
                // Phone — pushed toward the right, offset slightly left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-18).dp)
                ) {
                    PhoneSketch(volOn = volOn, tfOn = tfOn)
                }

                // Anno 1 — left side, vertically at volume-button height
                AnnoCard(
                    num = "1", title = "Volume keys", subtitle = "Hardware",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = annoVolBtnOffsetY)
                )

                // Horizontal dashed connector from Anno-1 to phone
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = annoVolBtnOffsetY + 14.dp) // vertically centered in anno card
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val startX = 130.dp.toPx()          // after anno card
                    val endX   = size.width - 130.dp.toPx() // up to phone left edge
                    if (endX > startX) {
                        drawLine(
                            color = DsgBrand.copy(alpha = 0.55f),
                            start = Offset(startX, size.height / 2),
                            end   = Offset(endX,   size.height / 2),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                        )
                    }
                }

                // Anno 2 — bottom-center
                AnnoCard(
                    num = "2", title = "Floating mic", subtitle = "On-screen",
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Vertical dashed connector from phone bottom to Anno-2
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(60.dp)
                        .height(22.dp)
                        .offset(y = (-42.dp)) // just above Anno-2 card
                ) {
                    drawLine(
                        color = DsgBrand.copy(alpha = 0.55f),
                        start = Offset(size.width / 2, 0f),
                        end   = Offset(size.width / 2, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                LegendItem("1", "Press")
                LegendDot()
                LegendItem("2", "Tap")
                LegendDot()
                Text("Either works.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DsgInkMute)
            }
        }
    }
}

@Composable
private fun AnnoCard(num: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier.border(1.dp, DsgLine, RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(DsgBrand)
            ) {
                Text(num, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DsgInk)
                Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DsgInk2)
            }
        }
    }
}

@Composable
private fun LegendItem(num: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(num, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DsgInk2)
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DsgInkMute)
    }
}

@Composable
private fun LegendDot() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(4.dp)
            .clip(CircleShape)
            .background(DsgInkFade)
    )
}

// ── Phone illustration ────────────────────────────────────────────

@Composable
private fun PhoneSketch(volOn: Boolean, tfOn: Boolean) {
    val phoneW = 130.dp
    val phoneH = 244.dp

    // Caret blink
    val caretAnim = rememberInfiniteTransition(label = "caret")
    val caretAlpha by caretAnim.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 490
                0f at 500
                0f at 990
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "caret"
    )

    // Bubble pulse
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.95f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(modifier = Modifier.size(phoneW, phoneH)) {
        // Phone body + volume buttons + screen content drawn on canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerR = 24.dp.toPx()
            val borderW = 2.dp.toPx()

            // Phone body
            drawRoundRect(
                color = DsgPhoneBd,
                cornerRadius = CornerRadius(cornerR, cornerR),
                style = Stroke(width = borderW)
            )
            drawRoundRect(
                color = DsgPhoneBg,
                topLeft = Offset(borderW / 2, borderW / 2),
                size = Size(w - borderW, h - borderW),
                cornerRadius = CornerRadius(cornerR - borderW / 2, cornerR - borderW / 2)
            )

            // Screen area (white inset)
            val screenL = 5.dp.toPx()
            val screenT = 8.dp.toPx()
            val screenR = 5.dp.toPx()
            val screenB = 8.dp.toPx()
            val screenCorner = 18.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(screenL, screenT),
                size = Size(w - screenL - screenR, h - screenT - screenB),
                cornerRadius = CornerRadius(screenCorner, screenCorner)
            )

            // Status bar row
            val sbTop = screenT + 14.dp.toPx()
            val sbLeft = screenL + 10.dp.toPx()
            // Time placeholder (small rect)
            drawRoundRect(
                color = DsgInk,
                topLeft = Offset(sbLeft, sbTop),
                size = Size(22.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Icons placeholder
            repeat(3) { i ->
                drawRoundRect(
                    color = DsgInk,
                    topLeft = Offset(w - screenR - 10.dp.toPx() - i * 7.dp.toPx(), sbTop),
                    size = Size(5.dp.toPx(), 5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }

            // Content bars
            val barTop = sbTop + 13.dp.toPx()
            val barH = 4.dp.toPx()
            val barL = screenL + 10.dp.toPx()
            val barMaxW = w - screenL - screenR - 20.dp.toPx()
            val barWidths = listOf(0.92f, 0.78f, 0.55f, 0.78f)
            barWidths.forEachIndexed { i, frac ->
                drawRoundRect(
                    color = Color(0xFFEFE5DA),
                    topLeft = Offset(barL, barTop + i * (barH + 5.dp.toPx())),
                    size = Size(barMaxW * frac, barH),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            // Focused text field near bottom
            val tfBottom = screenT + (h - screenT - screenB) - 10.dp.toPx()
            val tfH = 26.dp.toPx()
            val tfTop = tfBottom - tfH
            val tfL = screenL + 6.dp.toPx()
            val tfW = w - screenL - screenR - 12.dp.toPx()
            // bg fill
            drawRoundRect(
                color = Color(0xFFF6EEE7),
                topLeft = Offset(tfL, tfTop),
                size = Size(tfW, tfH),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            // border glow
            drawRoundRect(
                color = DsgBrandMid,
                topLeft = Offset(tfL, tfTop),
                size = Size(tfW, tfH),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // caret
            val caretX = tfL + 6.dp.toPx()
            val caretCY = tfTop + tfH / 2
            drawLine(
                color = DsgBrand.copy(alpha = caretAlpha),
                start = Offset(caretX, caretCY - 5.5.dp.toPx()),
                end = Offset(caretX, caretCY + 5.5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Volume buttons (left side)
            val vbtnX = 0f
            val vbtnW = 5.dp.toPx()
            val vbtnH = 26.dp.toPx()
            val vbtnTop1 = 48.dp.toPx()
            val vbtnTop2 = vbtnTop1 + vbtnH + 5.dp.toPx()
            val btnColor = if (volOn) DsgBrand else DsgPhoneBd
            val btnRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

            listOf(vbtnTop1, vbtnTop2).forEach { top ->
                drawRoundRect(
                    color = btnColor,
                    topLeft = Offset(vbtnX, top),
                    size = Size(vbtnW, vbtnH),
                    cornerRadius = btnRadius
                )
                if (volOn) {
                    drawRoundRect(
                        color = DsgBrand.copy(alpha = 0.18f),
                        topLeft = Offset(vbtnX - 3.dp.toPx(), top - 3.dp.toPx()),
                        size = Size(vbtnW + 3.dp.toPx(), vbtnH + 6.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }

            // Sound waves from volume buttons when volOn
            if (volOn) {
                drawSoundWaves(
                    origin = Offset(vbtnX, vbtnTop1 + vbtnH + (vbtnTop2 - vbtnTop1) / 2),
                    brand = DsgBrand
                )
            }
        }

        // Floating bubble (rendered as Compose Box on top of canvas)
        if (tfOn) {
            val phoneHPx = phoneH
            val bubbleSize = 30.dp
            val rightPad = 11.dp
            val topFrac = 0.60f

            Box(
                modifier = Modifier
                    .size(bubbleSize)
                    .align(Alignment.TopEnd)
                    .offset(
                        x = -rightPad,
                        y = phoneHPx * topFrac - bubbleSize / 2
                    )
            ) {
                // Pulse ring
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(bubbleSize + 8.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            alpha = pulseAlpha
                        )
                        .border(1.5.dp, DsgBrand, CircleShape)
                )
                // Bubble body
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(6.dp, CircleShape, ambientColor = DsgBrand.copy(0.5f), spotColor = DsgBrand.copy(0.5f))
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFD3654A), DsgBrand, DsgBrandDp)
                            )
                        )
                ) {
                    WaveformIcon(size = 14.dp, color = Color.White)
                }
            }
        }
    }
}

private fun DrawScope.drawSoundWaves(origin: Offset, brand: Color) {
    val path1 = Path().apply {
        moveTo(origin.x - 8.dp.toPx(), origin.y - 10.dp.toPx())
        quadraticBezierTo(origin.x - 16.dp.toPx(), origin.y, origin.x - 8.dp.toPx(), origin.y + 10.dp.toPx())
    }
    val path2 = Path().apply {
        moveTo(origin.x - 14.dp.toPx(), origin.y - 16.dp.toPx())
        quadraticBezierTo(origin.x - 28.dp.toPx(), origin.y, origin.x - 14.dp.toPx(), origin.y + 16.dp.toPx())
    }
    val path3 = Path().apply {
        moveTo(origin.x - 20.dp.toPx(), origin.y - 22.dp.toPx())
        quadraticBezierTo(origin.x - 38.dp.toPx(), origin.y, origin.x - 20.dp.toPx(), origin.y + 22.dp.toPx())
    }
    drawPath(path1, brand, alpha = 0.7f, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path2, brand, alpha = 0.45f, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path3, brand, alpha = 0.25f, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}

// ── Waveform icon (brand mark) ────────────────────────────────────

@Composable
private fun WaveformIcon(size: Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Points proportional to the SVG viewBox 0 0 80 80
        // path: M14 30 L22 42 L27 30 L32 52 L36 28 L40 60 L44 26 L48 50 L53 30 L58 40 L66 18
        val xs = listOf(14f, 22f, 27f, 32f, 36f, 40f, 44f, 48f, 53f, 58f, 66f).map { it / 80f * w }
        val ys = listOf(30f, 42f, 30f, 52f, 28f, 60f, 26f, 50f, 30f, 40f, 18f).map { it / 80f * h }
        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) path.lineTo(xs[i], ys[i])
        drawPath(path, color, style = Stroke(width = 6f / 80f * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Method row ────────────────────────────────────────────────────

@Composable
private fun MethodRow(
    number: Int,
    title: String,
    tag: String,
    desc: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    subContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isOn) Color.White else Color(0xFFFAF3EC))
            .border(
                width = 1.5.dp,
                color = if (isOn) DsgBrandMid else DsgLine,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Number badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOn) DsgBrand else Color(0xFF1B1410).copy(alpha = 0.08f)
                        )
                ) {
                    Text(
                        text = number.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOn) Color.White else DsgInk2
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Body
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.15).sp,
                            color = DsgInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isOn) DsgBrandSft else Color(0xFF1B1410).copy(alpha = 0.06f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag.uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                color = if (isOn) DsgBrand else DsgInkMute,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = desc,
                        fontSize = 12.5.sp,
                        color = DsgInkMute,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Toggle
                DsgToggle(on = isOn, onChange = onToggle)
            }

            // Sub-content (only visible when on)
            if (subContent != null) {
                AnimatedVisibility(
                    visible = isOn,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    subContent()
                }
            }
        }
    }
}

// ── Volume option row ─────────────────────────────────────────────

@Composable
private fun VolOptRow(opt: VolOpt, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) DsgBrandSft else Color(0xFF1B1410).copy(alpha = 0.025f))
            .border(
                1.dp,
                if (selected) DsgBrandMid else Color.Transparent,
                RoundedCornerShape(11.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        // Radio dot
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .border(
                    2.dp,
                    if (selected) DsgBrand else DsgLine2,
                    CircleShape
                )
                .clip(CircleShape)
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DsgBrand)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Vol glyph
        VolGlyph(kind = opt.kind, color = if (selected) DsgBrand else DsgInk2, size = 22.dp)

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = opt.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) DsgBrandDp else DsgInk,
                letterSpacing = (-0.1).sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            Text(
                text = opt.desc,
                fontSize = 11.5.sp,
                color = DsgInkMute,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun VolGlyph(kind: String, color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun scale(x: Float, y: Float) = Offset(x / 24f * w, y / 24f * h)

        when (kind) {
            "both" -> {
                // Two volume button rects + line
                drawRect(color, scale(6f, 3f), Size(5f / 24f * w, 9f / 24f * h), style = stroke)
                drawRect(color, scale(13f, 3f), Size(5f / 24f * w, 9f / 24f * h), style = stroke)
                drawLine(color, scale(5f, 21f), scale(19f, 21f), stroke.width, stroke.cap)
                drawLine(color, scale(8.5f, 14.5f), scale(8.5f, 18f), stroke.width, stroke.cap)
                drawLine(color, scale(15.5f, 14.5f), scale(15.5f, 18f), stroke.width, stroke.cap)
            }
            "up" -> {
                drawRect(color, scale(9.5f, 3f), Size(5f / 24f * w, 9f / 24f * h), style = stroke)
                val arrow = Path().apply {
                    moveTo(12f / 24f * w, 16f / 24f * h)
                    lineTo(12f / 24f * w, 21f / 24f * h)
                }
                drawPath(arrow, color, style = stroke)
                val arrowHead = Path().apply {
                    moveTo(9f / 24f * w, 18.5f / 24f * h)
                    lineTo(12f / 24f * w, 16f / 24f * h)
                    lineTo(15f / 24f * w, 18.5f / 24f * h)
                }
                drawPath(arrowHead, color, style = stroke)
            }
            else -> { // "down"
                drawRect(color, scale(9.5f, 3f), Size(5f / 24f * w, 9f / 24f * h), style = stroke)
                val arrow = Path().apply {
                    moveTo(12f / 24f * w, 16f / 24f * h)
                    lineTo(12f / 24f * w, 21f / 24f * h)
                }
                drawPath(arrow, color, style = stroke)
                val arrowHead = Path().apply {
                    moveTo(9f / 24f * w, 18.5f / 24f * h)
                    lineTo(12f / 24f * w, 21f / 24f * h)
                    lineTo(15f / 24f * w, 18.5f / 24f * h)
                }
                drawPath(arrowHead, color, style = stroke)
            }
        }
    }
}

// ── Status banner ─────────────────────────────────────────────────

@Composable
private fun StatusBanner(activeCount: Int) {
    val (icon, message) = when (activeCount) {
        0 -> Pair("⚠", "No method active — enable at least one to use Vozcribe.")
        1 -> Pair("✓", "1 of 2 methods active. Enable both for the most flexibility.")
        else -> Pair("✓", "Both methods active. Use whichever feels natural.")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, DsgLine, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(DsgBrandSft)
        ) {
            Text(icon, fontSize = 13.sp, color = DsgBrand)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            fontSize = 12.5.sp,
            color = DsgInk2,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Toggle ────────────────────────────────────────────────────────

@Composable
private fun DsgToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(
        targetValue = if (on) DsgBrand else Color(0xFF1B1410).copy(alpha = 0.12f),
        label = "track"
    )
    val thumbX by animateDpAsState(
        targetValue = if (on) 18.dp else 0.dp,
        label = "thumb"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color = trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { onChange(!on) }
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp + thumbX, top = 3.dp)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ── Horizontal rule ───────────────────────────────────────────────

@Composable
private fun HRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, DsgLine2, DsgLine2, Color.Transparent)
                )
            )
    )
}
