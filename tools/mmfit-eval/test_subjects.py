"""The subject map and the splits - DECISIONS.md P3-30.

Run from the repository root: python -m unittest tools/mmfit-eval/test_subjects.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import subjects  # noqa: E402


class SubjectMapTest(unittest.TestCase):

    def test_no_subject_is_in_both_the_development_and_the_held_out_workouts(self):
        self.assertEqual({}, subjects.leaks(subjects.SUBJECTS, subjects.DEVELOPMENT, subjects.HELD_OUT))

    def test_the_map_has_the_papers_counts(self):
        counts = tuple(sorted((len(ws) for ws in subjects.SUBJECTS.values()), reverse=True))
        self.assertEqual((6, 6, 2, 1, 1, 1, 1, 1, 1, 1), counts)

    def test_the_map_and_the_split_have_no_problems_at_all(self):
        self.assertEqual([], subjects.problems(subjects.SUBJECTS, subjects.DEVELOPMENT, subjects.HELD_OUT))

    def test_every_workout_has_exactly_one_subject(self):
        self.assertEqual([f"w{i:02d}" for i in range(21)], sorted(subjects.SUBJECT_OF))

    def test_the_split_is_the_owners(self):
        self.assertEqual(("A", "B", "C", "S19"), subjects.DEVELOPMENT_SUBJECTS)
        self.assertEqual(("w12", "w13", "w16", "w17", "w18", "w20"), subjects.HELD_OUT)

    # The check itself, against a split known to leak: P3-27's.

    def test_the_check_finds_p3_27s_split_leaky_through_a_and_b(self):
        found = subjects.leaks(subjects.SUBJECTS, subjects.P3_27_DEVELOPMENT, subjects.P3_27_HELD_OUT)
        self.assertEqual({"A", "B"}, set(found))
        self.assertEqual((["w01", "w03", "w06"], ["w08", "w10", "w14"]), found["A"])
        self.assertEqual((["w02", "w04"], ["w07", "w09", "w11", "w15"]), found["B"])

    def test_the_check_finds_a_wrong_count(self):
        merged = dict(subjects.SUBJECTS)
        merged["S12"] = ("w12", "w13")
        del merged["S13"]
        self.assertTrue(any("paper says" in p for p in subjects.problems(merged, subjects.DEVELOPMENT, subjects.HELD_OUT)))

    # The Kotlin harness reads the JSON copy; it must be exactly what subjects.py writes.

    def test_the_json_copy_is_current(self):
        with open(subjects.JSON_PATH, newline="") as f:
            self.assertEqual(subjects.json_text(), f.read(), "run: python tools/mmfit-eval/subjects.py --write")


if __name__ == "__main__":
    unittest.main()
