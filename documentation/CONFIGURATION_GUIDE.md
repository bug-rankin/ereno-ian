# ERENO Configuration Guide

## Overview

ERENO uses a flexible, JSON-based configuration system that supports individual actions, sequential pipelines, and parameterized loops. This guide provides comprehensive documentation for all configuration options.

## Configuration File Structure

All ERENO configurations are JSON files with a common top-level structure:

```json
{
  "action": "<action-type>",
  "description": "<human-readable description>",
  "actionConfigFile": "<path-to-action-config>",  // For pipeline orchestration
  "commonConfig": { /* shared settings */ },
  // Action-specific configuration follows...
}
```

## Action Types

### Valid Action Values
- `create_benign` - Generate benign (legitimate) traffic
- `create_attack_dataset` - Create labeled attack datasets
- `train_model` - Train ML classifiers
- `evaluate` - Evaluate trained models
- `compare` - Compare datasets
- `pipeline` - Execute multiple actions sequentially

## Configuration by Action

### 1. Create Benign Configuration

**Purpose**: Generate legitimate GOOSE protocol traffic

**File**: `config/actions/action_create_benign.json`

**Schema**:
```json
{
  "action": "create_benign",
  "description": "Generate benign traffic dataset",
  
  "output": {
    "directory": "target/benign_data",          // Output directory
    "formats": ["arff", "csv"],                 // Output formats (array)
    "filenamePrefix": null,                     // Optional: custom filename prefix
    "enableTracking": true,                     // Enable database tracking
    "experimentId": null                        // Optional: link to experiment
  },
  
  "generation": {
    "numberOfMessages": 40000,                  // Total messages to generate
    "faultProbability": 5                       // Fault probability (0-100%)
  },
  
  "gooseFlow": {
    "goID": "IntLockA",                        // GOOSE identifier
    "ethSrc": "01:0c:cd:01:2f:80",            // Source MAC address
    "ethDst": "01:0c:cd:01:2f:81",            // Destination MAC address
    "ethType": "0x88B8",                       // Ethernet type
    "gooseAppid": "0x00003001",               // GOOSE APPID
    "TPID": "0x8100",                         // VLAN Tag Protocol ID
    "ndsCom": false,                          // Needs Commissioning flag
    "test": false,                            // Test mode flag
    "cbstatus": false                         // Circuit breaker status
  },
  
  "setupIED": {
    "iedName": "Protection IED",              // IED name
    "gocbRef": "LD/LLN0$GO$gcblA",           // GOOSE Control Block reference
    "datSet": "LD/LLN0$IntLockA",            // Dataset reference
    "minTime": "4",                           // Min time between messages (ms)
    "maxTime": "1000",                        // Max time between messages (ms)
    "timestamp": "1",                         // Initial timestamp
    "stNum": "1",                             // Initial status number
    "sqNum": "1"                              // Initial sequence number
  }
}
```

**Key Parameters**:
- `numberOfMessages`: Controls dataset size (typical: 10000-100000)
- `faultProbability`: Simulates fault conditions (typical: 1-10%)
- `formats`: Can include "arff", "csv", or both

### 2. Create Attack Dataset Configuration

**Purpose**: Generate labeled datasets with benign and attack traffic

**File**: `config/actions/action_create_attack_dataset.json`

**Schema**:
```json
{
  "action": "create_attack_dataset",
  "description": "Generate labeled attack dataset",
  
  "input": {
    "benignDataPath": "target/benign_data/42_5%fault_benign_data.arff",
    "verifyBenignData": true                   // Verify file exists
  },
  
  "output": {
    "directory": "target/training",
    "filename": "training_dataset.arff",
    "format": "arff",                          // "arff" or "csv"
    "enableTracking": true,
    "experimentId": null
  },
  
  "datasetStructure": {
    "messagesPerSegment": 1000,                // Messages per attack segment
    "includeBenignSegment": true,              // Include pure benign segment
    "shuffleSegments": false                   // Randomize segment order
  },
  
  "attackSegments": [
    {
      "name": "uc01_random_replay",            // Segment identifier
      "enabled": true,                         // Enable/disable this attack
      "attackConfig": "config/attacks/uc01_random_replay.json",
      "description": "Random replay attack"
    },
    {
      "name": "uc02_inverse_replay",
      "enabled": true,
      "attackConfig": "config/attacks/uc02_inverse_replay.json",
      "description": "Inverse replay attack"
    },
    // ... more attack segments
    {
      "name": "combination_attack",            // Attack combination
      "enabled": true,
      "attacks": ["uc01_random_replay", "uc05_injection"],  // Multiple attacks
      "attackConfigs": [
        "config/attacks/uc01_random_replay.json",
        "config/attacks/uc05_injection.json"
      ],
      "description": "Combined attack scenario"
    }
  ]
}
```

**Attack Segment Types**:
1. **Single Attack**: Specify `attackConfig` for one attack type
2. **Combination Attack**: Specify `attacks` array and `attackConfigs` array for multiple simultaneous attacks

**Available Attacks**:
- `uc01_random_replay` - Random replay attack
- `uc02_inverse_replay` - Inverse replay attack
- `uc03_masquerade_fault` - Masquerade fake fault
- `uc04_masquerade_normal` - Masquerade fake normal operation
- `uc05_injection` - Random message injection
- `uc06_high_stnum` - High sequence number injection
- `uc07_flooding` - Message flooding
- `uc08_grayhole` - Grayhole (message dropping)

**Key Parameters**:
- `messagesPerSegment`: Controls attack sample size (typical: 500-2000)
- `includeBenignSegment`: Always include for balanced datasets
- `shuffleSegments`: Enable for randomized training order

### 3. Train Model Configuration

**Purpose**: Train machine learning classifiers on attack datasets

**File**: `config/actions/action_train_model.json`

**Schema**:
```json
{
  "action": "train_model",
  "description": "Train ML classifiers",
  
  "input": {
    "trainingDatasetPath": "target/training/training_dataset.arff",
    "verifyDataset": true
  },
  
  "output": {
    "modelDirectory": "target/models",
    "saveMetadata": true,
    "metadataFilename": "training_metadata.json",
    "enableTracking": true,
    "experimentId": null,
    "trainingDatasetId": null                  // Optional: link to tracked dataset
  },
  
  "classifiers": [
    "J48",                                     // Decision tree
    "RandomForest",                            // Random forest
    "NaiveBayes",                              // Naive Bayes
    "REPTree",                                 // Reduced Error Pruning Tree
    "IBk"                                      // k-Nearest Neighbors
  ],
  
  "classifierParameters": {
    "j48": {
      "confidenceFactor": 0.25,                // Pruning confidence (0.0-1.0)
      "minNumObj": 2                           // Min instances per leaf
    },
    "randomForest": {
      "numIterations": 100,                    // Number of trees
      "numFeatures": 0                         // Features per tree (0=auto: sqrt)
    },
    "ibk": {
      "k": 1                                   // Number of neighbors
    }
  }
}
```

**Supported Classifiers**:
- **J48**: C4.5 decision tree (interpretable, fast)
- **RandomForest**: Ensemble method (high accuracy, robust)
- **NaiveBayes**: Probabilistic classifier (fast, simple)
- **REPTree**: Fast decision tree (reduced error pruning)
- **IBk**: k-Nearest Neighbors (instance-based)

**Hyperparameter Guidelines**:

**J48**:
- `confidenceFactor`: Lower = more pruning (0.1-0.5)
- `minNumObj`: Higher = more general tree (1-10)

**RandomForest**:
- `numIterations`: More trees = better accuracy but slower (50-200)
- `numFeatures`: 0 = auto (sqrt of total features)

**IBk**:
- `k`: Odd numbers recommended (1, 3, 5)

### 4. Evaluate Configuration

**Purpose**: Evaluate trained models on test datasets

**File**: `config/actions/action_evaluate.json`

**Schema**:
```json
{
  "action": "evaluate",
  "description": "Evaluate trained models",
  
  "input": {
    "modelsDirectory": "target/models",
    "testDatasetPath": "target/evaluation_test/test_dataset.arff",
    "verifyInputs": true
  },
  
  "output": {
    "directory": "target/evaluation",
    "resultsFilename": "evaluation_results.json",
    "reportFilename": "evaluation_report.txt",
    "enableTracking": true,
    "experimentId": null
  },
  
  "evaluation": {
    "computePerClassMetrics": true,           // Detailed per-class stats
    "includeConfusionMatrix": true,           // Confusion matrix
    "generateReport": true                    // Human-readable report
  }
}
```

**Output Files**:
1. **evaluation_results.json**: Machine-readable JSON with all metrics
2. **evaluation_report.txt**: Human-readable summary report
3. **Database entries**: Logged to results.csv (if tracking enabled)

**Metrics Computed**:
- Overall: Accuracy, Precision, Recall, F1-Score
- ROC: TPR, FPR, AUC
- Per-Class: Precision, Recall, F1 for each label
- Confusion Matrix: Full classification matrix

### 5. Compare Configuration

**Purpose**: Statistical comparison between datasets

**File**: `config/actions/action_compare.json`

**Schema**:
```json
{
  "action": "compare",
  "description": "Compare benign and attack datasets",
  
  "input": {
    "benignDataPath": "target/benign_data/benign_data.arff",
    "attackDataPath": "target/training/training_dataset.arff"
  },
  
  "output": {
    "directory": "target/comparison",
    "reportFilename": "comparison_report.txt"
  },
  
  "analysis": {
    "computeStatistics": true,
    "identifyDistinguishingFeatures": true,
    "generateVisualizations": false
  }
}
```

## Pipeline Configurations

### Simple Pipeline

**Purpose**: Execute multiple actions sequentially

**File**: `config/pipelines/pipeline_complete.json`

**Schema**:
```json
{
  "action": "pipeline",
  "description": "Complete ML workflow",
  
  "commonConfig": {
    "randomSeed": 42,
    "outputFormat": "arff"
  },
  
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "description": "Generate benign traffic"
    },
    {
      "action": "create_attack_dataset",
      "actionConfigFile": "config/actions/action_create_attack_dataset.json",
      "description": "Create training dataset"
    },
    {
      "action": "train_model",
      "actionConfigFile": "config/actions/action_train_model.json",
      "description": "Train classifiers"
    },
    {
      "action": "evaluate",
      "actionConfigFile": "config/actions/action_evaluate.json",
      "description": "Evaluate models"
    }
  ]
}
```

**Execution**: Steps execute in order, each using its referenced config file.

### Loop Pipeline

**Purpose**: Systematic parameter variation for experiments

**File**: `config/pipelines/pipeline_loop_random_seeds.json`

**Schema**:
```json
{
  "action": "pipeline",
  "description": "Random seed variation experiment",
  
  "commonConfig": {
    "randomSeed": 42,
    "outputFormat": "arff"
  },
  
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "description": "Generate baseline benign data (pre-loop)"
    }
  ],
  
  "loop": {
    "variationType": "randomSeed",             // Type of variation
    "values": [42, 100, 200, 300, 500],       // Values to test
    "baselineDataset": "target/training/training_dataset.arff",
    
    "steps": [
      {
        "action": "create_attack_dataset",
        "actionConfigFile": "config/actions/action_create_attack_dataset.json",
        "description": "Create dataset with varied seed",
        "parameterOverrides": {
          "randomSeed": null,                  // Filled by loop value
          "output": {
            "directory": "target/training_variations",
            "filename": "dataset_seed_{iteration}.arff"
          }
        }
      },
      {
        "action": "train_model",
        "actionConfigFile": "config/actions/action_train_model.json",
        "description": "Train models on varied dataset",
        "parameterOverrides": {
          "input": {
            "trainingDatasetPath": "target/training_variations/dataset_seed_{iteration}.arff"
          },
          "output": {
            "modelDirectory": "target/models_seed_{iteration}"
          }
        }
      },
      {
        "action": "evaluate",
        "actionConfigFile": "config/actions/action_evaluate.json",
        "description": "Evaluate models",
        "parameterOverrides": {
          "input": {
            "modelsDirectory": "target/models_seed_{iteration}",
            "testDatasetPath": "target/training_variations/dataset_seed_{iteration}.arff"
          },
          "output": {
            "directory": "target/evaluation_seed_{iteration}"
          }
        }
      }
    ]
  }
}
```

**Loop Variation Types**:

#### 1. Random Seed Variation
```json
{
  "variationType": "randomSeed",
  "values": [42, 100, 200, 300, 500]
}
```
Tests reproducibility and statistical variance.

#### 2. Attack Segment Variation
```json
{
  "variationType": "attackSegments",
  "values": [
    ["uc01_random_replay"],
    ["uc03_masquerade_fault"],
    ["uc01_random_replay", "uc03_masquerade_fault"],
    ["uc05_injection", "uc07_flooding"]
  ]
}
```
Tests different attack types and combinations.

#### 3. Custom Parameter Variation
```json
{
  "variationType": "parameters",
  "values": [
    {"messagesPerSegment": 500},
    {"messagesPerSegment": 1000},
    {"messagesPerSegment": 2000}
  ]
}
```
Tests structural or algorithmic parameters.

**Template Variables**:
- `{iteration}` - Loop iteration number (1-based)
- `{randomSeed}` - Current random seed value
- `{value}` - Current loop value (generic)

**Parameter Overrides**:
Override specific config fields for each loop iteration:
- `randomSeed` - Override random seed
- `output.directory` - Override output paths
- `output.filename` - Override filenames (supports templates)
- `input.*` - Override input paths
- Custom fields specific to action type

### Pre-Loop Pipeline Steps

Execute setup actions before entering the loop:

```json
{
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "description": "Generate shared benign data (runs once)"
    }
  ],
  "loop": {
    // Loop configuration...
  }
}
```

## Attack Configurations

Attack-specific parameter files define attack behavior.

### Attack Config Schema

**File**: `config/attacks/uc01_random_replay.json`

```json
{
  "attackName": "uc01_random_replay",
  "description": "Random replay attack parameters",
  "attackParameters": {
    "replayProbability": 0.3,                 // Probability of replaying (0.0-1.0)
    "maxReplayWindow": 100,                   // Max messages to look back
    "randomizeTimestamp": true                // Randomize replayed timestamps
  }
}
```

Each attack type has specific parameters. See `config/attacks/` for examples.

## Common Configuration Patterns

### Experiment Linking

Link multiple actions to a shared experiment:

**Step 1**: Create experiment config with tracking
```json
{
  "output": {
    "enableTracking": true,
    "experimentId": null  // Will be auto-generated
  }
}
```

**Step 2**: Retrieve experiment ID from logs or database

**Step 3**: Use in subsequent actions
```json
{
  "output": {
    "enableTracking": true,
    "experimentId": "EXP_1234567890_1234"
  }
}
```

### Dynamic Path Generation

Use templates for loop-generated paths:

```json
{
  "parameterOverrides": {
    "output": {
      "directory": "target/results_{iteration}",
      "filename": "dataset_seed{randomSeed}_iter{iteration}.arff"
    }
  }
}
```

### Baseline Comparison

Specify baseline dataset for statistical comparison:

```json
{
  "loop": {
    "baselineDataset": "target/training/baseline_dataset.arff",
    "steps": [
      {
        "action": "evaluate",
        "parameterOverrides": {
          "baselineDatasetPath": "target/training/baseline_dataset.arff"
        }
      }
    ]
  }
}
```

## Configuration Validation

### Required Fields

**All Actions**:
- `action` (string)
- `description` (string)

**Action-Specific**:
- Each action has required `input` and `output` sections
- See individual action schemas above

### Path Resolution

**Relative Paths**: Resolved from ERENO root directory
```json
"benignDataPath": "target/benign_data/data.arff"
```

**Absolute Paths**: Used as-is
```json
"benignDataPath": "/home/user/data/benign_data.arff"
```

### Format Specifications

**Dates**: ISO 8601 or auto-generated timestamps
**MACs**: Colon-separated hex (01:0c:cd:01:2f:80)
**Hex Values**: 0x prefix (0x88B8, 0x8100)
**Arrays**: JSON arrays ["item1", "item2"]
**Objects**: JSON objects {"key": "value"}

## Best Practices

### 1. Configuration Organization
- Keep action configs in `config/actions/`
- Keep pipeline configs in `config/pipelines/`
- Keep attack configs in `config/attacks/`
- Use descriptive filenames

### 2. Naming Conventions
- Actions: `action_<verb>_<noun>.json`
- Pipelines: `pipeline_<purpose>.json`
- Attacks: `uc<number>_<attack_type>.json`

### 3. Documentation
- Always include `description` fields
- Add comments for non-obvious parameters (JSON5 if supported)
- Document expected ranges for numeric parameters

### 4. Path Management
- Use relative paths for portability
- Create output directories in `target/` (gitignored)
- Use template variables for loop-generated paths

### 5. Experiment Tracking
- Enable tracking for reproducibility
- Link related actions via experimentId
- Add meaningful notes in configs

### 6. Version Control
- Commit working configurations
- Tag configs used for published results
- Don't commit large output files

## Troubleshooting

### Configuration Not Found
**Error**: `Configuration error: file not found`
**Solution**: Verify path is correct and relative to ERENO root

### Invalid JSON
**Error**: `Configuration error: malformed JSON`
**Solution**: Validate JSON syntax (use online validator or IDE)

### Missing Required Field
**Error**: `Missing required field: <fieldname>`
**Solution**: Check action schema, add missing field

### Path Template Not Resolved
**Error**: Path contains `{iteration}` literally
**Solution**: Ensure using loop pipeline, not simple pipeline

### Attack Config Not Found
**Error**: Attack config file not found
**Solution**: Verify `attackConfig` path in attack segment

## Advanced Topics

### Custom Action Configs

To create a new action configuration:
1. Define config class in action handler
2. Implement JSON parsing (Gson)
3. Create example config file
4. Document in this guide

### Configuration Preprocessing

For complex experiments, generate configs programmatically:

```python
import json

base_config = {
    "action": "create_attack_dataset",
    # ... base fields
}

for seed in [42, 100, 200]:
    config = base_config.copy()
    config["output"]["filename"] = f"dataset_seed{seed}.arff"
    
    with open(f"config/generated/dataset_seed{seed}.json", "w") as f:
        json.dump(config, f, indent=2)
```

### Schema Evolution

When adding new config fields:
1. Make new fields optional with sensible defaults
2. Document default behavior
3. Update this guide
4. Consider migration script for old configs

## See Also

- [ACTION_RUNNER_GUIDE.md](ACTION_RUNNER_GUIDE.md) - How configurations are executed
- [DATABASE_GUIDE.md](DATABASE_GUIDE.md) - Tracking database integration
- [README.md](README.md) - Project overview
