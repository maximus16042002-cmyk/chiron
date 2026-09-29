"""P3-28's and P3-29's held-out results, re-sliced on the six people never seen - DECISIONS.md P3-30.

From the runs already made: nothing is re-run and nothing re-tuned. Each figure is read from the run that
produced it, beside its "before" run, three ways: P3-27's thirteen held-out workouts (which must reproduce
the recorded figure), the six unseen people pooled, and each of the six alone.

Not a fresh held-out result: these workouts were seen in aggregate in P3-28 and P3-29. It corrects how
those figures should be read.

Usage: python reslice_unseen.py <data dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from analyse import load, metrics  # noqa: E402
from subjects import HELD_OUT, P3_27_HELD_OUT, subject_of  # noqa: E402

# exercise: (before run, after run, where the after figure was recorded)
FIGURES = {
    "bicep_curl": ("mm-fit-eval-p3-27", "mm-fit-eval-p3-28-fast-start", "P3-28"),
    "pushup": ("mm-fit-eval-p3-28-nearside", "mm-fit-eval-p3-29-rule", "P3-29"),
    "situp": ("mm-fit-eval-p3-28-nearside", "mm-fit-eval-p3-29-rule", "P3-29"),
    "lateral_raise": ("mm-fit-eval-p3-29-rule", "mm-fit-eval-p3-29-raise", "P3-29"),
}


def rows(data_dir, run, exercise, workouts):
    return [r for r in load(os.path.join(data_dir, run, "primary"))
            if r["status"] == "evaluated" and r["profileId"] == exercise and r["workout"] in workouts]


def line(label, m):
    over = m.get("over", 0)
    return (f"    {label:34s} {m['sets']:3d} sets  labelled {m['labelled']:4d}  counted {m['counted']:4d}  "
            f"MAE {m['mae']:5.2f}  exact {m['exact']:6.1%}  within 1 {m['within1']:6.1%}  sets over label {over}")


def measured(rs):
    m = metrics(rs)
    m["over"] = sum(1 for r in rs if r["counted"] > int(r["labelledReps"]))
    return m


def main(data_dir):
    for exercise, (before, after, where) in FIGURES.items():
        print(f"=== {exercise}: {before} -> {after} (after = the figure {where} recorded)")
        for run, name in ((before, "before"), (after, "after")):
            print(f"  {name}: {run}")
            print(line("P3-27 held-out, 13 workouts", measured(rows(data_dir, run, exercise, set(P3_27_HELD_OUT)))))
            six = rows(data_dir, run, exercise, set(HELD_OUT))
            print(line("the six unseen people, pooled", measured(six)))
            for subject in sorted({subject_of(r["workout"]) for r in six}):
                print(line(f"  {subject}", measured([r for r in six if subject_of(r["workout"]) == subject])))


if __name__ == "__main__":
    main(sys.argv[1])
