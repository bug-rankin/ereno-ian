#!/usr/bin/env python3
"""
Generate comprehensive evaluation configuration for ERENO.

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

# Attack implementation mode
# Set to True to use old school (non-C) attacks with hardcoded parameters
# Set to False to use configurable (C) attacks that read from config/attacks/*.json
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
        "description": "Comprehensive Evaluation Pipeline: Create test datasets and evaluate all models",
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
                                    "useLegacy": USE_LEGACY,
                                    "useLegacy": USE_LEGACY
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
                                    "useLegacy": USE_LEGACY
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
                                    "useLegacy": USE_LEGACY
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
                                        "name": "${attack1}_${attack2}_combined_1",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack1}.json",
                                        "description": "${attack1} + ${attack2} simultaneous (first order)",
                                        "simultaneousAttack": {
                                            "enabled": True,
                                            "secondAttackConfig": "config/attacks/${attack2}.json"
                                        }
                                    },
                                    {
                                        "name": "${attack2}_${attack1}_combined_2",
                                        "enabled": True,
                                        "attackConfig": "config/attacks/${attack2}.json",
                                        "description": "${attack2} + ${attack1} simultaneous (second order)",
                                        "simultaneousAttack": {
                                            "enabled": True,
                                            "secondAttackConfig": "config/attacks/${attack1}.json"
                                        }
                                    }
                                ]
                            }
                        }
                    ]
                }
            },
            {
                "action": "comprehensive_evaluate",
                "description": "Step 3: Evaluate all 112 models against all 64 test datasets (7,168 evaluations)",
                "actionConfigFile": "config/actions/action_comprehensive_evaluate.json"
            }
        ]
    }
    
    return config

def main():
    """Generate and save configuration files"""
    
    # Generate action config
    action_config = generate_action_config()
    action_config_path = "config/actions/action_comprehensive_evaluate.json"
    
    os.makedirs(os.path.dirname(action_config_path), exist_ok=True)
    
    with open(action_config_path, 'w') as f:
        json.dump(action_config, f, indent=2)
    
    print(f"✓ Generated action config: {action_config_path}")
    print(f"  - Models: {len(action_config['input']['models'])}")
    print(f"  - Test datasets: {len(action_config['input']['testDatasets'])}")
    print(f"  - Total evaluations: {len(action_config['input']['models']) * len(action_config['input']['testDatasets'])}")
    
    # Generate pipeline config
    pipeline_config = generate_pipeline_config()
    pipeline_config_path = "config/pipelines/pipeline_comprehensive_evaluation.json"
    
    os.makedirs(os.path.dirname(pipeline_config_path), exist_ok=True)
    
    with open(pipeline_config_path, 'w') as f:
        json.dump(pipeline_config, f, indent=2)
    
    print(f"\n✓ Generated pipeline config: {pipeline_config_path}")
    print(f"  - Pipeline steps: {len(pipeline_config['pipeline'])}")
    
    # Print statistics
    attack_mode = "LEGACY (non-C)" if USE_LEGACY else "CONFIGURABLE (C)"
    print("\n" + "="*60)
    print("COMPREHENSIVE EVALUATION STATISTICS")
    print("="*60)
    print(f"Attack mode: {attack_mode}")
    print(f"Single attacks: {len(ATTACKS)}")
    print(f"Attack pairs (C(8,2)): {len(list(combinations(ATTACKS, 2)))}")
    print(f"Dataset patterns: {len(PATTERNS)}")
    print(f"Classifiers: {len(CLASSIFIERS)}")
    print(f"\nModels breakdown:")
    print(f"  - {len(list(combinations(ATTACKS, 2)))} attack pairs")
    print(f"  - × {len(PATTERNS)} patterns (simple, combined)")
    print(f"  - × {len(CLASSIFIERS)} classifiers (J48, RandomForest)")
    print(f"  = {len(action_config['input']['models'])} total models")
    print(f"\nTest datasets breakdown:")
    print(f"  - {len(ATTACKS)} single attack datasets")
    print(f"  - {len(list(combinations(ATTACKS, 2)))} dual attack pairs × {len(PATTERNS)} patterns")
    print(f"  = {len(action_config['input']['testDatasets'])} total test datasets")
    print(f"\nTotal evaluations: {len(action_config['input']['models'])} models × {len(action_config['input']['testDatasets'])} datasets")
    print(f"  = {len(action_config['input']['models']) * len(action_config['input']['testDatasets'])} evaluations")
    print("="*60)

if __name__ == "__main__":
    main()
