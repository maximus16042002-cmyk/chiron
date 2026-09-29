"""MM-Fit's subjects, and the evaluation's splits, in one place - DECISIONS.md P3-30.

MM-Fit publishes no subject-to-workout mapping. This one was judged by eye from contact sheets
(subject_sheets.py) and checked against everything the paper states (Stromback, Huang and Radu 2020):
ten subjects, two with six workouts, one with two, seven with one; and its split, whose cross-subject
test set (w00, w05, w12, w13, w20) shares no subject with its training set. P3-30 records the evidence.

**The split is by subject** (the owner's decision, P3-30). Development: A, B, C and w19's subject.
Held-out: the six people never seen in development.

P3-27's split was by workout, and it put A and B on both sides. It stays here, under its own name, only
so that results recorded under it (P3-27 to P3-29) can be reproduced and are labelled "P3-27 split".

`python subjects.py --write` writes subjects.json, the copy the Kotlin harness reads. test_subjects.py
fails if the two ever disagree.

Usage: python subjects.py [--write]
"""
import json
import os
import sys

# The letters and "S<nn>" names are this project's labels; nobody is identified beyond "the same person as".
SUBJECTS = {
    "A": ("w01", "w03", "w06", "w08", "w10", "w14"),
    "B": ("w02", "w04", "w07", "w09", "w11", "w15"),
    "C": ("w00", "w05"),
    "S12": ("w12",),
    "S13": ("w13",),
    "S16": ("w16",),
    "S17": ("w17",),
    "S18": ("w18",),
    "S19": ("w19",),
    "S20": ("w20",),
}

# MM-Fit section 3.1: "two participants carried out six workout sessions each, one participated in two
# sessions, and the remaining participants carried out one workout session each" - ten in all.
PAPER_WORKOUTS_PER_SUBJECT = (6, 6, 2, 1, 1, 1, 1, 1, 1, 1)

DEVELOPMENT_SUBJECTS = ("A", "B", "C", "S19")
HELD_OUT_SUBJECTS = ("S12", "S13", "S16", "S17", "S18", "S20")

# P3-27's split, by workout. Not for new work: see the module docstring.
P3_27_DEVELOPMENT = ("w00", "w01", "w02", "w03", "w04", "w05", "w06", "w19")
P3_27_HELD_OUT = ("w07", "w08", "w09", "w10", "w11", "w12", "w13", "w14", "w15", "w16", "w17", "w18", "w20")


def workouts_of(subjects):
    return tuple(sorted(w for s in subjects for w in SUBJECTS[s]))


DEVELOPMENT = workouts_of(DEVELOPMENT_SUBJECTS)
HELD_OUT = workouts_of(HELD_OUT_SUBJECTS)

# Every split a tool may name. The P3-27 pair is labelled as such wherever it is used.
SPLITS = {
    "development": DEVELOPMENT,
    "held-out": HELD_OUT,
    "p3-27-development": P3_27_DEVELOPMENT,
    "p3-27-held-out": P3_27_HELD_OUT,
}

SUBJECT_OF = {w: s for s, ws in SUBJECTS.items() for w in ws}


def subject_of(workout):
    return SUBJECT_OF[workout]


def chosen_split(argv):
    """A tool's development and held-out workouts, and the label its output carries.

    By subject (P3-30) unless `--p3-27-split` is given, which reproduces results recorded under P3-27's
    split by workout and labels every line of them "(P3-27 split)".
    """
    if "--p3-27-split" in argv:
        return set(P3_27_DEVELOPMENT), set(P3_27_HELD_OUT), " (P3-27 split)"
    return set(DEVELOPMENT), set(HELD_OUT), ""


def leaks(subjects, first, second):
    """The subjects with workouts on both sides of a split, and their workouts on each side."""
    found = {}
    for subject, workouts in subjects.items():
        a = sorted(set(workouts) & set(first))
        b = sorted(set(workouts) & set(second))
        if a and b:
            found[subject] = (a, b)
    return found


def problems(subjects, first, second):
    """Everything wrong with a subject map and a split of it, as sentences. Empty when all is well."""
    found = []
    for subject, (a, b) in leaks(subjects, first, second).items():
        found.append(f"subject {subject} is on both sides: {', '.join(a)} and {', '.join(b)}")
    counts = tuple(sorted((len(ws) for ws in subjects.values()), reverse=True))
    if counts != PAPER_WORKOUTS_PER_SUBJECT:
        found.append(f"workouts per subject {counts}, but MM-Fit's paper says {PAPER_WORKOUTS_PER_SUBJECT}")
    every = [w for ws in subjects.values() for w in ws]
    expected = [f"w{i:02d}" for i in range(21)]
    if sorted(every) != expected:
        found.append(f"the map does not hold each of w00-w20 exactly once: {sorted(every)}")
    if set(first) & set(second):
        found.append(f"workouts on both sides: {sorted(set(first) & set(second))}")
    if sorted(set(first) | set(second)) != expected:
        found.append("the split does not cover w00-w20")
    return found


def as_json():
    return {
        "source": "tools/mmfit-eval/subjects.py - DECISIONS.md P3-30. Generated; do not edit by hand.",
        "subjects": {s: list(ws) for s, ws in SUBJECTS.items()},
        "paperWorkoutsPerSubject": list(PAPER_WORKOUTS_PER_SUBJECT),
        "developmentSubjects": list(DEVELOPMENT_SUBJECTS),
        "heldOutSubjects": list(HELD_OUT_SUBJECTS),
        "splits": {name: list(ws) for name, ws in SPLITS.items()},
    }


JSON_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "subjects.json")


def json_text():
    return json.dumps(as_json(), indent=2) + "\n"


if __name__ == "__main__":
    wrong = problems(SUBJECTS, DEVELOPMENT, HELD_OUT)
    if wrong:
        sys.exit("refusing: " + "; ".join(wrong))
    if "--write" in sys.argv:
        with open(JSON_PATH, "w", newline="\n") as f:
            f.write(json_text())
        print("wrote", JSON_PATH)
    for name, ws in SPLITS.items():
        print(f"{name:18s} {len(ws):2d} workouts: {' '.join(ws)}")
