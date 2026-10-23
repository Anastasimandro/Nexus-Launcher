package app.lawnchair.animation

import app.lawnchair.animation.ChangeSummary.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSessionCoreTest {

    private class TestLayer(override val taskId: Int) : SessionCore.Layer

    private fun change(task: Int, kind: Kind, pkg: String? = "pkg.$task") = ChangeSummary(task, pkg, kind)

    private val noPending: (String?) -> Boolean = { false }
    private val anyPending: (String?) -> Boolean = { true }

    // ---- MergePlanner: los escenarios de interrupción

    @Test
    fun homeWhileOpeningReversesTheWindow() {
        val d = MergePlanner.decide(
            listOf(10),
            listOf(change(10, Kind.CLOSING), change(1, Kind.HOME_OPENING), change(0, Kind.OTHER)),
            noPending,
        )
        assertEquals(MergeDecision.Reverse(10), d)
    }

    @Test
    fun closingWithoutLauncherUnderneathIsRejected() {
        // Volver a otra app (no al inicio): el movimiento hacia el icono no tendría sentido.
        val d = MergePlanner.decide(
            listOf(10),
            listOf(change(10, Kind.CLOSING), change(20, Kind.OPENING)),
            anyPending,
        )
        assertEquals(MergeDecision.Reject, d)
    }

    @Test
    fun reopeningTheSameAppWhileItCloses() {
        val d = MergePlanner.decide(listOf(10), listOf(change(10, Kind.OPENING)), noPending)
        assertEquals(MergeDecision.Reopen(10), d)
    }

    @Test
    fun openingAnotherAppWithAPendingLaunchIsAdopted() {
        val b = change(20, Kind.OPENING, "pkg.b")
        val d = MergePlanner.decide(listOf(10), listOf(b, change(1, Kind.OTHER)), { it == "pkg.b" })
        assertEquals(MergeDecision.AdoptOpening(b), d)
    }

    @Test
    fun openingAnotherAppWithoutAPendingLaunchIsRejected() {
        val d = MergePlanner.decide(listOf(10), listOf(change(20, Kind.OPENING)), noPending)
        assertEquals(MergeDecision.Reject, d)
    }

    @Test
    fun pendingLaunchForAnotherPackageIsNotAdopted() {
        val d = MergePlanner.decide(listOf(10), listOf(change(20, Kind.OPENING, "pkg.b")), { it == "pkg.x" })
        assertEquals(MergeDecision.Reject, d)
    }

    @Test
    fun twoAppsOpeningAtOnceIsRejected() {
        val d = MergePlanner.decide(
            listOf(10),
            listOf(change(20, Kind.OPENING), change(30, Kind.OPENING)),
            anyPending,
        )
        assertEquals(MergeDecision.Reject, d)
    }

    @Test
    fun nothingToAnimateMeansReject() {
        assertEquals(MergeDecision.Reject, MergePlanner.decide(emptyList(), listOf(change(10, Kind.CLOSING)), anyPending))
        assertEquals(MergeDecision.Reject, MergePlanner.decide(listOf(10), emptyList(), anyPending))
        assertEquals(
            MergeDecision.Reject,
            MergePlanner.decide(listOf(10), listOf(change(1, Kind.HOME_OPENING)), anyPending),
        )
    }

    @Test
    fun closingAnUnrelatedTaskIsRejected() {
        val d = MergePlanner.decide(
            listOf(10),
            listOf(change(99, Kind.CLOSING), change(1, Kind.HOME_OPENING)),
            noPending,
        )
        assertEquals(MergeDecision.Reject, d)
    }

    // ---- SessionCore: la transición original termina una sola vez

    @Test
    fun finishesOnceWhenTheOnlyLayerSettles() {
        var calls = 0
        var forcedFlag = true
        val core = SessionCore<TestLayer> { forced ->
            calls++
            forcedFlag = forced
        }
        val a = TestLayer(1)
        assertTrue(core.add(a))
        core.settled(a)
        core.settled(a)
        assertEquals(1, calls)
        assertFalse(forcedFlag)
        assertTrue(core.finished)
    }

    @Test
    fun waitsForEveryLayerBeforeFinishing() {
        var calls = 0
        val core = SessionCore<TestLayer> { calls++ }
        val a = TestLayer(1)
        val b = TestLayer(2)
        core.add(a)
        core.add(b)
        core.settled(a)
        assertEquals(0, calls)
        core.settled(b)
        assertEquals(1, calls)
    }

    @Test
    fun aLayerAddedWhileAnotherIsStillRunningKeepsTheTransitionOpen() {
        // Abrir otra app mientras la primera aún vuelve al icono: la primera termina pero la
        // transición sigue viva hasta que acabe la segunda.
        var calls = 0
        val core = SessionCore<TestLayer> { calls++ }
        val a = TestLayer(1)
        val b = TestLayer(2)
        core.add(a)
        core.add(b)
        core.settled(a)
        assertFalse(core.finished)
        assertEquals(b, core.find(2))
        core.settled(b)
        assertEquals(1, calls)
    }

    @Test
    fun cannotAddAfterFinishing() {
        val core = SessionCore<TestLayer> { }
        val a = TestLayer(1)
        core.add(a)
        core.settled(a)
        assertFalse(core.add(TestLayer(2)))
    }

    @Test
    fun forcedFinishIsIdempotentAndIgnoresLateSettles() {
        var calls = 0
        var forcedFlag = false
        val core = SessionCore<TestLayer> { forced ->
            calls++
            forcedFlag = forced
        }
        val a = TestLayer(1)
        core.add(a)
        core.forceFinish()
        core.forceFinish()
        core.settled(a)
        assertEquals(1, calls)
        assertTrue(forcedFlag)
        assertFalse(core.add(TestLayer(2)))
    }

    @Test
    fun settlingAnUnknownLayerDoesNothing() {
        var calls = 0
        val core = SessionCore<TestLayer> { calls++ }
        core.add(TestLayer(1))
        core.settled(TestLayer(1))
        assertEquals(0, calls)
        assertFalse(core.finished)
    }

    @Test
    fun emptySessionOnlyFinishesWhenForced() {
        var calls = 0
        val core = SessionCore<TestLayer> { calls++ }
        assertEquals(0, calls)
        core.forceFinish()
        assertEquals(1, calls)
    }
}
