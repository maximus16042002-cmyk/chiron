package com.kinesis.core.analysis.workout

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The workout generator - DECISIONS.md P3-50. Deterministic, library-only, and every rule the
 * design entry names is pinned here.
 */
class WorkoutGeneratorTest {

    private fun every(block: (PlanRequest) -> Unit) {
        for (minutes in listOf(10, 20, 30)) for (focus in Focus.entries) for (level in Level.entries) {
            for (equipment in Equipment.entries) for (seed in 1..12) {
                block(PlanRequest(minutes, focus, level, equipment, seed = seed))
            }
        }
    }

    // --- The catalogue -------------------------------------------------------------------------

    @Test
    fun the_catalogue_covers_every_library_entry_exactly_once() {
        assertEquals(ExerciseLibrary.all.map { it.id }.sorted(), WorkoutCatalogue.entries.map { it.exerciseId }.sorted())
    }

    @Test
    fun equipment_and_room_needs_are_as_the_design_entry_lists_them() {
        assertEquals(
            setOf("bicep_curl", "lateral_raise", "shoulder_press", "row", "goblet_squat"),
            WorkoutCatalogue.entries.filter { it.equipment == Equipment.DUMBBELLS }.map { it.exerciseId }.toSet(),
        )
        assertEquals(
            setOf("lateral_raise", "shoulder_press"),
            WorkoutCatalogue.entries.filter { it.needs == RoomNeed.CHAIR }.map { it.exerciseId }.toSet(),
        )
        assertEquals(setOf("wall_sit"), WorkoutCatalogue.entries.filter { it.needs == RoomNeed.WALL }.map { it.exerciseId }.toSet())
    }

    // --- Reproducible ----------------------------------------------------------------------------

    @Test
    fun the_same_request_gives_the_same_plan() {
        every { request -> assertEquals(WorkoutGenerator.generate(request), WorkoutGenerator.generate(request)) }
    }

    /**
     * One plan pinned exactly, so the JVM and JS are held to the same shuffle: a seed saved as a
     * favourite on one must rebuild the same workout everywhere (P3-50 section 6).
     */
    @Test
    fun a_pinned_plan_is_the_same_on_every_platform() {
        val plan = WorkoutGenerator.generate(PlanRequest(30, Focus.FULL_BODY, Level.INTERMEDIATE, Equipment.DUMBBELLS, seed = 2026))
        assertEquals(
            listOf("glute_bridge", "plank", "row", "reverse_lunge", "wall_sit", "lateral_raise"),
            plan.items.map { it.exerciseId },
        )
    }

    @Test
    fun a_different_seed_can_give_a_different_plan() {
        val plans = (1..30).map {
            WorkoutGenerator.generate(PlanRequest(20, Focus.FULL_BODY, Level.INTERMEDIATE, Equipment.DUMBBELLS, seed = it)).items
        }.toSet()
        assertTrue(plans.size > 5, "30 seeds gave only ${plans.size} distinct plans")
    }

    // --- Within the rules ------------------------------------------------------------------------

    @Test
    fun every_plan_fits_its_time_and_its_estimate_is_its_own() {
        every { request ->
            val plan = WorkoutGenerator.generate(request)
            assertTrue(plan.estimatedSeconds <= request.minutes * 60, "$request took ${plan.estimatedSeconds} s")
            assertEquals(WorkoutGenerator.estimateSeconds(plan.items), plan.estimatedSeconds)
        }
    }

    @Test
    fun no_equipment_means_no_dumbbell_exercise() {
        every { request ->
            if (request.equipment == Equipment.NONE) {
                val plan = WorkoutGenerator.generate(request)
                assertTrue(plan.items.none { WorkoutCatalogue.of(it.exerciseId).equipment == Equipment.DUMBBELLS }, plan.toString())
            }
        }
    }

    @Test
    fun a_focus_keeps_to_its_body_area() {
        every { request ->
            val area = when (request.focus) {
                Focus.UPPER -> BodyArea.UPPER
                Focus.LOWER -> BodyArea.LOWER
                Focus.CORE -> BodyArea.CORE
                Focus.FULL_BODY -> null
            }
            if (area != null) {
                val plan = WorkoutGenerator.generate(request)
                assertTrue(plan.items.all { WorkoutCatalogue.of(it.exerciseId).area == area }, plan.toString())
            }
        }
    }

    @Test
    fun no_exercise_appears_twice_and_no_variant_beside_its_parent() {
        every { request ->
            val ids = WorkoutGenerator.generate(request).items.map { it.exerciseId }
            assertEquals(ids.size, ids.toSet().size, ids.toString())
            for (variant in ExerciseLibrary.variants) {
                assertTrue(!(variant.id in ids && variant.parent.id in ids), "${variant.id} beside its parent: $ids")
            }
        }
    }

    @Test
    fun avoided_exercises_are_left_out() {
        val avoid = setOf("squat", "pushup", "plank")
        for (seed in 1..20) {
            val plan = WorkoutGenerator.generate(PlanRequest(30, Focus.FULL_BODY, Level.ADVANCED, Equipment.DUMBBELLS, avoid, seed))
            assertTrue(plan.items.none { it.exerciseId in avoid }, plan.toString())
        }
    }

    /** Grouped by camera view: each view appears in one run, so the phone moves views-minus-one times. */
    @Test
    fun exercises_are_grouped_by_camera_view() {
        every { request ->
            val views = WorkoutGenerator.generate(request).items.map { WorkoutCatalogue.viewOf(it.exerciseId) }
            val changes = views.zipWithNext().count { (a, b) -> a != b }
            assertEquals(maxOf(views.toSet().size - 1, 0), changes, views.toString())
        }
    }

    // --- Targets ---------------------------------------------------------------------------------

    @Test
    fun targets_follow_the_level_and_how_the_exercise_is_counted() {
        for (level in Level.entries) {
            val targets = WorkoutGenerator.targets(level)
            val plan = WorkoutGenerator.generate(PlanRequest(30, Focus.FULL_BODY, level, Equipment.DUMBBELLS, seed = 3))
            for (item in plan.items) {
                val profile = ExerciseLibrary.all.single { it.id == item.exerciseId }
                assertEquals(targets.sets, item.sets)
                assertEquals(targets.restSeconds, item.restSeconds)
                if (profile is IsometricExercise) {
                    assertEquals(targets.holdSeconds, item.targetHoldSeconds)
                    assertNull(item.targetReps)
                } else {
                    assertEquals(targets.reps, item.targetReps)
                    assertNull(item.targetHoldSeconds)
                }
                assertEquals(item.exerciseId in setOf("bicep_curl", "knee_raise", "hip_abduction"), item.perSide, item.exerciseId)
            }
        }
        assertEquals(Targets(sets = 2, reps = 8, holdSeconds = 20, restSeconds = 60), WorkoutGenerator.targets(Level.BEGINNER))
        assertEquals(Targets(sets = 3, reps = 10, holdSeconds = 30, restSeconds = 60), WorkoutGenerator.targets(Level.INTERMEDIATE))
        assertEquals(Targets(sets = 3, reps = 12, holdSeconds = 45, restSeconds = 45), WorkoutGenerator.targets(Level.ADVANCED))
    }

    // --- Enough to do --------------------------------------------------------------------------

    @Test
    fun a_plan_is_never_empty_and_more_time_holds_more() {
        every { request -> assertTrue(WorkoutGenerator.generate(request).items.isNotEmpty(), request.toString()) }
        val long = WorkoutGenerator.generate(PlanRequest(30, Focus.FULL_BODY, Level.BEGINNER, Equipment.DUMBBELLS, seed = 1))
        assertTrue(long.items.size >= 4, long.toString())
    }

    /** The push-up is the only upper-body entry that needs nothing: the honest plan is push-ups. */
    @Test
    fun upper_body_with_no_equipment_is_the_push_up() {
        val plan = WorkoutGenerator.generate(PlanRequest(20, Focus.UPPER, Level.INTERMEDIATE, Equipment.NONE, seed = 7))
        assertEquals(listOf("pushup"), plan.items.map { it.exerciseId })
    }

    @Test
    fun each_item_says_what_the_room_needs() {
        val plan = WorkoutGenerator.generate(PlanRequest(30, Focus.UPPER, Level.BEGINNER, Equipment.DUMBBELLS, seed = 2))
        for (item in plan.items) assertEquals(WorkoutCatalogue.of(item.exerciseId).needs, item.needs)
        assertNotNull(plan.items.firstOrNull { it.needs == RoomNeed.CHAIR }, plan.toString())
    }

    // --- Guided mode ---------------------------------------------------------------------------

    @Test
    fun guided_steps_are_every_set_of_every_exercise_in_order() {
        val plan = WorkoutGenerator.generate(PlanRequest(20, Focus.FULL_BODY, Level.INTERMEDIATE, Equipment.NONE, seed = 5))
        val steps = GuidedWorkout.steps(plan)
        assertEquals(plan.items.sumOf { it.sets }, steps.size)
        assertEquals(
            plan.items.flatMap { item -> (1..item.sets).map { item.exerciseId to it } },
            steps.map { it.item.exerciseId to it.setNumber },
        )
        assertTrue(steps.last().isLastStep)
        assertEquals(1, steps.count { it.isLastStep })
        for (step in steps) assertEquals(step.setNumber == step.item.sets, step.isLastSetOfExercise)
    }
}
