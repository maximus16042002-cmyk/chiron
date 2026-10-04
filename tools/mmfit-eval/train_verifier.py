"""P9 Part B: Train and Evaluate the Exercise Verifier - DECISIONS.md P3-52.

Evaluates 4-fold Leave-One-Subject-Out Cross-Validation (LOSOCV) across development
subjects {A, B, C, S19} on MM-Fit dataset.
Trains final model on all development subjects, and exports weights and parity vectors to JSON.
"""
import csv
import json
import os
import sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, f1_score, accuracy_score
from sklearn.preprocessing import StandardScaler

CLASSES = [
    "bicep_curl",
    "jumping_jacks",
    "lateral_raise",
    "lunge",
    "pushup",
    "row",
    "shoulder_press",
    "situp",
    "squat",
    "tricep_extension",
]
CLASS_TO_IDX = {c: i for i, c in enumerate(CLASSES)}

SIGNAL_NAMES = [
    "left_elbow",
    "right_elbow",
    "left_shoulder",
    "right_shoulder",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "trunk_lean",
]

def make_feature_names():
    names = []
    for sig in SIGNAL_NAMES:
        for p in range(16):
            names.append(f"{sig}_{p:02d}")
    names.append("duration_ms")
    return names

def load_data(csv_path):
    print(f"Loading dataset from {csv_path}...")
    subjects = []
    workouts = []
    candidate_profiles = []
    true_exercises = []
    extraction_methods = []
    feature_rows = []

    with open(csv_path, newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        # header format:
        # subject,workout,setIndex,candidateProfile,trueExercise,extractionMethod,repIndex,validity,startMs,endMs,durationMs,f0..f144
        feature_start_idx = 11

        for row in reader:
            if not row:
                continue
            subj = row[0]
            w = row[1]
            cand = row[3]
            true_ex = row[4]
            method = row[5]

            features = [float(x) for x in row[feature_start_idx:]]
            if len(features) != 145:
                continue

            subjects.append(subj)
            workouts.append(w)
            candidate_profiles.append(cand)
            true_exercises.append(true_ex)
            extraction_methods.append(method)
            feature_rows.append(features)

    X = np.array(feature_rows, dtype=np.float64)
    y = np.array([CLASS_TO_IDX[e] for e in true_exercises], dtype=np.int32)
    subjects = np.array(subjects)
    workouts = np.array(workouts)
    candidate_profiles = np.array(candidate_profiles)
    true_exercises = np.array(true_exercises)
    extraction_methods = np.array(extraction_methods)

    print(f"Loaded {len(X)} samples across {len(np.unique(subjects))} subjects: {sorted(np.unique(subjects))}")
    print("Class distribution:")
    for cls in CLASSES:
        count = np.sum(true_exercises == cls)
        print(f"  {cls}: {count}")

    return {
        "X": X,
        "y": y,
        "subjects": subjects,
        "workouts": workouts,
        "candidate_profiles": candidate_profiles,
        "true_exercises": true_exercises,
        "extraction_methods": extraction_methods,
    }

def run_losocv(data):
    X = data["X"]
    y = data["y"]
    subjects = data["subjects"]
    cand_profiles = data["candidate_profiles"]
    true_exs = data["true_exercises"]
    methods = data["extraction_methods"]

    unique_subjects = sorted(np.unique(subjects))
    print(f"\n--- 4-Fold Leave-One-Subject-Out Cross-Validation (LOSOCV) ---")
    print(f"Subjects: {unique_subjects}")

    fold_accuracies = []
    fold_macro_f1s = []
    all_y_true = []
    all_y_pred = []

    matched_total = 0
    matched_warned = 0
    mismatched_total = 0
    mismatched_warned = 0

    PROFILE_TO_CLASS = {
        "squat": "squat",
        "pushup": "pushup",
        "shoulder_press": "shoulder_press",
        "lunge": "lunge",
        "row": "row",
        "situp": "situp",
        "bicep_curl": "bicep_curl",
        "lateral_raise": "lateral_raise",
    }

    for fold, test_subj in enumerate(unique_subjects):
        train_mask = subjects != test_subj
        test_mask = subjects == test_subj

        X_train, y_train = X[train_mask], y[train_mask]
        X_test, y_test = X[test_mask], y[test_mask]

        scaler = StandardScaler()
        X_train_s = scaler.fit_transform(X_train)
        X_test_s = scaler.transform(X_test)

        clf = LogisticRegression(
            solver="lbfgs",
            max_iter=1000,
            C=1.0,
            random_state=2026,
        )
        clf.fit(X_train_s, y_train)

        probs = clf.predict_proba(X_test_s)
        preds = np.argmax(probs, axis=1)

        acc = accuracy_score(y_test, preds)
        macro_f1 = f1_score(y_test, preds, average="macro")
        fold_accuracies.append(acc)
        fold_macro_f1s.append(macro_f1)

        all_y_true.extend(y_test)
        all_y_pred.extend(preds)

        print(f"Fold {fold+1} [Test Subject {test_subj} ({len(X_test)} samples)]: Accuracy={acc*100:.2f}%, Macro-F1={macro_f1*100:.2f}%")

        # Warning evaluation for candidate profile repetitions
        test_cands = cand_profiles[test_mask]
        test_trues = true_exs[test_mask]
        test_methods = methods[test_mask]

        for i in range(len(X_test)):
            if test_methods[i] != "profile_tracker":
                continue
            cand = test_cands[i]
            if cand not in PROFILE_TO_CLASS:
                continue
            selected_cls = PROFILE_TO_CLASS[cand]
            selected_idx = CLASS_TO_IDX[selected_cls]
            p_selected = probs[i, selected_idx]
            pred_idx = preds[i]
            p_pred = probs[i, pred_idx]
            pred_cls = CLASSES[pred_idx]

            is_warning = (p_selected < 0.25) and (p_pred >= 0.50) and (pred_cls != selected_cls)

            if selected_cls == test_trues[i]:
                matched_total += 1
                if is_warning:
                    matched_warned += 1
            else:
                mismatched_total += 1
                if is_warning:
                    mismatched_warned += 1

    print(f"\n--- Overall LOSOCV Results ---")
    print(f"Mean Accuracy: {np.mean(fold_accuracies)*100:.2f}% ± {np.std(fold_accuracies)*100:.2f}%")
    print(f"Mean Macro-F1: {np.mean(fold_macro_f1s)*100:.2f}% ± {np.std(fold_macro_f1s)*100:.2f}%")

    print("\nConfusion Matrix (Pooled LOSOCV):")
    cm = confusion_matrix(all_y_true, all_y_pred, labels=list(range(len(CLASSES))))
    col_w = max(len(c) for c in CLASSES)
    header = " " * (col_w + 2) + " ".join(f"{c[:4]:>5}" for c in CLASSES)
    print(header)
    for i, row in enumerate(cm):
        print(f"{CLASSES[i]:>{col_w}}: " + " ".join(f"{val:>5}" for val in row))

    print("\nPer-Class Classification Report (Pooled LOSOCV):")
    print(classification_report(all_y_true, all_y_pred, target_names=CLASSES, digits=4))

    print("\nMismatch Warning Engine Evaluation (Level 2 Findings):")
    print(f"  Matched Repetitions: {matched_total} total")
    print(f"    False Warnings: {matched_warned} ({matched_warned/max(1, matched_total)*100:.2f}%)")
    print(f"  Mismatched Repetitions: {mismatched_total} total")
    print(f"    Warnings Emitted: {mismatched_warned} ({mismatched_warned/max(1, mismatched_total)*100:.2f}%)")

    return {
        "fold_accuracies": fold_accuracies,
        "fold_macro_f1s": fold_macro_f1s,
        "mean_accuracy": float(np.mean(fold_accuracies)),
        "mean_macro_f1": float(np.mean(fold_macro_f1s)),
        "confusion_matrix": cm.tolist(),
        "matched_total": matched_total,
        "matched_warned": matched_warned,
        "mismatched_total": mismatched_total,
        "mismatched_warned": mismatched_warned,
    }

def train_and_export_final(data, out_dir):
    print("\n--- Training Final Model on all Development Data ---")
    X = data["X"]
    y = data["y"]

    scaler = StandardScaler()
    X_s = scaler.fit_transform(X)

    clf = LogisticRegression(
        solver="lbfgs",
        max_iter=1000,
        C=1.0,
        random_state=2026,
    )
    clf.fit(X_s, y)

    train_acc = accuracy_score(y, clf.predict(X_s))
    print(f"Final Model Train Accuracy on Dev ({len(X)} samples): {train_acc*100:.2f}%")

    feature_names = make_feature_names()

    model_dict = {
        "modelType": "multinomial_logistic_regression",
        "modelVersion": "p9-verifier-1",
        "classCount": len(CLASSES),
        "classes": CLASSES,
        "featureCount": 145,
        "featureNames": feature_names,
        "scalerMean": [float(m) for m in scaler.mean_],
        "scalerScale": [float(s) for s in scaler.scale_],
        "weights": [[float(w) for w in row] for row in clf.coef_],
        "intercepts": [float(b) for b in clf.intercept_],
        "mismatchThresholds": {
            "selectedPMax": 0.25,
            "predictedPMin": 0.50
        }
    }

    # Save to both out_dir and tools/mmfit-eval
    model_path1 = os.path.join(out_dir, "exercise_verifier_model.json")
    with open(model_path1, "w") as f:
        json.dump(model_dict, f, indent=2)
    print(f"Exported model to {model_path1}")

    model_path2 = os.path.join(os.path.dirname(__file__), "exercise_verifier_model.json")
    with open(model_path2, "w") as f:
        json.dump(model_dict, f, indent=2)
    print(f"Exported model to {model_path2}")

    # Generate Parity Test Vectors (50 samples representing all classes)
    print("\nGenerating Parity Test Vectors...")
    parity_vectors = []
    rng = np.random.default_rng(2026)

    for cls_idx, cls_name in enumerate(CLASSES):
        cls_indices = np.where(y == cls_idx)[0]
        chosen = rng.choice(cls_indices, size=min(5, len(cls_indices)), replace=False)
        for idx in chosen:
            raw_features = [float(x) for x in X[idx]]
            x_vec = X[idx:idx+1]
            x_vec_s = scaler.transform(x_vec)
            probs = clf.predict_proba(x_vec_s)[0]
            pred_idx = int(np.argmax(probs))
            parity_vectors.append({
                "sampleIndex": int(idx),
                "subject": str(data["subjects"][idx]),
                "workout": str(data["workouts"][idx]),
                "trueExercise": cls_name,
                "rawFeatures": raw_features,
                "expectedProbabilities": [float(p) for p in probs],
                "expectedPrediction": CLASSES[pred_idx]
            })

    parity_path = os.path.join(out_dir, "verifier_parity_vectors.json")
    with open(parity_path, "w") as f:
        json.dump(parity_vectors, f, indent=2)
    print(f"Exported {len(parity_vectors)} parity test vectors to {parity_path}")

def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    csv_path = os.path.join(root, "data", "verifier", "movement_contexts_dev.csv")
    out_dir = os.path.join(root, "data", "verifier")

    if not os.path.exists(csv_path):
        print(f"Error: {csv_path} does not exist. Run extractVerifierDataset first.")
        sys.exit(1)

    data = load_data(csv_path)
    losocv_results = run_losocv(data)
    train_and_export_final(data, out_dir)

if __name__ == "__main__":
    main()
