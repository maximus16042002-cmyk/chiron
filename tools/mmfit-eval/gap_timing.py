"""Curl phase times after a gap, against the raw angle - DECISIONS.md P3-39 (P3-30 decision 5).

A diagnostic on the development workouts only. For every VALID curl record whose phase times differ
between two harness runs (before and after P3-39), it times the same phases on the RAW elbow angle -
before the outlier gate and the smoother - by the engine's own rule: a phase runs from the first frame
off its zone to the first frame in the next zone, the zones being the profile's thresholds with their
hysteresis. The raw angle is not the truth either (MediaPipe jitters, and the engine's smoother lags
it), so what is compared is how far each run sits from it, not whether either equals it.

The angle is computed as the engine computes it: the 2D angle at the elbow, in the square-pixel frame
of CONTRACTS.md section 4 (as trace_set.py does).

With a stride (the harness's -Pstride, P3-32), only the frames the harness fed are used.

Usage: python gap_timing.py <landmark dir> <harness dir before> <harness dir after> [stride]
"""
import csv
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
import subjects  # noqa: E402

FPS = 30.0
JOINTS = {"LEFT": (11, 13, 15), "RIGHT": (12, 14, 16)}  # shoulder-elbow-wrist


def angle(p, a, v, b):
    ax, ay = p[a][0] - p[v][0], p[a][1] - p[v][1]
    bx, by = p[b][0] - p[v][0], p[b][1] - p[v][1]
    return math.degrees(math.atan2(abs(ax * by - ay * bx), ax * bx + ay * by))


def records(harness):
    rows = [r for r in csv.DictReader(open(os.path.join(harness, "primary", "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] == "bicep_curl"]
    out, exports = {}, {}
    for r in rows:
        w = r["workout"]
        if w not in exports:
            exports[w] = json.load(open(os.path.join(harness, "primary", w + ".export.json")))["sessions"][0]["sets"]
        for rep in exports[w][int(r["exportSetIndex"]) - 1]["reps"]:
            out[(w, r["mmfitSetIndex"], rep["repIndex"])] = (r, rep)
    return out


def raw_phases(series, times, top_edge, far_edge, start):
    """Descent and ascent on the raw angle, by the engine's rule; the curl's top is the larger angle.
    `start` is the index of the record's own start: its first raw far frame from there is its far point."""
    in_top = series >= top_edge
    in_far = series <= far_edge
    far = np.flatnonzero(in_far[start:]) + start
    if len(far) == 0:
        return None
    f_far = far[0]
    tops_before = np.flatnonzero(in_top[:f_far])
    if len(tops_before) == 0:
        return None
    f_off = tops_before[-1] + 1
    tops_after = np.flatnonzero(in_top[f_far:]) + f_far
    if len(tops_after) == 0:
        return None
    f_top = tops_after[0]
    far_before_top = np.flatnonzero(in_far[:f_top])
    f_leave = far_before_top[-1] + 1
    return times[f_far] - times[f_off], times[f_top] - times[f_leave]


def main(landmark_dir, before_dir, after_dir, stride=1):
    development = set(subjects.DEVELOPMENT)
    before, after = records(before_dir), records(after_dir)
    cache = {}
    errors = {"descent": ([], []), "ascent": ([], [])}
    one_result = {"descent": [0, 0], "ascent": [0, 0]}
    skipped = 0
    for key, (row, old) in sorted(before.items()):
        w = key[0]
        assert w in development, f"{w} is not a development workout"
        new = after[key][1]
        if old["validity"] != "VALID" or (old["descentMs"] == new["descentMs"] and old["ascentMs"] == new["ascentMs"]):
            continue
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache[w] = (width, height, {int(f): i for i, f in enumerate(frames)}, points)
        width, height, at, points = cache[w]
        top, bottom, hyst = float(row["top"]), float(row["bottom"]), float(row["hysteresis"])
        a, v, b = JOINTS[old["attributedSide"]]
        # Half a second either side: the raw angle may leave the start before the smoothed one does.
        first = int(round(old["startMs"] * FPS / 1000.0))
        last = int(round(old["endMs"] * FPS / 1000.0))
        window_first = int(row["windowFirst"])
        frames = [f for f in range(first - 15, last + 16) if f in at and (f - window_first) % stride == 0]
        series = np.array([angle(points[at[f]][:, :2] * np.array([1.0, height / width]), a, v, b) for f in frames])
        times = np.array(frames) * 1000.0 / FPS
        raw = raw_phases(series, times, top - hyst, bottom + hyst, int(np.searchsorted(frames, first)))
        if raw is None:
            skipped += 1
            continue
        for i, phase in enumerate(["descent", "ascent"]):
            field = "descentMs" if phase == "descent" else "ascentMs"
            if old[field] == new[field] or old[field] is None or new[field] is None:
                continue
            errors[phase][0].append(old[field] - raw[i])
            errors[phase][1].append(new[field] - raw[i])
            one_result[phase][0] += old[field] <= 34
            one_result[phase][1] += new[field] <= 34
    print("VALID curl phases whose time moved, against the same phase timed on the raw angle (engine - raw, ms);"
          f" {skipped} records had no raw cycle to time:")
    for phase, (old_e, new_e) in errors.items():
        old_e, new_e = np.asarray(old_e), np.asarray(new_e)
        if len(old_e) == 0:
            continue
        print(f"  {phase:7} n={len(old_e):4}  before: median {np.median(old_e):7.1f}, median |error| {np.median(np.abs(old_e)):6.1f}"
              f"  |  after: median {np.median(new_e):7.1f}, median |error| {np.median(np.abs(new_e)):6.1f}"
              f"  |  one-result phases {one_result[phase][0]} -> {one_result[phase][1]}")


if __name__ == "__main__":
    if len(sys.argv) not in (4, 5):
        sys.exit("usage: python gap_timing.py <landmark dir> <harness dir before> <harness dir after> [stride]")
    main(*sys.argv[1:4], *([int(sys.argv[4])] if len(sys.argv) == 5 else []))
