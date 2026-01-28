"""
Generate all dual-attack pipeline configurations for 28 attack pairs.
This script creates individual config files for each attack pair combination.
"""

import json
import os
from itertools import combinations

# Define all attacks
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

attack_descriptions = {
    "uc01_random_replay": "Random Replay Attack",
    "uc02_inverse_replay": "Inverse Replay Attack",
    "uc03_masquerade_fault": "Masquerade Fake Fault Attack",
    "uc04_masquerade_normal": "Masquerade Fake Normal Attack",
    "uc05_injection": "Random Injection Attack",
    "uc06_high_stnum_injection": "High StNum Injection Attack",
    "uc07_flooding": "Flooding Attack",
    "uc08_grayhole": "Grayhole Attack"
}

# Generate all combinations of 2 attacks
attack_pairs = list(combinations(attacks, 2))
print(f"Generating configurations for {len(attack_pairs)} attack pairs...")

# Create directories
os.makedirs("config/dual_attack_datasets", exist_ok=True)
os.makedirs("config/dual_attack_models", exist_ok=True)

# Generate dataset and model configs for each pair
for attack1, attack2 in attack_pairs:
    pair_name = f"{attack1}_{attack2}"
    
    # Create simple pattern dataset config
    simple_config = {
        "action": "create_attack_dataset",
        "description": f"Dual-attack dataset - Simple pattern ({attack1}, {attack2})",
        "input": {
            "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
            "verifyBenignData": True
        },
        "output": {
            "directory": f"target/dual_attack_training/{pair_name}",
            "filename": "training_dataset_simple.arff",
            "format": "arff"
        },
        "datasetStructure": {
            "messagesPerSegment": 1000,
            "includeBenignSegment": True,
            "shuffleSegments": False
        },
        "attackSegments": [
            {
                "name": attack1,
                "enabled": True,
                "attackConfig": f"config/attacks/{attack1}.json",
                "description": attack_descriptions[attack1]
            },
            {
                "name": attack2,
                "enabled": True,
                "attackConfig": f"config/attacks/{attack2}.json",
                "description": attack_descriptions[attack2]
            }
        ]
    }
    
    with open(f"config/dual_attack_datasets/{pair_name}_simple.json", "w") as f:
        json.dump(simple_config, f, indent=2)
    
    # Create combined pattern dataset config
    combined_config = {
        "action": "create_attack_dataset",
        "description": f"Dual-attack dataset - Combined pattern ({attack1}, {attack2})",
        "input": {
            "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
            "verifyBenignData": True
        },
        "output": {
            "directory": f"target/dual_attack_training/{pair_name}",
            "filename": "training_dataset_combined.arff",
            "format": "arff"
        },
        "datasetStructure": {
            "messagesPerSegment": 1000,
            "includeBenignSegment": True,
            "shuffleSegments": False
        },
        "attackSegments": [
            {
                "name": attack1,
                "enabled": True,
                "attackConfig": f"config/attacks/{attack1}.json",
                "description": attack_descriptions[attack1]
            },
            {
                "name": attack2,
                "enabled": True,
                "attackConfig": f"config/attacks/{attack2}.json",
                "description": attack_descriptions[attack2]
            },
            {
                "name": f"{attack1}_{attack2}_combined_1",
                "enabled": True,
                "attackConfig": f"config/attacks/{attack1}.json",
                "description": f"{attack_descriptions[attack1]} + {attack_descriptions[attack2]} simultaneous",
                "simultaneousAttack": {
                    "enabled": True,
                    "secondAttackConfig": f"config/attacks/{attack2}.json"
                }
            },
            {
                "name": f"{attack2}_{attack1}_combined_2",
                "enabled": True,
                "attackConfig": f"config/attacks/{attack2}.json",
                "description": f"{attack_descriptions[attack2]} + {attack_descriptions[attack1]} simultaneous",
                "simultaneousAttack": {
                    "enabled": True,
                    "secondAttackConfig": f"config/attacks/{attack1}.json"
                }
            }
        ]
    }
    
    with open(f"config/dual_attack_datasets/{pair_name}_combined.json", "w") as f:
        json.dump(combined_config, f, indent=2)
    
    # Create model training configs
    for pattern in ["simple", "combined"]:
        model_config = {
            "action": "train_model",
            "description": f"Train models on {pair_name} - {pattern} pattern",
            "input": {
                "trainingDatasetPath": f"target/dual_attack_training/{pair_name}/training_dataset_{pattern}.arff",
                "verifyDataset": True
            },
            "output": {
                "modelDirectory": f"target/dual_attack_models/{pair_name}_{pattern}",
                "saveMetadata": True,
                "metadataFilename": "training_metadata.json"
            },
            "classifiers": [
                "J48",
                "RandomForest"
            ],
            "classifierParameters": {
                "j48": {
                    "confidenceFactor": 0.25,
                    "minNumObj": 2
                },
                "randomForest": {
                    "numIterations": 100,
                    "numFeatures": 0
                }
            }
        }
        
        with open(f"config/dual_attack_models/{pair_name}_{pattern}.json", "w") as f:
            json.dump(model_config, f, indent=2)

print(f"Generated {len(attack_pairs) * 4} configuration files:")
print(f"  - {len(attack_pairs) * 2} dataset configs (simple + combined)")
print(f"  - {len(attack_pairs) * 2} model training configs (simple + combined)")

# Generate master pipeline that runs all of them
pipeline_steps = [
    {
        "action": "create_benign",
        "actionConfigFile": "config/actions/action_create_benign.json",
        "description": "Generate baseline benign traffic data"
    }
]

for attack1, attack2 in attack_pairs:
    pair_name = f"{attack1}_{attack2}"
    
    # Add dataset creation steps
    pipeline_steps.append({
        "action": "create_attack_dataset",
        "actionConfigFile": f"config/dual_attack_datasets/{pair_name}_simple.json",
        "description": f"Create dataset: {pair_name} (simple)"
    })
    
    pipeline_steps.append({
        "action": "train_model",
        "actionConfigFile": f"config/dual_attack_models/{pair_name}_simple.json",
        "description": f"Train models: {pair_name} (simple)"
    })
    
    pipeline_steps.append({
        "action": "create_attack_dataset",
        "actionConfigFile": f"config/dual_attack_datasets/{pair_name}_combined.json",
        "description": f"Create dataset: {pair_name} (combined)"
    })
    
    pipeline_steps.append({
        "action": "train_model",
        "actionConfigFile": f"config/dual_attack_models/{pair_name}_combined.json",
        "description": f"Train models: {pair_name} (combined)"
    })

master_pipeline = {
    "action": "pipeline",
    "description": "Complete dual-attack pipeline: All 28 attack pairs with simple and combined patterns",
    "commonConfig": {
        "randomSeed": 42,
        "outputFormat": "arff"
    },
    "pipeline": pipeline_steps
}

with open("config/pipelines/pipeline_dual_attack_all.json", "w") as f:
    json.dump(master_pipeline, f, indent=2)

print(f"\nMaster pipeline created: config/pipelines/pipeline_dual_attack_all.json")
print(f"Total steps in pipeline: {len(pipeline_steps)}")
print(f"  - 1 benign data generation")
print(f"  - {len(attack_pairs) * 4} attack pair steps (2 datasets + 2 training per pair)")
print(f"\nRun with:")
print(f"  java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_dual_attack_all.json")
