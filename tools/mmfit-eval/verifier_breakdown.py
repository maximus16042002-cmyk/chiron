"""Per-exercise breakdown of the exercise verifier's warnings - DECISIONS.md P3-59.

P3-52 reported the verifier's warnings pooled over all eight selectable exercises: 9.15% of
matched repetitions warned, 95.97% of mismatched ones did. A pooled rate hides the exercise a
user actually picks, so this repeats P3-52's leave-one-subject-out folds exactly (same data,
same model, same thresholds) and reports both rates for each selected exercise.

Development subjects only, as P3-52: the held-out six are not read. Nothing is exported; the
shipped model is untouched.

    python tools/mmfit-eval/verifier_breakdown.py
"""
import os
import sys

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, os.path.dirname(__file__))
from train_verifier import CLASSES, CLASS_TO_IDX, load_data  # noqa: E402

SELECTABLE = ["bicep_curl", "lateral_raise", "lunge", "pushup", "row", "shoulder_press", "situp", "squat"]
SELECTED_P_MAX = 0.25
PREDICTED_P_MIN = 0.50


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    data = load_data(os.path.join(root, "data", "verifier", "movement_contexts_dev.csv"))
    X, y = data["X"], data["y"]
    subjects, cands, trues = data["subjects"], data["candidate_profiles"], data["true_exercises"]
    methods = data["extraction_methods"]

    # [selected] -> [matched total, matched warned, mismatched total, mismatched warned]
    counts = {s: [0, 0, 0, 0] for s in SELECTABLE}
    per_fold = {}

    for test_subject in sorted(np.unique(subjects)):
        train, test = subjects != test_subject, subjects == test_subject
        scaler = StandardScaler()
        clf = LogisticRegression(solver="lbfgs", max_iter=1000, C=1.0, random_state=2026)
        clf.fit(scaler.fit_transform(X[train]), y[train])
        probs = clf.predict_proba(scaler.transform(X[test]))
        fold = {s: [0, 0] for s in SELECTABLE}
        for i, row in enumerate(np.where(test)[0]):
            if methods[row] != "profile_tracker" or cands[row] not in counts:
                continue
            selected = cands[row]
            p = probs[i]
            predicted = int(np.argmax(p))
            warned = (p[CLASS_TO_IDX[selected]] < SELECTED_P_MAX and p[predicted] >= PREDICTED_P_MIN
                      and CLASSES[predicted] != selected)
            c = counts[selected]
            if trues[row] == selected:
                c[0] += 1
                c[1] += int(warned)
                fold[selected][0] += 1
                fold[selected][1] += int(warned)
            else:
                c[2] += 1
                c[3] += int(warned)
        per_fold[test_subject] = fold

    print("\nPer selected exercise, pooled over the four development folds")
    print(f"{'selected':<16}{'matched':>9}{'false warn':>12}{'rate':>8}{'mismatched':>12}{'caught':>8}{'rate':>8}")
    for s in SELECTABLE:
        mt, mw, xt, xw = counts[s]
        fr = f"{100 * mw / mt:.1f}%" if mt else "-"
        cr = f"{100 * xw / xt:.1f}%" if xt else "-"
        print(f"{s:<16}{mt:>9}{mw:>12}{fr:>8}{xt:>12}{xw:>8}{cr:>8}")

    print("\nFalse-warning rate on matched repetitions, per held-out development subject")
    print(f"{'selected':<16}" + "".join(f"{subj:>10}" for subj in per_fold))
    for s in SELECTABLE:
        cells = []
        for subj in per_fold:
            mt, mw = per_fold[subj][s]
            cells.append(f"{100 * mw / mt:.0f}% ({mt})" if mt else "-")
        print(f"{s:<16}" + "".join(f"{cell:>10}" for cell in cells))


if __name__ == "__main__":
    main()
