#!/usr/bin/env python3
"""
Generate the UC10 delayed-replay variant cross-evaluation pipeline + action configs.

Produces:
  - config/pipelines/pipeline_uc10_variant_evaluation.json
  - config/actions/action_uc10_variant_evaluate.json

Experiment design:
  - 8 attack variants = 4 base UC10 configs x replaceWithFake in {real, fake}
  - 3 training seeds per variant x 2 classifiers = 48 trained models total
  - 10 test seeds per variant = 80 test datasets total
  - Cross-evaluation = 48 x 80 = 3,840 evaluations (full cross, includes self-variant)
"""

import json
import os
from pathlib import Path

# --- Experiment configuration -----------------------------------------------

VARIANTS = [
    "uc10_replay_real",
    "uc10_replay_fake",
    "uc10_backoff_real",
    "uc10_backoff_fake",
    "uc10_batchdump_real",
    "uc10_batchdump_fake",
    "uc10_doubledrop_real",
    "uc10_doubledrop_fake",
]

CLASSIFIERS = ["J48", "RandomForest"]
TRAIN_SEEDS = [42, 43, 44]
TEST_SEEDS = list(range(100, 110))  # 100..109

ATTACK_MESSAGES = 100_000
BENIGN_MESSAGES = 150_000

# Tag stored in the trainingPattern column so this experiment's rows are easy
# to filter and don't collide with existing 'simple'/'combined' analyzers.
TRAINING_PATTERN_TAG = "uc10_variant"

BENIGN_DATA_PATH = "target/benign_data/42_5%fault_benign_data.arff"
ATTACK_CONFIG_DIR = "config/attacks/uc10_variants"

TRAIN_DATA_DIR = "target/uc10_variants/training"
MODEL_DIR = "target/uc10_variants/models"
TEST_DATA_DIR = "target/uc10_variants/test_datasets"
RESULTS_CSV = "target/uc10_variants/comprehensive_results.csv"

PIPELINE_OUTPUT = "config/pipelines/pipeline_uc10_variant_evaluation.json"
ACTION_OUTPUT = "config/actions/action_uc10_variant_evaluate.json"

# --- Pipeline-step builders -------------------------------------------------

def _classifier_params():
    return {
        "j48": {"confidenceFactor": 0.25, "minNumObj": 2},
        "randomForest": {"numIterations": 100, "numFeatures": 0},
    }

def _attack_segment(variant):
    return [{
        "name": variant,
        "enabled": True,
        "attackConfig": f"{ATTACK_CONFIG_DIR}/{variant}.json",
        "description": f"UC10 variant: {variant}",
    }]

def _dataset_structure():
    return {
        "messagesPerSegment": ATTACK_MESSAGES,
        "benignMessagesPerSegment": BENIGN_MESSAGES,
        "includeBenignSegment": True,
        "shuffleSegments": False,
        "binaryClassification": True,
    }

def build_train_step(variant):
    """One nested-loop pipeline step covering 3 training seeds for one variant.

    Each iteration creates a training dataset and trains both classifiers."""
    return {
        "action": "create_attack_dataset",
        "description": f"Train models for variant {variant} (loop over training seeds)",
        "loop": {
            "variationType": "randomSeed",
            "values": TRAIN_SEEDS,
            "steps": [
                {
                    "action": "create_attack_dataset",
                    "description": f"Generate training data for {variant} seed=${{seed}}",
                    "inline": {
                        "action": "create_attack_dataset",
                        "input": {
                            "benignDataPath": BENIGN_DATA_PATH,
                            "verifyBenignData": True,
                        },
                        "output": {
                            "directory": f"{TRAIN_DATA_DIR}/{variant}_seed${{seed}}",
                            "filename": "training.arff",
                            "format": "arff",
                        },
                        "datasetStructure": _dataset_structure(),
                        "attackSegments": _attack_segment(variant),
                    },
                },
                {
                    "action": "train_model",
                    "description": f"Train J48+RandomForest on {variant} seed=${{seed}}",
                    "inline": {
                        "action": "train_model",
                        "input": {
                            "trainingDatasetPath":
                                f"{TRAIN_DATA_DIR}/{variant}_seed${{seed}}/training.arff",
                            "verifyDataset": True,
                        },
                        "output": {
                            "modelDirectory": f"{MODEL_DIR}/{variant}_seed${{seed}}",
                            "saveMetadata": True,
                            "metadataFilename": "training_metadata.json",
                        },
                        "classifiers": CLASSIFIERS,
                        "classifierParameters": _classifier_params(),
                    },
                },
            ],
        },
    }

def build_test_step(variant):
    """One nested-loop pipeline step generating 10 seed-varied test datasets."""
    return {
        "action": "create_attack_dataset",
        "description": f"Generate test datasets for variant {variant} (loop over test seeds)",
        "loop": {
            "variationType": "randomSeed",
            "values": TEST_SEEDS,
            "steps": [
                {
                    "action": "create_attack_dataset",
                    "description": f"Generate test dataset for {variant} seed=${{seed}}",
                    "inline": {
                        "action": "create_attack_dataset",
                        "input": {
                            "benignDataPath": BENIGN_DATA_PATH,
                            "verifyBenignData": True,
                        },
                        "output": {
                            "directory": TEST_DATA_DIR,
                            "filename": f"test_{variant}_seed${{seed}}.arff",
                            "format": "arff",
                        },
                        "datasetStructure": _dataset_structure(),
                        "attackSegments": _attack_segment(variant),
                    },
                },
            ],
        },
    }

# --- Pipeline + action assembly ---------------------------------------------

def build_pipeline_config():
    pipeline_steps = [
        {
            "action": "create_benign",
            "actionConfigFile": "config/actions/action_create_benign.json",
            "description": "Generate baseline benign traffic data",
        },
    ]
    pipeline_steps += [build_train_step(v) for v in VARIANTS]
    pipeline_steps += [build_test_step(v) for v in VARIANTS]
    pipeline_steps.append({
        "action": "comprehensive_evaluate",
        "actionConfigFile": ACTION_OUTPUT,
        "description":
            f"Cross-evaluate {len(VARIANTS) * len(TRAIN_SEEDS) * len(CLASSIFIERS)} models "
            f"x {len(VARIANTS) * len(TEST_SEEDS)} test datasets",
    })

    return {
        "action": "pipeline",
        "description": (
            "UC10 delayed-replay variant cross-evaluation: "
            f"{len(VARIANTS)} variants x {len(TRAIN_SEEDS)} train seeds x "
            f"{len(CLASSIFIERS)} classifiers vs {len(VARIANTS)} variants x "
            f"{len(TEST_SEEDS)} test seeds"
        ),
        "commonConfig": {
            "randomSeed": 42,
            "outputFormat": "arff",
        },
        "pipeline": pipeline_steps,
    }

def build_action_config():
    models = []
    for variant in VARIANTS:
        for seed in TRAIN_SEEDS:
            for clf in CLASSIFIERS:
                model_dir_name = f"{variant}_seed{seed}"
                model_filename = f"{model_dir_name}_{clf}_model.model"
                models.append({
                    "trainingAttack1": variant,
                    "trainingAttack2": "",
                    "trainingPattern": TRAINING_PATTERN_TAG,
                    "modelName": clf,
                    "trainingSeed": str(seed),
                    "modelPath": f"{MODEL_DIR}/{model_dir_name}/{model_filename}",
                })

    test_datasets = []
    for variant in VARIANTS:
        for seed in TEST_SEEDS:
            test_datasets.append({
                "testAttack": variant,
                "testSeed": str(seed),
                "testDatasetPath": f"{TEST_DATA_DIR}/test_{variant}_seed{seed}.arff",
            })

    return {
        "action": "comprehensive_evaluate",
        "description": (
            f"UC10 variant cross-evaluation: {len(models)} models x "
            f"{len(test_datasets)} test datasets = {len(models) * len(test_datasets)} evaluations"
        ),
        "input": {
            "models": models,
            "testDatasets": test_datasets,
            "verifyFiles": False,
        },
        "output": {
            "csvFilePath": RESULTS_CSV,
            "appendMode": False,
            "includeHeaders": True,
        },
    }

def _write_json(path, payload):
    Path(os.path.dirname(path)).mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2)

def main():
    pipeline = build_pipeline_config()
    action = build_action_config()

    _write_json(PIPELINE_OUTPUT, pipeline)
    _write_json(ACTION_OUTPUT, action)

    n_models = len(action["input"]["models"])
    n_tests = len(action["input"]["testDatasets"])
    n_steps = len(pipeline["pipeline"])

    print(f"Wrote {PIPELINE_OUTPUT}")
    print(f"  - {n_steps} top-level pipeline steps "
          f"(1 benign + {len(VARIANTS)} train + {len(VARIANTS)} test + 1 evaluate)")
    print(f"Wrote {ACTION_OUTPUT}")
    print(f"  - {n_models} models, {n_tests} test datasets")
    print(f"  - {n_models * n_tests} total evaluations")

if __name__ == "__main__":
    main()
