"""P3-45's test: the sit-up's hip driver against the trunk driver, on the development workouts.

For each development subject with sit-ups, the share of MM-Fit's labelled repetitions counted VALID
under each driver, and every set that counts above its label. The trunk driver passes only if every
subject's share is at least the hip driver's, the total is higher, and no set counts above its label.

With `--held-out` (DECISIONS.md P3-46, approved by the owner for one run) it reads the held-out
workouts instead, and refuses development ones.

Usage: python situp_driver_compare.py <hip harness dir> <trunk harness dir> [--held-out]
"""
import csv
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import subjects  # noqa: E402


def counts(harness, held_out):
    base = os.path.join(harness, "primary")
    rows = [r for r in csv.DictReader(open(os.path.join(base, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] == "situp"]
    allowed = set(subjects.HELD_OUT if held_out else subjects.DEVELOPMENT)
    out, exports = {}, {}
    for r in rows:
        w = r["workout"]
        if w not in allowed:
            sys.exit(f"refusing: {w} is not a {'held-out' if held_out else 'development'} workout (DECISIONS.md P3-45, P3-46)")
        if w not in exports:
            exports[w] = json.load(open(os.path.join(base, w + ".export.json")))["sessions"][0]["sets"]
        s = exports[w][int(r["exportSetIndex"]) - 1]
        valid = sum(1 for rep in s["reps"] if rep["validity"] == "VALID")
        out[(w, r["mmfitSetIndex"])] = (subjects.subject_of(w), int(r["labelledReps"]), valid)
    return out


def main(hip_dir, trunk_dir, held_out=False):
    hip, trunk = counts(hip_dir, held_out), counts(trunk_dir, held_out)
    assert hip.keys() == trunk.keys(), "the two runs evaluated different sets"
    per = defaultdict(lambda: [0, 0, 0, 0, 0])  # labelled, hip, trunk, hip sets over, trunk sets over
    over = []
    for key, (subject, labelled, h) in sorted(hip.items()):
        t = trunk[key][2]
        p = per[subject]
        p[0] += labelled
        p[1] += h
        p[2] += t
        p[3] += h > labelled
        p[4] += t > labelled
        if t > labelled:
            over.append(f"{key[0]} set {key[1]}: {t} counted, {labelled} labelled")
    print(f"{'subject':8} {'labelled':>8} {'hip':>12} {'trunk':>12}  sets over label (hip / trunk)")
    passed_each = True
    total = [0, 0, 0]
    for subject, (labelled, h, t, ho, to) in sorted(per.items()):
        print(f"{subject:8} {labelled:8} {h:5} ({h / labelled:5.1%}) {t:5} ({t / labelled:5.1%})  {ho} / {to}")
        passed_each &= t / labelled >= h / labelled
        total[0] += labelled
        total[1] += h
        total[2] += t
    print(f"{'all':8} {total[0]:8} {total[1]:5} ({total[1] / total[0]:5.1%}) {total[2]:5} ({total[2] / total[0]:5.1%})")
    print("sets over label with the trunk driver: " + ("; ".join(over) if over else "none"))
    verdict = passed_each and total[2] > total[1] and not over
    print("VERDICT: " + ("PASS" if verdict else "FAIL") + " (P3-45: every subject at least as many, the total higher, no set over its label)")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--held-out"]
    if len(args) != 2:
        sys.exit("usage: python situp_driver_compare.py <hip harness dir> <trunk harness dir> [--held-out]")
    main(args[0], args[1], held_out="--held-out" in sys.argv[1:])
