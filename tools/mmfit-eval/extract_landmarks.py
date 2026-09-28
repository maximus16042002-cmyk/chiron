"""P7: MediaPipe Pose over MM-Fit's RGB video, one labelled set at a time - DECISIONS.md P3-27.

CLAUDE.md section 14: the pipeline is MM-Fit RGB video -> MediaPipe Pose -> Kinesis. MM-Fit's own
OpenPose skeletons are never fed to the engine; this script produces the MediaPipe landmarks that are.

What it runs, and why it matches the phone
------------------------------------------
- The phone's own model file, app/src/main/assets/pose_landmarker_lite.task.
- The phone's settings: one pose, MediaPipe's default confidence thresholds
  (PoseLandmarkerSource sets neither). VIDEO mode, the offline twin of the phone's LIVE_STREAM:
  the same tracking from frame to frame, without dropping frames.
- A fresh landmarker for every set, as the phone starts one per set.
- Every frame of each labelled set, plus MARGIN_S seconds either side, so the harness can run the
  exact label window and a widened one from the same file.
- Frames decoded strictly in order. OpenCV's seek is not frame-exact on these files, and the labels
  are frame numbers, so a seek could put every label a few frames off without any error.
- Timestamps frame * 1000 / 30 ms, from the start of the video.

What it writes
--------------
Raw landmarks, exactly as MediaPipe returns them: x and y normalised to the frame's width and height.
The frame-shape correction of CONTRACTS.md section 4 is the harness's job, as it is the phone
adapter's, so this file holds what the model said and nothing else.

<out>/wXX.bin, big-endian:
    header  b"KMFL", int32 version (1), int32 width, int32 height, int32 record count
    record  int32 setIndex (1-based, the label file's order), int32 frame, int8 posePresent,
            33 x (float32 x, y, z, visibility) - NaN when no pose was found
<out>/wXX.json: the labels, each set's frame range and pose coverage, the video's frame count as
    reported and as decoded, the model's SHA-256 and the library versions.

Usage: python extract_landmarks.py <mm-fit root> <out dir> [workout ...] [--workers N]
"""
import argparse
import csv
import hashlib
import json
import os
import struct
import time
from multiprocessing import Pool

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_tasks
from mediapipe.tasks.python import vision

FPS = 30.0
MARGIN_S = 2.0
LANDMARKS = 33
MODEL = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "pose_landmarker_lite.task")


def labels_of(root, workout):
    path = os.path.join(root, "mm-fit", workout, workout + "_labels.csv")
    with open(path, newline="") as handle:
        return [
            {"setIndex": i + 1, "start": int(s), "end": int(e), "reps": int(r), "exercise": name}
            for i, (s, e, r, name) in enumerate(csv.reader(handle))
        ]


def landmarker():
    options = vision.PoseLandmarkerOptions(
        base_options=mp_tasks.BaseOptions(model_asset_path=os.path.abspath(MODEL)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
    )
    return vision.PoseLandmarker.create_from_options(options)


def shifts_of(out_dir, workout):
    """setIndex -> shift from alignment_by_set.py's wXX.shifts.csv: MM-Fit frame N is decoded frame N - shift."""
    with open(os.path.join(out_dir, workout + ".shifts.csv"), newline="") as handle:
        return {int(r["setIndex"]): int(r["shift"]) for r in csv.DictReader(handle)}


def extract(args):
    root, out_dir, workout, shifted = args
    started = time.time()
    sets = labels_of(root, workout)
    margin = int(round(MARGIN_S * FPS))
    video = os.path.join(root, "video", workout + "_rgb.mp4")
    cap = cv2.VideoCapture(video)
    reported = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width, height = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    # Each set's window, with its margin, clipped to the video. Windows are visited in frame order.
    # --shifted: centred on the decoded frames the label means, for a workout whose numbering drifts
    # further than the margin covers (P3-27: w16 reaches 103 frames).
    shifts = shifts_of(out_dir, workout) if shifted else {}
    windows = []
    for s in sets:
        shift = shifts.get(s["setIndex"], 0)
        s["shiftApplied"] = shift
        first, last = max(0, s["start"] - shift - margin), min(reported - 1, s["end"] - shift + margin)
        windows.append((first, last, s))
    windows.sort(key=lambda w: w[0])

    records = []
    nan = [float("nan")] * (LANDMARKS * 4)
    frame = -1
    for first, last, s in windows:
        detector = landmarker()
        present = 0
        # Advance in order to the window's first frame. A frame already passed cannot be revisited, so
        # where two sets' margins overlap - w17's sets 31 and 32 are under 4 s apart - this set's window
        # starts after the frames already done, and the trim is recorded. Those frames are in the file
        # under the earlier set; the harness takes a frame from whichever set's run has it (P3-27).
        if first <= frame:
            s["marginTrimmedFrom"] = first
            first = frame + 1
        while frame < first - 1:
            if not cap.grab():
                raise RuntimeError(f"{workout}: video ended at frame {frame}, before {first}")
            frame += 1
        for _ in range(first, last + 1):
            ok, image = cap.read()
            if not ok:
                raise RuntimeError(f"{workout}: video ended at frame {frame} inside set {s['setIndex']}")
            frame += 1
            rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            result = detector.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(round(frame * 1000.0 / FPS)))
            if result.pose_landmarks:
                present += 1
                values = []
                for lm in result.pose_landmarks[0]:
                    values += [lm.x, lm.y, lm.z, lm.visibility]
                records.append((s["setIndex"], frame, 1, values))
            else:
                records.append((s["setIndex"], frame, 0, nan))
        detector.close()
        s["firstFrame"], s["lastFrame"], s["framesWithPose"] = first, last, present

    # Decode the rest, so the file records how many frames the video really has.
    decoded = frame + 1
    while cap.grab():
        decoded += 1
    cap.release()

    with open(os.path.join(out_dir, workout + ".bin"), "wb") as handle:
        handle.write(b"KMFL" + struct.pack(">iiii", 1, width, height, len(records)))
        body = struct.Struct(">iib" + "f" * (LANDMARKS * 4))
        for set_index, f, flag, values in records:
            handle.write(body.pack(set_index, f, flag, *values))
    with open(MODEL, "rb") as handle:
        model_sha = hashlib.sha256(handle.read()).hexdigest()
    meta = {
        "workout": workout,
        "video": os.path.basename(video),
        "width": width,
        "height": height,
        "fps": FPS,
        "framesReported": reported,
        "framesDecoded": decoded,
        "marginFrames": margin,
        "model": "pose_landmarker_lite.task",
        "modelSha256": model_sha,
        "mediapipe": mp.__version__,
        "opencv": cv2.__version__,
        "sets": sets,
        "seconds": round(time.time() - started, 1),
    }
    with open(os.path.join(out_dir, workout + ".json"), "w") as handle:
        json.dump(meta, handle, indent=1)
    return workout, len(records), decoded, reported, meta["seconds"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("out")
    parser.add_argument("workouts", nargs="*")
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--shifted", action="store_true",
                        help="centre each window on the decoded frames, using <out>/wXX.shifts.csv")
    a = parser.parse_args()
    os.makedirs(a.out, exist_ok=True)
    workouts = a.workouts or sorted(d for d in os.listdir(os.path.join(a.root, "mm-fit")) if d.startswith("w"))
    jobs = [(a.root, a.out, w, a.shifted) for w in workouts]
    with Pool(a.workers) as pool:
        for workout, n, decoded, reported, seconds in pool.imap_unordered(extract, jobs):
            flag = "" if decoded == reported else f"  FRAME COUNT DIFFERS: decoded {decoded}, reported {reported}"
            print(f"{workout}: {n} frames extracted in {seconds} s{flag}", flush=True)
