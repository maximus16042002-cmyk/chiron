package com.kinesis.tools.mmfit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The subject map and the splits as the harness reads them - DECISIONS.md P3-30. `test_subjects.py`
 * checks the Python side, and that the JSON copy is current.
 */
class SubjectsTest {

    private val subjects = Subjects.load()

    @Test
    fun no_subject_is_in_both_the_development_and_the_held_out_workouts() {
        assertEquals(emptyMap(), subjects.leaks("development", "held-out"))
    }

    @Test
    fun the_map_has_the_papers_counts() {
        assertEquals(listOf(6, 6, 2, 1, 1, 1, 1, 1, 1, 1), subjects.subjects.values.map { it.size }.sortedDescending())
        assertEquals(subjects.paperWorkoutsPerSubject, subjects.subjects.values.map { it.size }.sortedDescending())
    }

    @Test
    fun the_split_covers_every_workout_once() {
        val all = subjects.split("development") + subjects.split("held-out")
        assertEquals((0..20).map { "w%02d".format(it) }, all.sorted())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun the_held_out_workouts_are_the_six_people_never_seen() {
        assertEquals(listOf("w12", "w13", "w16", "w17", "w18", "w20"), subjects.split("held-out"))
        assertEquals(setOf("A", "B", "C", "S19"), subjects.split("development").map(subjects::subjectOf).toSet())
    }

    /** The check itself, against a split known to leak. */
    @Test
    fun the_check_finds_p3_27s_split_leaky_through_a_and_b() {
        val found = subjects.leaks("p3-27-development", "p3-27-held-out")
        assertEquals(setOf("A", "B"), found.keys)
        assertEquals(listOf("w01", "w03", "w06") to listOf("w08", "w10", "w14"), found["A"])
    }
}
