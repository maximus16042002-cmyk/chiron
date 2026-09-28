"""The OpenPose-to-decoded frame shift, set by set, from moving joints - DECISIONS.md P3-27.

MM-Fit's labels are frame numbers. If this pipeline's decoded frame f is not MM-Fit's frame f, every
label window is shifted without any error. MM-Fit's bundled OpenPose skeleton carries MM-Fit's frame
numbers, so inside each labelled set the trajectory of each moving joint is cross-correlated between
MediaPipe (decoded frame numbers) and OpenPose (MM-Fit frame numbers). The lag of the correlation peak
is the shift: 0 means the two numberings agree.

Absolute pixel distance is not used: the two models place joints differently, and a joint that barely
moves in a set carries no timing information at all. Only joints that move are used, each standardised.

The OpenPose skeleton is used here only as a clock. It never reaches the Kinesis engine
(CLAUDE.md section 14).

Usage: python alignment_by_set.py <mm-fit root> <landmark dir> [workout ...]
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402

# The shift is tracked from set to set, not searched for afresh (P3-27). One wide search is not safe:
# on repetitive movement the correlation also peaks a whole repetition away, and at +/-240 frames w18's
# last sets locked onto a peak about 63 frames (one repetition) beyond their drift, with every joint
# agreeing. So each workout starts near 0 - every workout's first sets are within a few frames of it -
# and each set is searched only within TRACK_WINDOW of the last reliable set's shift. The drift moves a
# few frames between sets and its largest measured jumps are 17 to 29 frames; a false peak is a whole
# repetition away, 45 frames or more for these exercises.
FIRST_WINDOW = 60
TRACK_WINDOW = 40

# A set's shift is taken as measured only when the joints agree: at least two joint-axes, with the
# middle half of their lags within this many frames. Otherwise it is interpolated from its neighbours,
# as a set with nothing moving already is. w16's push-up sets 5 and 6 split between about 0 and about
# 20 - neither half is evidence of the shift.
MIN_JOINT_AXES = 2
MAX_IQR_FRAMES = 6

# COCO-18 OpenPose joint -> MediaPipe landmark, the body joints only. Measured: each OpenPose joint's
# nearest MediaPipe landmark over workout w19 (DECISIONS.md P3-27).
COCO_TO_MEDIAPIPE = {
    2: 12, 3: 14, 4: 16,  # right shoulder, elbow, wrist
    5: 11, 6: 13, 7: 15,  # left shoulder, elbow, wrist
    8: 24, 9: 26, 10: 28,  # right hip, knee, ankle
    11: 23, 12: 25, 13: 27,  # left hip, knee, ankle
}


def best_lag(m, frames, op_series, op_first, lo, hi):
    """The lag in lo..hi at which MediaPipe's values `m` (at decoded `frames`) best correlate with
    OpenPose's track at frame + lag, read across the whole workout rather than only inside the
    extracted window, and that correlation. OpenPose marks a joint it did not find as 0 or below."""
    best, peak = None, -2.0
    for lag in range(lo, hi + 1):
        idx = frames + lag - op_first
        ok = (idx >= 0) & (idx < len(op_series))
        if ok.sum() < 60:
            continue
        o = op_series[idx[ok]]
        x = m[ok]
        valid = o > 0
        o, x = o[valid], x[valid]
        if len(o) < 60 or o.std() < 8 or x.std() < 8:  # moves less than ~8 px: no timing information
            continue
        r = float(np.corrcoef(x, o)[0, 1])
        if r > peak:
            best, peak = lag, r
    return best, peak


def write_shifts(path, sets, results):
    """The harness's input: one shift per labelled set. A set with no reliable shift - nothing in it
    moved enough, no joint correlated, or the joints disagreed - takes one interpolated linearly, by
    label start frame, between the nearest reliably measured sets on either side (or the nearest one,
    at an end), marked so. A rejected estimate is kept in its own column, not used."""
    measured = [(s["start"], r["shift"]) for s, r in zip(sets, results) if r["shift"] is not None]
    with open(path, "w", newline="") as handle:
        handle.write("setIndex,labelStart,shift,source,iqrLow,iqrHigh,jointAxes,rejectedEstimate\n")
        for s, r in zip(sets, results):
            low, high = r["iqr"] if r.get("iqr") else ("", "")
            rejected = ""
            if r["shift"] is not None:
                shift, source = r["shift"], "measured"
            else:
                before = [m for m in measured if m[0] <= s["start"]]
                after = [m for m in measured if m[0] > s["start"]]
                if before and after:
                    (s0, v0), (s1, v1) = before[-1], after[0]
                    shift = int(round(v0 + (v1 - v0) * (s["start"] - s0) / (s1 - s0)))
                else:
                    shift = (before[-1] if before else after[0])[1]
                source = "interpolated"
                rejected = "" if r.get("estimate") is None else r["estimate"]
            handle.write(f"{s['setIndex']},{s['start']},{shift},{source},{low},{high},{r['joints']},{rejected}\n")


def main(root, landmark_dir, workouts):
    summary = {}
    for workout in workouts:
        meta = json.load(open(os.path.join(landmark_dir, workout + ".json")))
        width, height, frames, points = read_bin(os.path.join(landmark_dir, workout + ".bin"))
        pose = np.load(os.path.join(root, "mm-fit", workout, workout + "_pose_2d.npy"))
        op_frames = pose[0, :, 0].astype(int)
        op_first = int(op_frames.min())
        # OpenPose's track laid out densely by frame - op_first. Its frame numbers have gaps in some
        # workouts (w03 - found by an assertion that assumed they did not); a missing frame is 0, which
        # best_lag already treats as a joint OpenPose did not find.
        dense = {}
        for axis in (0, 1):
            for coco in COCO_TO_MEDIAPIPE:
                track = np.zeros(int(op_frames.max()) - op_first + 1)
                track[op_frames - op_first] = pose[axis, :, 1 + coco]
                dense[(axis, coco)] = track
        mp_row = {int(f): i for i, f in enumerate(frames)}
        results = []
        anchor, first = 0, True
        for s in sorted(meta["sets"], key=lambda x: x["start"]):
            window = np.array([f for f in range(s["firstFrame"], s["lastFrame"] + 1) if f in mp_row])
            lo, hi = (anchor - FIRST_WINDOW, anchor + FIRST_WINDOW) if first else (anchor - TRACK_WINDOW, anchor + TRACK_WINDOW)
            lags = []
            if len(window) >= 200:
                rows = np.array([mp_row[f] for f in window])
                for coco, mpi in COCO_TO_MEDIAPIPE.items():
                    for axis, scale in ((0, width), (1, height)):
                        m = points[rows, mpi, axis] * scale
                        if np.isnan(m).any():
                            continue
                        lag, peak = best_lag(m, window, dense[(axis, coco)], op_first, lo, hi)
                        if lag is not None and peak > 0.8:
                            lags.append(lag)
            estimate = int(np.median(lags)) if lags else None
            spread = (int(np.percentile(lags, 25)), int(np.percentile(lags, 75))) if lags else None
            reliable = len(lags) >= MIN_JOINT_AXES and spread is not None and spread[1] - spread[0] <= MAX_IQR_FRAMES
            # A peak at the edge of the search may really lie beyond it: not evidence either.
            at_edge = estimate is not None and (estimate <= lo + 1 or estimate >= hi - 1)
            reliable = reliable and not at_edge
            shift = estimate if reliable else None
            if reliable:
                anchor, first = shift, False
            results.append({"setIndex": s["setIndex"], "exercise": s["exercise"], "start": s["start"],
                            "shift": shift, "estimate": estimate, "iqr": spread, "joints": len(lags),
                            "searched": [lo, hi]})
            note = "" if reliable else ("  -> at the search edge, interpolated" if at_edge else "  -> unreliable, interpolated")
            print(f"{workout} set {s['setIndex']:2d} {s['exercise']:24s} start {s['start']:6d}  shift {estimate}  "
                  f"(interquartile {spread}, {len(lags)} joint-axes, searched {lo}..{hi}){note}", flush=True)
        results.sort(key=lambda r: r["setIndex"])
        json.dump(results, open(os.path.join(landmark_dir, workout + ".alignment-by-set.json"), "w"), indent=1)
        write_shifts(os.path.join(landmark_dir, workout + ".shifts.csv"), meta["sets"], results)
        shifts = [r["shift"] for r in results if r["shift"] is not None]
        summary[workout] = (int(np.median(shifts)) if shifts else None, min(shifts, default=None), max(shifts, default=None), len(shifts))
    for w, (median, lo, hi, n) in summary.items():
        print(f"{w}: median shift {median}, range {lo}..{hi}, over {n} sets")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
