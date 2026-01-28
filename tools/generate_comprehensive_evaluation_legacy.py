#!/usr/bin/env python3
"""
Generate comprehensive evaluation configuration for ERENO with LEGACY mode enabled.

This version creates configurations that use the old school (non-C) attack implementations
by setting useLegacy: true in all attack dataset creation steps.

Creates configuration to evaluate:
- 112 models (28 attack pairs × 2 patterns × 2 classifiers)
- 64 test datasets (8 single attacks + 28 dual attacks × 2 patterns)
- Total: 7,168 model-dataset evaluations

Output: CSV with columns trainingAttack1, trainingAttack2, trainingPattern, 
        modelName, testAttack, accuracy, precision, recall, f1
"""

import json
import os
from itertools import combinations

# 8 single attacks
ATTACKS = [
    "uc01_random_replay",
    "uc02_inverse_replay",
    "uc03_masquerade_fault",
    "uc04_masquerade_normal",
    "uc05_injection",
    "uc06_high_stnum_injection",
    "uc07_flooding",
    "uc08_grayhole"
]

# Model types
CLASSIFIERS = ["J48", "RandomForest"]

# Dataset patterns
PATTERNS = ["simple", "combined"]

# USE LEGACY MODE (old school attacks)
USE_LEGACY = True

def generate_attack_pairs():
    """Generate all 28 unique attack pairs (combinations of 8 attacks taken 2 at a time)"""
    return list(combinations(ATTACKS, 2))

def generate_models():
    """Generate all 112 model specifications"""
    models = []
    attack_pairs = generate_attack_pairs()
    
    for attack1, attack2 in attack_pairs:
        for pattern in PATTERNS:
            for classifier in CLASSIFIERS:
                model_dir = f"{attack1}_{attack2}_{pattern}"
                model_file = f"{model_dir}_{classifier}_model.model"
                model_path = f"target/dual_attack_models/{model_dir}/{model_file}"
                
                models.append({
                    "trainingAttack1": attack1,
                    "trainingAttack2": attack2,
                    "trainingPattern": pattern,
                    "modelName": classifier,
                    "modelPath": model_path
                })
    
    return models

def generate_test_datasets():
    """Generate all 64 test dataset specifications
    
    8 single attack datasets + 28 dual attack pairs × 2 patterns = 64 total
    """
    test_datasets = []
    
    # 8 single attack test datasets (no pattern variation for singles)
    for attack in ATTACKS:
        test_datasets.append({
            "testAttack": attack,
            "testDatasetPath": f"target/dual_attack_test_datasets/single/test_{attack}.arff"
        })
    
    # 56 dual attack test datasets (28 pairs × 2 patterns)
    attack_pairs = generate_attack_pairs()
    for attack1, attack2 in attack_pairs:
        # Simple pattern
        test_name_simple = f"{attack1}+{attack2}_simple"
        test_datasets.append({
            "testAttack": test_name_simple,
            "testDatasetPath": f"target/dual_attack_test_datasets/dual/test_{attack1}_{attack2}_simple.arff"
        })
        
        # Combined pattern
        test_name_combined = f"{attack1}+{attack2}_combined"
        test_datasets.append({
            "testAttack": test_name_combined,
            "testDatasetPath": f"target/dual_attack_test_datasets/dual/test_{attack1}_{attack2}_combined.arff"
        })
    
    return test_datasets

def generate_action_config():
    """Generate the comprehensive evaluation action configuration"""
    models = generate_models()
    test_datasets = generate_test_datasets()
    
    config = {
        "action": "comprehensive_evaluate",
        "description": f"Comprehensive evaluation: {len(models)} models × {len(test_datasets)} test datasets = {len(models) * len(test_datasets)} evaluations",
        "input": {
            "models": models,
            "testDatasets": test_datasets,
            "verifyFiles": False  # Set to False to skip verification and continue on missing files
        },
        "output": {
            "csvFilePath": "target/comprehensive_evaluation/comprehensive_results.csv",
            "appendMode": False,
            "includeHeaders": True
        }
    }
    
    return config

def generate_pipeline_config():
    """Generate the pipeline configuration with test dataset creation step"""
    attack_pairs = generate_attack_pairs()
    
    config = {
        "action": "pipeline",
        "description": "Comprehensive Evaluation Pipeline: Create test datasets and evaluate all models (LEGACY MODE)",
        "commonConfig": {
            "randomSeed": 42,
            "outputFormat": "arff"
        },
        "pipeline": [
            {
                "action": "create_test_datasets_single",
                "description": "Step 1: Create 8 single-attack test datasets",
                "loop": {
                    "variationType": "singleAttacks",
                    "values": ATTACKS,
                    "steps": [
                        {
                            "action": "create_attack_dataset",
                            "description": "Create test dataset for ${attackName}",
                            "inline": {
                                "action": "create_attack_dataset",
                                "description": "Generate test dataset for ${attackName}",
                                "input": {
                                    "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
                                    "verifyBenignData": True,
                                    "useLegacy": USE_LEGACY  # <-- LEGACY MODE ENABLED
                                },
                                "output": {
                                    "directory": "target/dual_attack_test_datasets/single",
                                    "filename": "test_${attackName}.arff",
                                    "format": "arff"
                                },
                                "datasetStructure": {
                                    "messagesPerSegment": 10000,
                                    "includeBenignSegment": True,
                                    "shuffleSegments": False,
                                    "binaryClassification": True
                                },
                                "attackSegments": [
                                    {
                                        "name": "${attackName}",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attackName}.json",
                                        "description": "Single attack: ${attackName}"
                                    }
                                ]
                            }
                        }
                    ]
                }
            },
            {
                "action": "create_test_datasets_dual_simple",
                "description": "Step 2a: Create 28 dual-attack test datasets (simple pattern)",
                "loop": {
                    "variationType": "dualAttackPairs",
                    "values": [[a1, a2] for a1, a2 in attack_pairs],
                    "steps": [
                        {
                            "action": "create_attack_dataset",
                            "description": "Create simple test dataset for ${attack1}+${attack2}",
                            "inline": {
                                "action": "create_attack_dataset",
                                "description": "Generate dual simple test dataset for ${attack1}+${attack2}",
                                "input": {
                                    "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
                                    "verifyBenignData": True,
                                    "useLegacy": USE_LEGACY  # <-- LEGACY MODE ENABLED
                                },
                                "output": {
                                    "directory": "target/dual_attack_test_datasets/dual",
                                    "filename": "test_${attack1}_${attack2}_simple.arff",
                                    "format": "arff"
                                },
                                "datasetStructure": {
                                    "messagesPerSegment": 10000,
                                    "includeBenignSegment": True,
                                    "shuffleSegments": False,
                                    "binaryClassification": True
                                },
                                "attackSegments": [
                                    {
                                        "name": "${attack1}",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack1}.json",
                                        "description": "First attack: ${attack1}"
                                    },
                                    {
                                        "name": "${attack2}",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack2}.json",
                                        "description": "Second attack: ${attack2}"
                                    }
                                ]
                            }
                        }
                    ]
                }
            },
            {
                "action": "create_test_datasets_dual_combined",
                "description": "Step 2b: Create 28 dual-attack test datasets (combined pattern - simultaneous attacks)",
                "loop": {
                    "variationType": "dualAttackPairs",
                    "values": [[a1, a2] for a1, a2 in attack_pairs],
                    "steps": [
                        {
                            "action": "create_attack_dataset",
                            "description": "Create combined test dataset for ${attack1}+${attack2}",
                            "inline": {
                                "action": "create_attack_dataset",
                                "description": "Generate dual combined test dataset for ${attack1}+${attack2}",
                                "input": {
                                    "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
                                    "verifyBenignData": True,
                                    "useLegacy": USE_LEGACY  # <-- LEGACY MODE ENABLED
                                },
                                "output": {
                                    "directory": "target/dual_attack_test_datasets/dual",
                                    "filename": "test_${attack1}_${attack2}_combined.arff",
                                    "format": "arff"
                                },
                                "datasetStructure": {
                                    "messagesPerSegment": 10000,
                                    "includeBenignSegment": True,
                                    "shuffleSegments": False,
                                    "binaryClassification": True
                                },
                                "attackSegments": [
                                    {
                                        "name": "${attack1}",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack1}.json",
                                        "description": "First attack: ${attack1}"
                                    },
                                    {
                                        "name": "${attack2}",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack2}.json",
                                        "description": "Second attack: ${attack2}"
                                    },
                                    {
                                        "name": "${attack1}_${attack2}_combined",
                                        "enabled": True,
                                        "attackConfigs": [
                                            "config/attacks/${attack1}.json",
                                            "config/attacks/${attack2}.json"
                                        ],
                                        "description": "Combined simultaneous: ${attack1}+${attack2}"
                                    },
                                    {
                                        "name": "${attack2}_${attack1}_combined",
                                        "enabled": True,
                                        "attackConfigs": [
                                            "config/attacks/${attack2}.json",
                                            "config/attacks/${attack1}.json"
                                        ],
                                        "description": "Combined simultaneous: ${attack2}+${attack1}"
                                    }
                                ]
                            }
                        }
                    ]
                }
            },
            {
                "action": "comprehensive_evaluate",
                "actionConfigFile": "config/actions/action_comprehensive_evaluate.json",
                "description": "Step 3: Evaluate all 112 models against all 64 test datasets"
            }
        ]
    }
    
    return config

def main():
    # Create output directory
    os.makedirs("config/actions", exist_ok=True)
    os.makedirs("config/pipelines", exist_ok=True)
    
    # Generate action config
    action_config = generate_action_config()
    with open("config/actions/action_comprehensive_evaluate.json", "w") as f:
        json.dump(action_config, f, indent=2)
    print(f"✓ Generated action config: config/actions/action_comprehensive_evaluate.json")
    
    # Generate pipeline config
    pipeline_config = generate_pipeline_config()
    with open("config/pipelines/pipeline_comprehensive_evaluation.json", "w") as f:
        json.dump(pipeline_config, f, indent=2)
    print(f"✓ Generated pipeline config: config/pipelines/pipeline_comprehensive_evaluation.json")
    
    # Print summary
    models = generate_models()
    test_datasets = generate_test_datasets()
    print(f"\n{'='*60}")
    print(f"COMPREHENSIVE EVALUATION CONFIGURATION (LEGACY MODE)")
    print(f"{'='*60}")
    print(f"Models:         {len(models)}")
    print(f"Test Datasets:  {len(test_datasets)}")
    print(f"Total Evals:    {len(models) * len(test_datasets):,}")
    print(f"Attack Mode:    LEGACY (non-C)")
    print(f"\nBreakdown:")
    print(f"  - 28 attack pairs × 2 patterns × 2 classifiers = 112 models")
    print(f"  - 8 single attacks + (28 pairs × 2 patterns) = 64 test datasets")
    print(f"\nUsage:")
    print(f"  java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_comprehensive_evaluation.json")

if __name__ == "__main__":
    main()
