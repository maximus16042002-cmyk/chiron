"""Which MM-Fit subject is in which workout - DECISIONS.md P3-30, Step 0.

MM-Fit publishes no subject-to-workout mapping. Its paper (Stromback et al. 2020, section 3.1) gives
the counts - ten subjects; two did six workouts each, one did two, seven did one - and its experiments
section gives a split whose cross-subject test set (w00, w05, w12, w13, w20) shares no subject with
training (w01-w04, w06-w08, w16-w18). The lifted 3D pose does not tell the subjects apart: it pulls
every body towards average proportions.

This writes contact sheets for grouping the workouts by eye: per workout, the person cropped from the
middle of three standing or seated sets, using MM-Fit's own 2D pose for the box. The sheets show MM-Fit's
subjects, so they go under data/ with the dataset (not in git) and nowhere else.

Usage: python subject_sheets.py <mm-fit dir> <landmark dir> <out dir>
"""
import csv
import os
import sys

import cv2
import numpy as np

EXERCISES = ("squats", "jumping_jacks", "lateral_shoulder_raises", "bicep_curls")
TILE_H, TILE_W, PER_WORKOUT = 320, 220, 3


def crops(mmfit, landmarks, workout):
    labels = list(csv.reader(open(f"{mmfit}/mm-fit/{workout}/{workout}_labels.csv")))
    with open(f"{landmarks}/{workout}.shifts.csv") as f:
        shifts = {int(r["setIndex"]): int(r["shift"]) for r in csv.DictReader(f)}
    pose = np.load(f"{mmfit}/mm-fit/{workout}/{workout}_pose_2d.npy")  # 2 x frames x (frame no. + 18)
    frames = pose[0, :, 0].astype(int)
    video = cv2.VideoCapture(f"{mmfit}/video/{workout}_rgb.mp4")
    out = []
    for exercise in EXERCISES:
        sets = [i for i, row in enumerate(labels) if row[3] == exercise]
        if not sets or len(out) >= PER_WORKOUT:
            continue
        k = sets[0]
        middle = (int(labels[k][0]) + int(labels[k][1])) // 2
        j = int(np.argmin(abs(frames - middle)))
        xs, ys = pose[0, j, 1:], pose[1, j, 1:]
        seen = (xs > 0) & (ys > 0)
        if seen.sum() < 5:
            continue
        x0, x1, y0, y1 = xs[seen].min(), xs[seen].max(), ys[seen].min(), ys[seen].max()
        # MM-Fit frame N is decoded frame N - shift (P3-27). Seeking is not frame-exact, which a
        # picture of a person does not need.
        video.set(cv2.CAP_PROP_POS_FRAMES, middle - shifts.get(k + 1, 0))
        ok, image = video.read()
        if not ok:
            continue
        margin = 0.25 * (y1 - y0)
        crop = image[max(0, int(y0 - margin)):int(y1 + 0.4 * margin), max(0, int(x0 - margin)):int(x1 + margin)]
        crop = cv2.resize(crop, (max(1, int(crop.shape[1] * TILE_H / crop.shape[0])), TILE_H))
        crop = cv2.copyMakeBorder(crop, 0, 0, 0, max(0, TILE_W - crop.shape[1]), cv2.BORDER_CONSTANT)
        out.append(crop[:, :TILE_W])
    while len(out) < PER_WORKOUT:
        out.append(np.zeros((TILE_H, TILE_W, 3), np.uint8))
    tile = np.hstack(out)
    cv2.putText(tile, workout, (5, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2)
    return tile


def main(mmfit, landmarks, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    tiles = [crops(mmfit, landmarks, f"w{w:02d}") for w in range(21)]
    for n, (a, b) in enumerate([(0, 8), (8, 16), (16, 21)]):
        sheet = tiles[a:b] + ([np.zeros_like(tiles[0])] if (b - a) % 2 else [])
        gap = np.full((TILE_H, 12, 3), 255, np.uint8)
        rows = [np.hstack([sheet[i], gap, sheet[i + 1]]) for i in range(0, len(sheet), 2)]
        path = os.path.join(out_dir, f"subjects_{n}.jpg")
        cv2.imwrite(path, np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 80])
        print(path)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
