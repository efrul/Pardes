package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.data.TempleCountdownData
import io.github.kdroidfilter.seforimapp.android.data.computeTempleCountdown
import io.github.kdroidfilter.seforimapp.android.data.millisUntilNextJerusalemSunset
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Port of desktop 0.6.7 features/home/widgets/temple/TempleCountdownWidget.kt: years, months and days since the
// Temple's destruction (the Hebrew day turning at Jerusalem's sunset), over the burning Temple. Desktop plays the
// animated WebP frame by frame with Skia; Android plays it with AnimatedImageDrawable, and shows the still picture on a
// low-RAM device.

private val ACCENT_START = Color(0xFFFF6B35)
private val ACCENT_END = Color(0xFFFFAA70)

/** Years, months and days since the Temple's destruction, over the burning Temple. */
internal object TempleCountdownWidget : HomeWidget {
    override val id = "temple_countdown"
    override val title = "חורבן הבית"
    override val defaultSpan = CellSpan(6, 3)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(12, 6)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = TempleDestructionCountdownCard(modifier, animated = !PlatformInfo.isLowEnd)

    // One animation decoder at a time: the gallery shows the still picture
    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = TempleDestructionCountdownCard(modifier, animated = false)
}

/**
 * The animated WebP, decoded off the UI thread. Each new frame redraws the whole window, so it plays only while it can
 * be seen: the card on screen, its tab in front with no overlay over it, the activity resumed. Otherwise it holds its
 * frame (desktop holds it while the tab is hidden). With system animations off or in battery saver it is the still
 * picture, and nothing is decoded.
 */
@Composable
private fun AnimatedTempleBackground(modifier: Modifier = Modifier) {
    // ImageDecoder and AnimatedImageDrawable are API 28: Android 8 shows the still picture, as a low-RAM device does
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        StillTemple(modifier)
        return
    }
    if (!rememberMotionAllowed()) {
        StillTemple(modifier)
        return
    }
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, R.raw.temple_jerusalem_in_fire)) }.getOrNull()
            }
    }
    val current = drawable
    if (current == null) {
        StillTemple(modifier)
        return
    }
    val front = isTabInFront()
    val appVm = LocalAppViewModel.current
    val overlayShown by remember(appVm) { appVm?.state?.map { it.overlays.isNotEmpty() }?.distinctUntilChanged() ?: flowOf(false) }
        .collectAsState(appVm?.state?.value?.overlays?.isNotEmpty() == true)
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    // Hidden tabs are not placed, so this only turns false on a scroll or a resize; the checks above cover the rest
    var onScreen by remember { mutableStateOf(false) }
    val play = onScreen && front && !overlayShown && resumed
    val animation = current as? AnimatedImageDrawable
    DisposableEffect(animation, play) {
        if (play) animation?.start() else animation?.stop()
        onDispose { animation?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { view -> if (view.drawable !== current) view.setImageDrawable(current) },
        modifier = modifier.onGloballyPositioned { onScreen = it.isVisibleInRoot() },
    )
}

/** Some of it is inside the window: its bounds are clipped by every clipping parent (the page's scroll, the card). */
private fun LayoutCoordinates.isVisibleInRoot(): Boolean {
    if (!isAttached) return false
    val bounds = boundsInRoot()
    val root = findRootCoordinates().size
    return bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f && bounds.left < root.width && bounds.top < root.height
}

/**
 * False with system animations off (animator duration scale 0: "Remove animations", developer options) or in battery
 * saver; follows both as they change.
 */
@Composable
private fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var animatorsOn by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var powerSave by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    powerSave = power?.isPowerSaveMode == true
                }
            }
        // ContextCompat: RECEIVER_NOT_EXPORTED is API 33, and only the compat call keeps it unexported before it
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val stopFollowingScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) followAnimatorScale { animatorsOn = it } else null
        // A change made while the app was away
        animatorsOn = ValueAnimator.areAnimatorsEnabled()
        powerSave = power?.isPowerSaveMode == true
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            stopFollowingScale?.invoke()
        }
    }
    // Below Android 13 nothing says when the scale changes: it is read again as the activity resumes (it is changed in
    // Settings, away from the app)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { animatorsOn = ValueAnimator.areAnimatorsEnabled() }
    }
    return animatorsOn && !powerSave
}

/** Follows the animator duration scale ([onChange]: animators on) until the function returned is called. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun followAnimatorScale(onChange: (Boolean) -> Unit): () -> Unit {
    // Held weakly by ValueAnimator: the function returned keeps it alive
    val listener = ValueAnimator.DurationScaleChangeListener { scale -> onChange(scale != 0f) }
    ValueAnimator.registerDurationScaleChangeListener(listener)
    return { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
}

@Composable
private fun StillTemple(modifier: Modifier) {
    Image(painterResource(R.drawable.temple_small), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
private fun TempleDestructionCountdownCard(
    modifier: Modifier = Modifier,
    animated: Boolean,
) {
    // The initial value too off the UI thread: as produceState's argument it was computed again at every recomposition
    val countdown by produceState<TempleCountdownData?>(null) {
        while (true) {
            value = withContext(Dispatchers.Default) { computeTempleCountdown() }
            delay(millisUntilNextJerusalemSunset(System.currentTimeMillis()))
        }
    }
    val countdownData = countdown ?: return WidgetCardLoading(modifier)
    val countdownItems =
        listOf(
            countdownData.years to "שנים",
            countdownData.months to "חודשים",
            countdownData.days to "ימים",
        )

    WidgetCard(modifier) {
        if (animated) AnimatedTempleBackground(Modifier.matchParentSize()) else StillTemple(Modifier.matchParentSize())
        // Dark overlay
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.45f)))))
        // Content — same layout as DayMomentCard
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.Start) {
            // Title row with GradientDot — matches AdaptiveCardTitle pattern
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(11.dp)
                        .align(Alignment.CenterStart)
                        .background(brush = Brush.radialGradient(listOf(ACCENT_START, ACCENT_END)), shape = CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                )
                Text(
                    text = "זמן שחלף מאז חורבן בית המקדש השני",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            // Values — aligned to bottom
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                // A narrow card can't hold three roomy units: tighter ones keep "1956" on one line
                val compact = maxWidth < 240.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    countdownItems.forEach { (value, label) -> CountdownUnit(value, label, compact) }
                }
            }
            // Subtitle at bottom
            Spacer(Modifier.height(8.dp))
            Text(
                text = "והראנו בבנינו ושמחנו בתיקונו, והשב שכינתך לתוכו.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.60f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    compact: Boolean,
) {
    val glassShape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .clip(glassShape)
                .background(Color.Black.copy(alpha = 0.35f), glassShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), glassShape)
                .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 6.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(value.toString(), fontSize = if (compact) 18.sp else 22.sp, maxLines = 1, color = Color.White, textAlign = TextAlign.Center)
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f), textAlign = TextAlign.Center, maxLines = 1)
    }
}
