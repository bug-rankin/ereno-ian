"""
Generate a comprehensive evaluation configuration for all trained models.
This script creates a single evaluation config that tests all 112 models against their training datasets.
"""

import json
import itertools
from pathlib import Path

# Define attack pairs (28 combinations)
attacks = [
    "uc01_random_replay",
    "uc02_inverse_replay",
    "uc03_masquerade_fault",
    "uc04_masquerade_normal",
    "uc05_injection",
    "uc06_high_stnum_injection",
    "uc07_flooding",
    "uc08_grayhole"
]

attack_pairs = list(itertools.combinations(attacks, 2))
patterns = ["simple", "combined"]
classifiers = ["J48", "RandomForest"]

# Generate evaluation steps for each model
evaluation_steps = []

for (attack1, attack2), pattern in itertools.product(attack_pairs, patterns):
    model_dir = f"{attack1}_{attack2}_{pattern}"
    test_dataset_path = f"target/dual_attack_training/{attack1}_{attack2}/training_dataset_{pattern}.arff"
    
    # Create evaluation step for this model/dataset combination
    for classifier in classifiers:
        model_path = f"target/dual_attack_models/{model_dir}/{model_dir}_{classifier}_model.model"
        
        step = {
            "action": "evaluate",
            "description": f"Evaluate {model_dir} {classifier} model",
            "input": {
                "models": [{
                    "name": classifier,
                    "modelPath": model_path
                }],
                "testDatasetPath": test_dataset_path,
                "verifyFiles": True
            },
            "output": {
                "evaluationDirectory": "target/dual_attack_evaluation",
                "evaluationFilename": "all_results.json",
                "format": "json",
                "appendMode": True,
                "includeHeaders": True
            },
            "outputMetrics": [
                "accuracy",
                "precision",
                "recall",
                "f1"
            ]
        }
        evaluation_steps.append(step)

# Create the pipeline configuration
pipeline_config = {
    "action": "pipeline",
    "description": "Comprehensive evaluation of all dual-attack trained models",
    "pipeline": evaluation_steps
}

# Write the configuration
output_path = Path("config/pipelines/pipeline_full_evaluation_generated.json")
output_path.parent.mkdir(parents=True, exist_ok=True)

with open(output_path, 'w') as f:
    json.dump(pipeline_config, f, indent=2)

print(f"Generated evaluation pipeline with {len(evaluation_steps)} evaluation steps")
print(f"Output: {output_path}")
print(f"Testing {len(attack_pairs)} attack pairs × {len(patterns)} patterns × {len(classifiers)} classifiers = {len(evaluation_steps)} total evaluations")
