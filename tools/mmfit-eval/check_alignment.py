"""Are MM-Fit's frame numbers the frames this pipeline decoded? - DECISIONS.md P3-27.

MM-Fit's labels are frame numbers. If the video were decoded with a different numbering - a dropped
or duplicated frame at the start, a different first frame - every label would sit a few frames off
without any error. MM-Fit's bundled OpenPose skeleton carries its own frame numbers, from the same
video; so for each workout, MediaPipe's joints and OpenPose's are compared, in pixels, with the
OpenPose track shifted by -SEARCH..+SEARCH frames. Aligned numbering puts the best match at a shift of 0.

The OpenPose skeleton is used here only as a clock. It never reaches the Kinesis engine
(CLAUDE.md section 14).

Usage: python check_alignment.py <mm-fit root> <landmark dir> [workout ...]
"""
import json
import os
import struct
import sys

import numpy as np

LANDMARKS = 33
SEARCH = 90


def read_bin(path):
    with open(path, "rb") as handle:
        magic = handle.read(4)
        assert magic == b"KMFL", magic
        version, width, height, count = struct.unpack(">iiii", handle.read(16))
        body = struct.Struct(">iib" + "f" * (LANDMARKS * 4))
        frames, points = [], []
        for _ in range(count):
            fields = body.unpack(handle.read(body.size))
            frames.append(fields[1])
            points.append(np.array(fields[3:], dtype=np.float64).reshape(LANDMARKS, 4))
    return width, height, np.array(frames), np.array(points)


def main(root, landmark_dir, workouts):
    for workout in workouts:
        width, height, frames, points = read_bin(os.path.join(landmark_dir, workout + ".bin"))
        pose = np.load(os.path.join(root, "mm-fit", workout, workout + "_pose_2d.npy"))
        op_frames = pose[0, :, 0].astype(int)
        op_x, op_y = pose[0, :, 1:], pose[1, :, 1:]
        op_at = {f: i for i, f in enumerate(op_frames)}
        mp_x, mp_y = points[:, :, 0] * width, points[:, :, 1] * height
        # Which MediaPipe landmark each OpenPose joint is, by nearest mean position at shift 0.
        common = [i for i, f in enumerate(frames) if f in op_at]
        rows = [op_at[frames[i]] for i in common]
        pairs = []
        for j in range(op_x.shape[1]):
            ox, oy = op_x[rows, j], op_y[rows, j]
            valid = (ox > 0) & (oy > 0)
            if valid.sum() < 100:
                continue
            d = [np.nanmean(np.hypot(mp_x[common, k][valid] - ox[valid], mp_y[common, k][valid] - oy[valid])) for k in range(LANDMARKS)]
            k = int(np.nanargmin(d))
            pairs.append((j, k, d[k]))
        matched = [(j, k) for j, k, dist in pairs if dist < 25]
        # Mean pixel distance over the matched joints, for each shift of the OpenPose track.
        result = {}
        for shift in range(-SEARCH, SEARCH + 1):
            dists = []
            for i in common:
                f = frames[i] + shift
                if f not in op_at:
                    continue
                r = op_at[f]
                for j, k in matched:
                    if op_x[r, j] > 0 and op_y[r, j] > 0:
                        dists.append(np.hypot(mp_x[i, k] - op_x[r, j], mp_y[i, k] - op_y[r, j]))
            result[shift] = float(np.nanmean(dists)) if dists else float("nan")
        best = min(result, key=lambda s: result[s])
        print(f"{workout}: {len(common)} shared frames, {len(matched)} matched joints; best shift {best:+d} frames "
              f"({result[best]:.2f} px); at 0: {result[0]:.2f} px; at -3: {result[-3]:.2f}; at +3: {result[3]:.2f}")
        json.dump({"workout": workout, "shiftPx": result, "bestShift": best, "matched": matched},
                  open(os.path.join(landmark_dir, workout + ".alignment.json"), "w"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
