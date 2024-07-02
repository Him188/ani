package me.him188.ani.danmaku.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.danmaku.ui.DanmakuRegexFilterConfig
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * @see DanmakuCollectionAlgorithmTest
 */
internal class TimeBasedDanmakuCollectionTest {
    suspend fun create(
        sequence: Sequence<Danmaku>,
        shiftMillis: Long = 0,
    ): DanmakuCollection = TimeBasedDanmakuSession.create(
        sequence, shiftMillis,
        coroutineContext = currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext,
    )

    @Test
    fun empty() = runTest {
        create(emptySequence())
    }

    @Test
    fun `before all`() = runTest {
        val instance = create(
            sequenceOf(
                dummyDanmaku(1.0),
                dummyDanmaku(2.0),
            ),
        )
        val list = instance.at(flowOf(0.seconds), danmakuRegexFilterConfig = flowOf(DanmakuRegexFilterConfig())).events.toList()
        assertEquals(0, list.size)
    }

    private fun dummyDanmaku(timeSecs: Double, text: String = "$timeSecs") =
        dummyDanmaku((timeSecs * 1000).toLong(), text)

    private fun dummyDanmaku(timeMillis: Long, text: String = "$timeMillis") =
        Danmaku(text, "dummy", timeMillis, text, DanmakuLocation.NORMAL, text, 0)
}