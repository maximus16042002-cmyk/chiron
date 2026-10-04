package com.kinesis.core.analysis.workout

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile

enum class BodyArea { LOWER, UPPER, CORE }

enum class Equipment { NONE, DUMBBELLS }

enum class Focus { FULL_BODY, UPPER, LOWER, CORE }

enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }

/** What the room needs beyond the floor: said on the plan, never used to filter it. */
enum class RoomNeed { CHAIR, WALL }

/** One library entry, as a planner sees it - DECISIONS.md P3-50. */
data class CatalogueEntry(val exerciseId: String, val area: BodyArea, val equipment: Equipment, val needs: RoomNeed?)

/**
 * Every library entry's body area, equipment and room need - DECISIONS.md P3-50. A test holds it
 * to exactly the 17 entries of `ExerciseLibrary.all`.
 */
object WorkoutCatalogue {

    val entries: List<CatalogueEntry> = listOf(
        CatalogueEntry("squat", BodyArea.LOWER, Equipment.NONE, null),
        CatalogueEntry("goblet_squat", BodyArea.LOWER, Equipment.DUMBBELLS, null),
        CatalogueEntry("lunge", BodyArea.LOWER, Equipment.NONE, null),
        CatalogueEntry("reverse_lunge", BodyArea.LOWER, Equipment.NONE, null),
        CatalogueEntry("glute_bridge", BodyArea.LOWER, Equipment.NONE, null),
        CatalogueEntry("hip_abduction", BodyArea.LOWER, Equipment.NONE, null),
        CatalogueEntry("wall_sit", BodyArea.LOWER, Equipment.NONE, RoomNeed.WALL),
        CatalogueEntry("pushup", BodyArea.UPPER, Equipment.NONE, null),
        CatalogueEntry("bicep_curl", BodyArea.UPPER, Equipment.DUMBBELLS, null),
        CatalogueEntry("lateral_raise", BodyArea.UPPER, Equipment.DUMBBELLS, RoomNeed.CHAIR),
        CatalogueEntry("shoulder_press", BodyArea.UPPER, Equipment.DUMBBELLS, RoomNeed.CHAIR),
        CatalogueEntry("row", BodyArea.UPPER, Equipment.DUMBBELLS, null),
        CatalogueEntry("plank", BodyArea.CORE, Equipment.NONE, null),
        CatalogueEntry("side_plank", BodyArea.CORE, Equipment.NONE, null),
        CatalogueEntry("situp", BodyArea.CORE, Equipment.NONE, null),
        CatalogueEntry("leg_raise", BodyArea.CORE, Equipment.NONE, null),
        CatalogueEntry("knee_raise", BodyArea.CORE, Equipment.NONE, null),
    )

    private val byId = entries.associateBy { it.exerciseId }

    fun of(exerciseId: String): CatalogueEntry = byId[exerciseId] ?: error("no catalogue entry for " + exerciseId)

    fun profileOf(exerciseId: String): ExerciseProfile = ExerciseLibrary.all.single { it.id == exerciseId }

    fun viewOf(exerciseId: String): CameraView = profileOf(exerciseId).requiredView
}

/** What the plan asks of one level - the app's own suggestions, not a source's (P3-50). */
data class Targets(val sets: Int, val reps: Int, val holdSeconds: Int, val restSeconds: Int)

data class PlanRequest(
    val minutes: Int,
    val focus: Focus,
    val level: Level,
    val equipment: Equipment,
    val avoid: Set<String> = emptySet(),
    val seed: Int,
)

/**
 * One exercise in a plan. Exactly one of [targetReps] and [targetHoldSeconds] is set; [perSide]
 * says a repetition target counts each side, as an exercise counted one limb at a time is (P3-1).
 */
data class PlanItem(
    val exerciseId: String,
    val sets: Int,
    val targetReps: Int?,
    val targetHoldSeconds: Int?,
    val perSide: Boolean,
    val restSeconds: Int,
    val needs: RoomNeed?,
)

data class WorkoutPlan(val request: PlanRequest, val items: List<PlanItem>, val estimatedSeconds: Int) {
    /** Rounded up: "about 18 min". */
    val estimatedMinutes: Int get() = (estimatedSeconds + 59) / 60
}

/**
 * The workout generator - DECISIONS.md P3-50. Deterministic and library-only: no ML (CLAUDE.md
 * section 12), no history, no network. The same request gives the same plan; the seed is what
 * "shuffle" changes.
 */
object WorkoutGenerator {

    /** P3-50's estimate: assumptions, named. */
    const val SECONDS_PER_REPETITION: Int = 3
    const val SETUP_SECONDS: Int = 30
    const val VIEW_CHANGE_SECONDS: Int = 30

    fun targets(level: Level): Targets = when (level) {
        Level.BEGINNER -> Targets(sets = 2, reps = 8, holdSeconds = 20, restSeconds = 60)
        Level.INTERMEDIATE -> Targets(sets = 3, reps = 10, holdSeconds = 30, restSeconds = 60)
        Level.ADVANCED -> Targets(sets = 3, reps = 12, holdSeconds = 45, restSeconds = 45)
    }

    fun generate(request: PlanRequest): WorkoutPlan {
        require(request.minutes > 0) { "a plan needs some time, got " + request.minutes }
        val budget = request.minutes * 60
        val targets = targets(request.level)

        val candidates = WorkoutCatalogue.entries
            .filter { inFocus(it.area, request.focus) }
            .filter { request.equipment == Equipment.DUMBBELLS || it.equipment == Equipment.NONE }
            .filter { it.exerciseId !in request.avoid }
            .map { it.exerciseId }
        val order = shuffled(candidates, request.seed)

        var chosen = emptyList<PlanItem>()
        for (id in order) {
            if (chosen.any { sameMovement(it.exerciseId, id) }) continue
            val tentative = groupedByView(chosen + itemFor(id, targets))
            if (estimateSeconds(tentative) <= budget) chosen = tentative
        }
        return WorkoutPlan(request, chosen, estimateSeconds(chosen))
    }

    /** P3-50's estimate of how long [items] take, in the order given. */
    fun estimateSeconds(items: List<PlanItem>): Int {
        if (items.isEmpty()) return 0
        var total = 0
        var previousView: CameraView? = null
        for (item in items) {
            val view = WorkoutCatalogue.viewOf(item.exerciseId)
            total += SETUP_SECONDS
            if (previousView != null && view != previousView) total += VIEW_CHANGE_SECONDS
            previousView = view
            val work = item.targetHoldSeconds
                ?: ((item.targetReps ?: 0) * SECONDS_PER_REPETITION * if (item.perSide) 2 else 1)
            total += item.sets * (work + item.restSeconds)
        }
        // No rest after the workout's last set.
        return total - items.last().restSeconds
    }

    private fun itemFor(id: String, targets: Targets): PlanItem {
        val profile = WorkoutCatalogue.profileOf(id)
        val hold = profile is IsometricExercise
        val perSide = profile is RepetitionExercise && profile.repetitionSemantics == RepetitionSemantics.PER_LIMB
        return PlanItem(
            exerciseId = id,
            sets = targets.sets,
            targetReps = if (hold) null else targets.reps,
            targetHoldSeconds = if (hold) targets.holdSeconds else null,
            perSide = perSide,
            restSeconds = targets.restSeconds,
            needs = WorkoutCatalogue.of(id).needs,
        )
    }

    private fun inFocus(area: BodyArea, focus: Focus): Boolean = when (focus) {
        Focus.FULL_BODY -> true
        Focus.UPPER -> area == BodyArea.UPPER
        Focus.LOWER -> area == BodyArea.LOWER
        Focus.CORE -> area == BodyArea.CORE
    }

    /** A variant and its parent are the same movement (CONTRACTS.md section 3). */
    private fun sameMovement(a: String, b: String): Boolean = rootOf(a) == rootOf(b)

    private fun rootOf(id: String): String =
        (WorkoutCatalogue.profileOf(id) as? ExerciseVariant)?.parent?.id ?: id

    /** Views in the order they first appear; within a view, the order chosen. */
    private fun groupedByView(items: List<PlanItem>): List<PlanItem> {
        val views = items.map { WorkoutCatalogue.viewOf(it.exerciseId) }.distinct()
        return views.flatMap { view -> items.filter { WorkoutCatalogue.viewOf(it.exerciseId) == view } }
    }

    /** Fisher–Yates with a 32-bit xorshift on `Int`, so the JVM and JS shuffle alike. */
    private fun shuffled(ids: List<String>, seed: Int): List<String> {
        val out = ids.toMutableList()
        var state = if (seed == 0) GOLDEN else seed
        for (i in out.indices.reversed()) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            val j = (state ushr 1) % (i + 1)
            val swap = out[i]
            out[i] = out[j]
            out[j] = swap
        }
        return out
    }

    private const val GOLDEN: Int = -0x61c88647
}

/** One set of a guided workout - DECISIONS.md P3-50. */
data class GuidedStep(val item: PlanItem, val setNumber: Int, val isLastSetOfExercise: Boolean, val isLastStep: Boolean)

/** Guided mode's sequence: every set of every exercise, in the plan's order. */
object GuidedWorkout {

    fun steps(plan: WorkoutPlan): List<GuidedStep> {
        val total = plan.items.sumOf { it.sets }
        var index = 0
        return plan.items.flatMap { item ->
            (1..item.sets).map { set ->
                index++
                GuidedStep(item, set, isLastSetOfExercise = set == item.sets, isLastStep = index == total)
            }
        }
    }
}
