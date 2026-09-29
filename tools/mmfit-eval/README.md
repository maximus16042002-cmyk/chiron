# MM-Fit evaluation (P7)

Kinesis's rep counter against MM-Fit's labelled counts, through the phone's own pipeline:
MM-Fit RGB video → MediaPipe Pose (the phone's model and settings) → the frame-shape correction of
CONTRACTS.md §4 → the phone's `LiveRepetitionAnalysis` → P5 exports (CONTRACTS.md §10) → per-set
comparison. What it found, and why it is built this way: `docs/DECISIONS.md` P3-27.

MM-Fit (CC BY 4.0) is expected at `data/mm-fit/` (`mm-fit/wXX/` labels and pose, `video/wXX_rgb.mp4`).
Everything written goes under `data/`, which is not in git.

## Run it

```sh
# 1. MediaPipe over each labelled set (+2 s either side). About 1.5 h of CPU in all;
#    run several workouts at once with --workers, memory allowing (~300 MB each).
python tools/mmfit-eval/extract_landmarks.py data/mm-fit data/mm-fit-mediapipe --workers 5

# 2. Each set's shift between MM-Fit's frame numbers and the decoded video, tracked set by set.
python tools/mmfit-eval/alignment_by_set.py data/mm-fit data/mm-fit-mediapipe w00 w01 ... w20

# 3. Workouts whose shift outgrows the 2 s margin (w16-w20) re-extracted centred on the shift.
python tools/mmfit-eval/extract_landmarks.py data/mm-fit data/mm-fit-mediapipe w16 w17 w18 w19 w20 --workers 5 --shifted

# 4. Every set through the engine, three protocols, P5 exports + sets.csv.
./gradlew :mmfit-eval:runHarness

# 5. The numbers, the flags and the diagnostics.
python tools/mmfit-eval/analyse.py data/mm-fit-eval data/mm-fit-mediapipe --json data/mm-fit-eval/report.json
```

## The pieces

| File | What it does |
|---|---|
| `extract_landmarks.py` | MediaPipe (the phone's `pose_landmarker_lite.task`, one pose, default confidences, VIDEO mode) over each set, frames decoded strictly in order. Raw landmarks, uncorrected. |
| `alignment_by_set.py` | Per set, the lag between MediaPipe's moving joints and MM-Fit's OpenPose track - OpenPose used only as a clock, never fed to the engine (CLAUDE.md §14). |
| `check_alignment.py` | The landmark file reader; a first, whole-workout alignment check. |
| `label_clock.py` | An attempt to tell which numbering the labels use from movement onset. Inconclusive; kept as the record of why the numbering is an assumption. |
| `src/…/MmFit.kt`, `Harness.kt` | The harness: labels, exercise mapping, the frame-shape correction, the three protocols, each set through `LiveRepetitionAnalysis`. |
| `src/…/TraceSet.kt` | A diagnostic: one set's engine state frame by frame (`gradlew :mmfit-eval:traceSet -Pworkout=w00 -Pset=22`). |
| `analyse.py` | Counted against labelled per exercise and protocol, development and held-out apart, with flags, per-side diagnostics, threshold cycles and a measured view index. |
| `trace_set.py` | A Python view of one set's raw angles and ideal threshold cycles. |
| `src/…/NearSideDiagnostic.kt` | For near-side-only profiles: each side's own pipeline and state machine re-run, the engine's combining rule re-created and checked against the harness, and two alternatives counted (`gradlew :mmfit-eval:nearSideDiagnostic`). DECISIONS.md P3-28. |
| `raise_rest_angles.py` | The lateral raise's resting angle between raises, per split - the evidence for its start-zone proposal. DECISIONS.md P3-28. |
| `raise_threshold_check.py` | One held-out check of one proposed lateral-raise start zone, on the ideal threshold counter: a ceiling, not the engine. DECISIONS.md P3-28. |
| `start_zone_angles.py` | Where push-ups and sit-ups return to between repetitions on the near side, per split - the measurement behind P3-29's proposal. DECISIONS.md P3-29. |
| `subject_sheets.py` | Contact sheets of each workout's person, for grouping MM-Fit's workouts by subject - MM-Fit publishes no mapping. Written under `data/` only (`python tools/mmfit-eval/subject_sheets.py data/mm-fit data/mm-fit-mediapipe data/mm-fit-subjects`). DECISIONS.md P3-30. |
