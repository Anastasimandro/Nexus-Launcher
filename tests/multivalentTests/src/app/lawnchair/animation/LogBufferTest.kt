package app.lawnchair.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {

    private fun line(n: Int) = LogLine(n.toLong(), 'I', "T", "main", "m$n")

    @Test
    fun drainReturnsEverythingInOrderAndEmptiesTheBuffer() {
        val buffer = LogBuffer(10)
        for (n in 1..3) buffer.offer(line(n))
        val out = ArrayList<LogLine>()
        assertEquals(0, buffer.drain(out))
        assertEquals(listOf("m1", "m2", "m3"), out.map { it.message })
        assertEquals(0, buffer.size)
    }

    @Test
    fun dropsTheOldestWhenFullAndCountsWhatWasLost() {
        val buffer = LogBuffer(3)
        for (n in 1..5) buffer.offer(line(n))
        val out = ArrayList<LogLine>()
        assertEquals(2, buffer.drain(out))
        assertEquals(listOf("m3", "m4", "m5"), out.map { it.message })
    }

    @Test
    fun lostCounterResetsAfterEachDrain() {
        val buffer = LogBuffer(1)
        buffer.offer(line(1))
        buffer.offer(line(2))
        val out = ArrayList<LogLine>()
        assertEquals(1, buffer.drain(out))
        buffer.offer(line(3))
        out.clear()
        assertEquals(0, buffer.drain(out))
        assertEquals(1, out.size)
    }

    @Test
    fun concurrentWritersNeverExceedTheCapacity() {
        val buffer = LogBuffer(50)
        val threads = (1..4).map { t ->
            Thread { for (n in 1..2000) buffer.offer(line(t * 10_000 + n)) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(buffer.size <= 50)
        val out = ArrayList<LogLine>()
        val lost = buffer.drain(out)
        assertEquals(8000, out.size + lost)
    }
}
