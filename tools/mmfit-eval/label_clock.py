"""Which frame numbering do MM-Fit's labels use? - DECISIONS.md P3-27.

alignment_by_set.py shows MM-Fit's frame numbers (OpenPose's) and this pipeline's decoded frames drift
apart by tens of frames within a workout. The labels are frame numbers too, so which numbering are
they in? A label's start and end are placed where the set's movement starts and stops. Measured in
the labels' own numbering, the gap between a label boundary and the movement's onset or offset is
roughly constant from set to set; measured in the other numbering, it also carries the drift, and
varies with it. So the spread of those gaps, across a workout's sets, in each numbering, says which
one the labels were written in.

Movement is measured the same way in both: the summed frame-to-frame speed of the moving joints,
from MediaPipe for decoded frames and from OpenPose for MM-Fit's frames.

Usage: python label_clock.py <mm-fit root> <landmark dir> [workout ...]
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from alignment_by_set import COCO_TO_MEDIAPIPE  # noqa: E402
from check_alignment import read_bin  # noqa: E402


def onset_offset(track, start, end):
    """First and last frame, near the label window, whose movement exceeds a quarter of the set's
    high movement. `track` maps frame -> movement (pixels per frame)."""
    inside = [track[f] for f in range(start, end + 1) if f in track]
    if len(inside) < 100:
        return None
    level = 0.25 * np.percentile(inside, 90)
    frames = [f for f in range(start - 45, end + 46) if f in track]
    smooth = {}
    for f in frames:
        near = [track[g] for g in range(f - 3, f + 4) if g in track]
        smooth[f] = float(np.mean(near))
    moving = [f for f in frames if smooth[f] > level]
    return (moving[0], moving[-1]) if moving else None


def movement(positions):
    """frame -> summed speed over joints, from frame -> array of (x, y) per joint."""
    out = {}
    for f, p in positions.items():
        q = positions.get(f - 1)
        if q is not None:
            out[f] = float(np.nansum(np.hypot(p[:, 0] - q[:, 0], p[:, 1] - q[:, 1])))
    return out


def main(root, landmark_dir, workouts):
    for workout in workouts:
        meta = json.load(open(os.path.join(landmark_dir, workout + ".json")))
        width, height, frames, points = read_bin(os.path.join(landmark_dir, workout + ".bin"))
        pose = np.load(os.path.join(root, "mm-fit", workout, workout + "_pose_2d.npy"))
        mp_joints = list(COCO_TO_MEDIAPIPE.values())
        op_joints = list(COCO_TO_MEDIAPIPE.keys())
        decoded = {int(f): np.stack([points[i, mp_joints, 0] * width, points[i, mp_joints, 1] * height], axis=1)
                   for i, f in enumerate(frames)}
        mmfit = {int(f): np.stack([pose[0, i, [1 + j for j in op_joints]], pose[1, i, [1 + j for j in op_joints]]], axis=1)
                 for i, f in enumerate(pose[0, :, 0])}
        for d in mmfit.values():
            d[d <= 0] = np.nan
        speed_decoded, speed_mmfit = movement(decoded), movement(mmfit)
        gaps = {"decoded": [], "mmfit": []}
        for s in meta["sets"]:
            for name, track in (("decoded", speed_decoded), ("mmfit", speed_mmfit)):
                found = onset_offset(track, s["start"], s["end"])
                if found:
                    gaps[name].append((s["setIndex"], found[0] - s["start"], found[1] - s["end"]))
        for name in ("mmfit", "decoded"):
            starts = np.array([g[1] for g in gaps[name]])
            ends = np.array([g[2] for g in gaps[name]])
            print(f"{workout} in {name:7s} numbering: {len(starts)} sets | onset - label start: median {np.median(starts):+.0f}, "
                  f"IQR {np.percentile(starts, 25):+.0f}..{np.percentile(starts, 75):+.0f}, SD {starts.std():.1f} | "
                  f"offset - label end: median {np.median(ends):+.0f}, IQR {np.percentile(ends, 25):+.0f}..{np.percentile(ends, 75):+.0f}, SD {ends.std():.1f}")
        json.dump(gaps, open(os.path.join(landmark_dir, workout + ".label-clock.json"), "w"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
