"""The anomaly study's operating point - DECISIONS.md P3-34, P3-36, P3-37.

Run from the repository root: python -m unittest tools/mmfit-eval/test_anomaly_study.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anomaly_study  # noqa: E402


class EqualCutTest(unittest.TestCase):

    # Two subjects of 20: the mean of their flag rates must be at most 5%.
    A = [float(i) for i in range(1, 21)]          # 1 .. 20
    B = [float(i) / 10 for i in range(1, 21)]     # 0.1 .. 2.0

    def test_the_distinct_cut_is_the_smallest_score_that_meets_the_target(self):
        cut = anomaly_study.equal_cut({"A": self.A, "B": self.B}, distinct=True)
        # At 2.0: A flags 18/20, B 0 -> 45%. Only far up does the mean fall to 5%: A must flag at most 2 of 20.
        self.assertEqual(18.0, cut)
        self.assertLessEqual(anomaly_study.mean_rate([self.A, self.B], cut), 0.05)

    def test_the_grid_cut_is_the_smallest_multiple_of_a_hundredth_that_meets_it(self):
        # Scores 0.105 .. 2.0; one subject, 20 values: at most 1 may be flagged.
        values = [0.105 + 0.1 * i for i in range(20)]
        cut = anomaly_study.equal_cut({"A": values}, distinct=False)
        self.assertAlmostEqual(1.91, cut)  # at 1.91 only 2.005 is above it; at 1.90, 1.905 is too
        self.assertLessEqual(anomaly_study.mean_rate([values], cut), 0.05)
        self.assertGreater(anomaly_study.mean_rate([values], cut - 0.01), 0.05)

    def test_subjects_weigh_equally_whatever_their_counts(self):
        many = [0.0] * 1000
        few = [1.0] * 10
        # Flagging all of `few` gives a mean of 50% however many `many` has.
        self.assertEqual(0.5, anomaly_study.mean_rate([many, few], 0.5))


if __name__ == "__main__":
    unittest.main()
