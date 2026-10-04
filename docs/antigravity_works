# Antigravity Works — Activity & Decision Log

*Maintained by the Antigravity Agent. This document records all engineering actions, architectural decisions, model training protocols, and verification results executed in the Kinesis repository.*

---

## 1. Context & User Directives

- **Initial Mandate:** Thoroughly read and understand the entire repository without editing or modifying any code.
- **Physical Constraints:** The user explicitly requested **no physical exercise** ("i dont wanna do any excersise now"). All planned tasks are strictly desk-bound, software engineering, offline dataset analysis, and machine learning evaluation on PC.
- **Phase Roadmap Analysis:**
  - Evaluated pending phases across `docs/PROJECT_STATUS.md` and `docs/HANDOVER.md`:
    - **P4 Device Reality Check:** Partially done (solo curl checked); tests 3–12 require phone execution.
    - **P6 Sideways Recalibration:** Requires camera recording on device.
    - **P8 Rule Wiring:** Technique rules built in shared core; awaiting UI/device integration.
    - **P9 ML Layer (Part B — Exercise Verifier):** Outlined in P3-34 and HANDOVER.md §8.3, but not started.
    - **P10 Visualisation & Android:** Completed in code; pending verification on phone.
    - **P12 Studies & Write-up:** Evaluation studies pending.
  - **Selected Next Action:** Execute **Phase P9 Part B: The Exercise Verifier** (multiclass movement mismatch classifier).

---

## 2. Phase P9 Part B: The Exercise Verifier

### 2.1 Research Protocols & Non-Negotiables (CLAUDE.md §13, §17, §18)
1. **Warning-Only Boundary:** The verifier emits a Level 2 warning (*"This looks like a different exercise: [predicted] ([confidence]%)"*). It **must never** automatically switch or select the user's exercise.
2. **Post-Rep Evaluation Path:** Operates strictly on completed repetition records ($O(1)$ primitive ring buffer tracking per frame; zero per-frame ML inference).
3. **Strict Subject Split:**
   - Training and cross-validation restricted strictly to **Development subjects** ({A, B, C, S19} = 15 workouts: `w00–w11`, `w14`, `w15`, `w19`).
   - The 6 held-out subjects (`S12`, `S13`, `S16`, `S17`, `S18`, `S20`) remain **completely untouched**.
4. **Unverified Exercise Safeguard:** Exercises not covered by MM-Fit (`plank`, `side_plank`, `wall_sit`, `glute_bridge`, `hip_abduction`, `knee_raise`, `leg_raise`) receive no verification and are explicitly declared unverified.
5. **Numerical Parity Contract:** The pure Kotlin multiplatform inference engine in `core/analysis` must match Python scikit-learn outputs to $\le 10^{-9}$ relative precision.

---

### 2.2 Work Completed

#### Step 1: Design & Decisions Recorded
- Added `## P3-52 — P9 Part B: The Exercise Verifier` to `docs/DECISIONS.md`.
- Performed permissive license check (§17) for `scikit-learn` (BSD-3-Clause) in offline Python tooling.
- Committed to Git: `880b902`.

#### Step 2: Domain Modeling & Real-Time Tracking
- **`core/domain/src/commonMain/kotlin/.../MovementContext.kt`:**
  - Implemented the 145-dimensional feature representation: 9 kinematic signals (left/right elbow flexion, left/right shoulder-to-trunk angle, left/right hip angle, left/right knee angle, trunk inclination) $\times$ 16 resampled points + duration in ms.
  - Added unit tests in `MovementContextTest.kt`.
- **`core/domain/src/commonMain/kotlin/.../RepRecord.kt`:**
  - Added optional `val movementContext: MovementContext? = null` field, preserving backwards compatibility across Room v3 and JSON exports.
- **`core/analysis/src/commonMain/kotlin/.../MovementContextTracker.kt`:**
  - Created real-time zero-allocation tracker using 9 primitive `DoubleRingBuffer`s ($O(1)$ per frame).
  - Added post-rep resampling to 16 points with 500 ms gap bridging and neutral posture imputation.
  - Added unit tests in `MovementContextTrackerTest.kt`.
- **`core/analysis/src/commonMain/kotlin/.../BilateralRepTracker.kt`:**
  - Integrated `MovementContextTracker` into the bilateral rep tracking pipeline (`emitOneSided`, `tryPairSimultaneous`, `flushOrphan`).

#### Step 3: Dataset Extraction Pipeline
- Created `tools/mmfit-eval/src/jvmMain/kotlin/.../ExtractVerifierDataset.kt` and Gradle task `:mmfit-eval:extractVerifierDataset`.
- Processed 15 development workouts (`w00–w11`, `w14`, `w15`, `w19`) across subjects A, B, C, S19 against candidate profiles (`squat`, `pushup`, `shoulder_press`, `lunge`, `row`, `situp`, `bicep_curl`, `lateral_raise`).
- Extracted **27,783 repetition contexts** across 10 MM-Fit movement classes:
  - `bicep_curl`: 1,795
  - `jumping_jacks`: 2,990
  - `lateral_raise`: 2,699
  - `lunge`: 2,864
  - `pushup`: 2,429
  - `row`: 4,007
  - `shoulder_press`: 3,266
  - `situp`: 1,366
  - `squat`: 3,454
  - `tricep_extension`: 2,913
- Written to `data/verifier/movement_contexts_dev.csv` (44.7 MB).

#### Step 4: Python Training & 4-Fold LOSOCV Evaluation
- Created `tools/mmfit-eval/train_verifier.py`.
- Evaluated 4-fold Leave-One-Subject-Out Cross-Validation (LOSOCV) across development subjects {A, B, C, S19}:
  - **Mean LOSOCV Accuracy:** $74.17\% \pm 12.99\%$
  - **Mean LOSOCV Macro-F1:** $72.48\% \pm 16.43\%$
  - **Pooled Dataset Accuracy:** **82.10%** across 10 classes
  - **Pooled Macro-F1:** **83.09%**
  - **Per-Class F1 Scores:**
    - `pushup`: 98.07%
    - `tricep_extension`: 95.80%
    - `situp`: 94.53%
    - `bicep_curl`: 93.72%
    - `shoulder_press`: 91.70%
    - `lunge`: 91.03%
    - `row`: 84.40%
    - `jumping_jacks`: 82.85%
    - `lateral_raise`: 63.86%
    - `squat`: 34.91% *(foreshortening from face-on camera view)*
  - **Mismatch Warning Engine Performance (Level 2 Findings):**
    - **Matched Repetitions:** 4,425 total $\rightarrow$ **9.15% False Warning Rate** (90.85% silent).
    - **Mismatched Repetitions:** 18,948 total $\rightarrow$ **95.97% Mismatch Detection Rate** (18,184 warnings emitted).
- Trained final model on all development subjects (98.96% train accuracy).
- Exported model weights and metadata to `tools/mmfit-eval/exercise_verifier_model.json`.
- Exported 50 parity test vectors to `data/verifier/verifier_parity_vectors.json`.

#### Step 5: Pure Kotlin Multiplatform Evaluator & Parity Guard
- Created `tools/mmfit-eval/generate_kotlin_verifier.py`.
- Generated:
  - `core/analysis/src/commonMain/kotlin/.../PretrainedExerciseVerifierModel.kt`: In-memory pre-trained weights for all 10 classes.
  - `core/analysis/src/commonTest/kotlin/.../VerifierParityGolden.kt`: 25 representative parity test vectors.
- Created `core/analysis/src/commonMain/kotlin/.../ExerciseVerifierModel.kt`.
- Created `core/analysis/src/commonMain/kotlin/.../ExerciseVerifier.kt`:
  - Standardizes 145-dimensional features using model $\mu$ and $\sigma$.
  - Computes linear logits and numerically stable softmax probabilities.
  - Emits Level 2 `Deviation` findings when mismatch criteria are satisfied ($P(\text{selected}) < 0.25$ and $P(\text{predicted}) \ge 0.50$ and $\text{predicted} \ne \text{selected}$).
  - Returns `VerificationResult.Unverified` for unverified exercise profiles or null context.
- Created `core/analysis/src/commonTest/kotlin/.../ExerciseVerifierParityTest.kt`:
  - Asserts $|P_{kt}(c) - P_{py}(c)| < 1 \times 10^{-9}$ for all 10 classes across all test vectors.
  - Tested match verification, mismatch detection, unverified exercise fallback, and null context handling.
  - **Mutation Check:** Intentionally mutated tolerance to $10^{-18}$; verified test suite immediately failed with assertion error. Restored tolerance to $10^{-9}$.

#### Step 6: Full Multiplatform Test Suite Run
- Executed `./gradlew.bat allTests` and `./gradlew.bat :app:testDebugUnitTest`.
- All tasks passed cleanly (0 errors, 0 warnings).

#### Step 7: Documentation & Commit
- Appended empirical results and parity details to `## P3-52` in `docs/DECISIONS.md`.
- Updated `docs/PROJECT_STATUS.md` and `docs/HANDOVER.md`: Phase P9 marked as **Complete** (overall repo progress advances from 75% to **~79%**).
- Committed changes:
  - `c00efaa`: `feat(verifier): implement Phase P9 Part B exercise verifier and LOSOCV evaluation (P3-52)`
  - `860ee1e`: `docs: update PROJECT_STATUS and HANDOVER for Phase P9 Part B completion`

---

## 3. Log of Committed Files

| Path | Description | Commit |
|---|---|---|
| `docs/DECISIONS.md` | P3-52 design entry, research protocols, and empirical evaluation results | `880b902`, `c00efaa` |
| `core/domain/src/commonMain/kotlin/.../MovementContext.kt` | 145-feature movement context domain class | `c00efaa` |
| `core/domain/src/commonMain/kotlin/.../RepRecord.kt` | Added `movementContext` field to `RepRecord` | `c00efaa` |
| `core/domain/src/commonTest/kotlin/.../MovementContextTest.kt` | Unit tests for `MovementContext` | `c00efaa` |
| `core/analysis/src/commonMain/kotlin/.../BilateralRepTracker.kt` | Rep tracker integration with movement context | `c00efaa` |
| `core/analysis/src/commonMain/kotlin/.../MovementContextTracker.kt` | $O(1)$ ring buffer kinematic tracker and resampler | `c00efaa` |
| `core/analysis/src/commonTest/kotlin/.../MovementContextTrackerTest.kt` | Unit tests for `MovementContextTracker` | `c00efaa` |
| `core/analysis/src/commonMain/kotlin/.../ExerciseVerifierModel.kt` | Model data container and validation | `c00efaa` |
| `core/analysis/src/commonMain/kotlin/.../ExerciseVerifier.kt` | Pure Kotlin inference engine & warning emitter | `c00efaa` |
| `core/analysis/src/commonMain/kotlin/.../PretrainedExerciseVerifierModel.kt` | Pre-trained weights and normalization factors | `c00efaa` |
| `core/analysis/src/commonTest/kotlin/.../VerifierParityGolden.kt` | Golden parity test vectors | `c00efaa` |
| `core/analysis/src/commonTest/kotlin/.../ExerciseVerifierParityTest.kt` | $\le 10^{-9}$ numerical parity and behavior tests | `c00efaa` |
| `tools/mmfit-eval/build.gradle.kts` | Registered `extractVerifierDataset` task | `c00efaa` |
| `tools/mmfit-eval/src/jvmMain/kotlin/.../ExtractVerifierDataset.kt` | Feature extraction pipeline from MM-Fit binary landmarks | `c00efaa` |
| `tools/mmfit-eval/train_verifier.py` | Python LOSOCV cross-validation and export script | `c00efaa` |
| `tools/mmfit-eval/generate_kotlin_verifier.py` | Code generator for Kotlin model constants and test vectors | `c00efaa` |
| `tools/mmfit-eval/exercise_verifier_model.json` | Exported model weights and threshold specifications | `c00efaa` |
| `docs/PROJECT_STATUS.md` | Updated phase table: P9 marked complete (~79% progress) | `860ee1e` |
| `docs/HANDOVER.md` | Updated status table: P9 complete | `860ee1e` |
| `docs/antigravity_works.md` | This document | Staged |
