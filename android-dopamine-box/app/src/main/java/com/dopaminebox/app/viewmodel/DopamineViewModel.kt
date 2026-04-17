package com.dopaminebox.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.dopaminebox.app.model.FeedEvent
import com.dopaminebox.app.model.MiniGameType
import com.dopaminebox.app.model.PlayerState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

class DopamineViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dopamine_box", Context.MODE_PRIVATE)

    var playerState by mutableStateOf(PlayerState())
        private set

    var feed by mutableStateOf(seedFeed())
        private set

    var streakWarning by mutableStateOf(false)
        private set

    var showJackpotCelebration by mutableStateOf(false)
        private set

    init {
        onAppOpened()
    }

    fun accelerateFeed(swipesPerSecond: Float) {
        playerState = playerState.copy(
            scrollSpeedMultiplier = (1f + swipesPerSecond / 4f).coerceIn(1f, 3.5f)
        )
    }

    fun onWin(reward: Long) {
        val updatedCoins = playerState.coins + reward
        playerState = playerState.copy(
            coins = updatedCoins,
            lastReward = reward,
        )
        if (updatedCoins >= 10_000_000L) {
            showJackpotCelebration = true
        }
        maybeAppendFeed()
    }

    fun onLose(penalty: Long) {
        val nextCoins = max(0L, playerState.coins - penalty)
        playerState = playerState.copy(coins = nextCoins, lastReward = -penalty)
        maybeAppendFeed(extraDepth = 4)
    }

    fun ensureFeedForIndex(targetIndex: Int) {
        if (targetIndex < 0) return
        val remainingAhead = feed.lastIndex - targetIndex
        if (remainingAhead >= 10) return
        val missing = (10 - remainingAhead).coerceAtLeast(2)
        maybeAppendFeed(extraDepth = missing)
    }

    fun consumeStreakWarning() {
        streakWarning = false
    }

    fun consumeJackpotAndReset() {
        showJackpotCelebration = false
        playerState = playerState.copy(coins = 1_000, lastReward = 0)
    }

    fun resetRun() {
        playerState = playerState.copy(coins = 1_000, lastReward = 0, scrollSpeedMultiplier = 1f)
        feed = seedFeed()
    }

    private fun onAppOpened() {
        val today = LocalDate.now()
        val lastOpenRaw = prefs.getString("last_open", null)
        val storedStreak = prefs.getInt("streak_days", 0)

        val (nextStreak, inDanger) = if (lastOpenRaw == null) {
            1 to false
        } else {
            val lastOpen = runCatching { LocalDate.parse(lastOpenRaw) }.getOrNull()
            if (lastOpen == null) {
                1 to false
            } else {
                val daysDelta = ChronoUnit.DAYS.between(lastOpen, today)
                when {
                    daysDelta <= 0L -> storedStreak.coerceAtLeast(1) to false
                    daysDelta == 1L -> (storedStreak + 1).coerceAtLeast(1) to false
                    else -> 0 to true
                }
            }
        }

        playerState = playerState.copy(streakDays = nextStreak)
        streakWarning = inDanger
        prefs.edit()
            .putString("last_open", today.toString())
            .putInt("streak_days", nextStreak)
            .apply()
    }

    private fun maybeAppendFeed(extraDepth: Int = 2) {
        val nextId = (feed.lastOrNull()?.id ?: 0L) + 1
        val additions = List(extraDepth) { index ->
            val types = MiniGameType.values()
            val game = types[(nextId.toInt() + index) % types.size]
            FeedEvent(
                id = nextId + index,
                title = when (game) {
                    MiniGameType.COIN_FLIP -> "Coin Flip Rush"
                    MiniGameType.HIGHER_LOWER -> "Higher or Lower"
                    MiniGameType.PLINKO -> "Plinko Drop"
                    MiniGameType.FLAPPY_COINS -> "Flappy Coins"
                },
                subtitle = "Win big and keep scrolling",
                gameType = game,
            )
        }
        feed = feed + additions
    }

    private fun seedFeed(): List<FeedEvent> = List(12) { i ->
        val types = MiniGameType.values()
        val game = types[i % types.size]
        FeedEvent(
            id = i.toLong(),
            title = "Dopamine Round ${i + 1}",
            subtitle = "One tap away from a bigger hit",
            gameType = game,
        )
    }
}