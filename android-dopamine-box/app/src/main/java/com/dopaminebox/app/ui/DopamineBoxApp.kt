package com.dopaminebox.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dopaminebox.app.model.FeedEvent
import com.dopaminebox.app.model.MiniGameType
import com.dopaminebox.app.ui.games.CoinFlipGame
import com.dopaminebox.app.ui.games.FlappyCoinsGame
import com.dopaminebox.app.ui.games.HigherLowerGame
import com.dopaminebox.app.ui.games.PlinkoGame
import com.dopaminebox.app.ui.theme.DopamineBorder
import com.dopaminebox.app.ui.theme.DopaminePrimary
import com.dopaminebox.app.ui.theme.DopamineTheme
import com.dopaminebox.app.ui.theme.DopamineTextPrimary
import com.dopaminebox.app.ui.theme.DopamineTextSecondary
import com.dopaminebox.app.util.Haptics
import com.dopaminebox.app.util.SoundManager
import com.dopaminebox.app.viewmodel.DopamineViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DopamineBoxApp(vm: DopamineViewModel = viewModel()) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val sounds = remember { SoundManager() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { vm.feed.size })

    var flashColor by remember { mutableStateOf(Color.Transparent) }
    var showFlash by remember { mutableStateOf(false) }
    var lastPageChangeNanos by remember { mutableLongStateOf(0L) }
    var currentPage by remember { mutableIntStateOf(0) }
    val hudScaleAnim = remember { Animatable(1f) }

    LaunchedEffect(vm.streakWarning) {
        if (vm.streakWarning) {
            Toast.makeText(context, "Streak in danger!", Toast.LENGTH_SHORT).show()
            vm.consumeStreakWarning()
        }
    }

    LaunchedEffect(vm.showJackpotCelebration) {
        if (vm.showJackpotCelebration) {
            haptics.woohoo()
            sounds.woohoo()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
        vm.ensureFeedForIndex(currentPage)

        val now = System.nanoTime()
        if (lastPageChangeNanos != 0L) {
            val deltaMs = max(1L, (now - lastPageChangeNanos) / 1_000_000)
            val pagesPerSecond = (1000f / deltaMs).coerceIn(0f, 8f)
            vm.accelerateFeed(pagesPerSecond)
            if (pagesPerSecond > 2.4f) {
                haptics.scrollFastTick()
            }
        }
        lastPageChangeNanos = now
    }

    LaunchedEffect(vm.playerState.coins) {
        if (vm.playerState.lastReward > 0) {
            hudScaleAnim.snapTo(1.09f)
            hudScaleAnim.animateTo(
                1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
    }

    DopamineTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val event = vm.feed[page]
                ReelBackground(event.gameType)
                ReelPage(
                    event = event,
                    speedMultiplier = vm.playerState.scrollSpeedMultiplier,
                    onWin = { reward ->
                        vm.onWin(reward)
                        haptics.win()
                        sounds.coin()
                        sounds.win()
                        flashColor = Color(0x4D22C55E)
                        showFlash = true
                        scope.launch {
                            delay(300)
                            showFlash = false
                        }
                    },
                    onLose = { penalty ->
                        vm.onLose(penalty)
                        haptics.lose()
                        sounds.lose()
                        flashColor = Color(0x4DEF4444)
                        showFlash = true
                        scope.launch {
                            delay(300)
                            showFlash = false
                            delay(700)
                            val next = (page + 1).coerceAtMost(vm.feed.lastIndex)
                            vm.ensureFeedForIndex(next)
                            pagerState.animateScrollToPage(next)
                        }
                    },
                    onWoohoo = {
                        haptics.woohoo()
                        sounds.woohoo()
                    },
                )
            }

            TopHud(
                coins = vm.playerState.coins,
                streak = vm.playerState.streakDays,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
                    .scale(hudScaleAnim.value),
            )

            SwipeHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )

            AnimatedVisibility(
                visible = showFlash,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().background(flashColor))
            }

            if (vm.showJackpotCelebration) {
                JackpotOverlay(
                    onDismiss = { vm.consumeJackpotAndReset() },
                )
            }
        }
    }
}

@Composable
private fun ReelBackground(type: MiniGameType) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradientShift",
    )

    val (start, end) = when (type) {
        MiniGameType.COIN_FLIP -> Color(0xFF7C3AED) to Color(0xFFDB2777)
        MiniGameType.HIGHER_LOWER -> Color(0xFF1D4ED8) to Color(0xFF06B6D4)
        MiniGameType.PLINKO -> Color(0xFF065F46) to Color(0xFF84CC16)
        MiniGameType.FLAPPY_COINS -> Color(0xFFEA580C) to Color(0xFFEAB308)
    }
    val shiftedStart = lerpColor(start, end, 0.25f * t)
    val shiftedEnd = lerpColor(end, start, 0.2f * (1f - t))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(listOf(shiftedStart, shiftedEnd))),
    )
}

@Composable
private fun TopHud(coins: Long, streak: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0x99000000))
                .border(1.dp, DopamineBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            val shimmer = rememberInfiniteTransition(label = "shimmer")
            val shimmerX by shimmer.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer-x",
            )
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    DopaminePrimary.copy(alpha = 0.55f),
                    Color.Transparent,
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerX * 400f, 0f),
                end = androidx.compose.ui.geometry.Offset(shimmerX * 400f + 240f, 90f),
            )
            Box(modifier = Modifier.matchParentSize().background(shimmerBrush).alpha(0.7f))
            Text(
                text = "💰 $${String.format("%,d", coins)}",
                fontWeight = FontWeight.Bold,
                color = DopamineTextPrimary,
                fontSize = 22.sp,
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (streak <= 0) {
                Pill("Start your streak!", listOf(Color(0xFF737373), Color(0xFF525252)))
            } else {
                Pill("🔥 $streak", listOf(Color(0xFFEF4444), Color(0xFFF97316)))
            }
        }
    }
}

@Composable
private fun Pill(text: String, colors: List<Color>) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(colors))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun ReelPage(
    event: FeedEvent,
    speedMultiplier: Float,
    onWin: (Long) -> Unit,
    onLose: (Long) -> Unit,
    onWoohoo: () -> Unit,
) {
    val emoji = when (event.gameType) {
        MiniGameType.COIN_FLIP -> "🪙"
        MiniGameType.HIGHER_LOWER -> "🃏"
        MiniGameType.PLINKO -> "⚡"
        MiniGameType.FLAPPY_COINS -> "🐦"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient fade makes top HUD readable on vibrant reel backgrounds.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x99000000), Color.Transparent),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 110.dp),
        ) {
            Text(text = emoji, fontSize = 56.sp)
            Text(
                text = event.title,
                color = DopamineTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Text(
                text = event.subtitle,
                color = DopamineTextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (event.gameType) {
                    MiniGameType.COIN_FLIP -> CoinFlipGame(speedMultiplier, onWin, onLose)
                    MiniGameType.HIGHER_LOWER -> HigherLowerGame(speedMultiplier, onWin, onLose)
                    MiniGameType.PLINKO -> PlinkoGame(speedMultiplier, onWin, onLose)
                    MiniGameType.FLAPPY_COINS -> FlappyCoinsGame(
                        speedMultiplier = speedMultiplier,
                        index = event.id.toInt(),
                        onWin = onWin,
                        onLose = onLose,
                        onWoohoo = onWoohoo,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "swipe")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipe-bob",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.offset(y = (-bob).dp)) {
        Text("↑", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Swipe", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun JackpotOverlay(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val alpha = remember { Animatable(0f) }
    val confetti = remember {
        List(90) {
            ConfettiPiece(
                x = RandomFloat.next(0f, 1f),
                y = RandomFloat.next(-0.2f, 0f),
                speed = RandomFloat.next(0.4f, 1.2f),
                color = listOf(Color(0xFFFFD700), Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFFF97316)).random(),
            )
        }
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0B0B16))
            .alpha(alpha.value),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            confetti.forEachIndexed { i, piece ->
                val px = size.width * piece.x
                val py = size.height * (piece.y + (alpha.value * piece.speed))
                drawCircle(color = piece.color, radius = 4f + (i % 5), center = androidx.compose.ui.geometry.Offset(px, py))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YOU WIN THE BOX 🎊", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
            Text(
                "Cashout reached $10,000,000",
                color = DopamineTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFF97316))))
                    .padding(horizontal = 22.dp, vertical = 14.dp)
                    .graphicsLayer { shadowElevation = 20.dp.toPx() }
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                    .clickable {
                        scope.launch {
                            alpha.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                            onDismiss()
                        }
                    },
            ) {
                Text(
                    text = "Play Again",
                    color = Color(0xFF1A1A1A),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}

private object RandomFloat {
    fun next(min: Float, max: Float): Float = ((Math.random() * (max - min)) + min).toFloat()
}

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val speed: Float,
    val color: Color,
)