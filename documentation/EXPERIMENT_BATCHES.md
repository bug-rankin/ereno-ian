# ERENO Experiment Batches (Loop Pipelines)

## Overview

**Experiment Batches** in ERENO are implemented through **Loop Pipelines** - a powerful system for systematically varying parameters across multiple iterations. Instead of manually creating and running dozens of configuration files, loop pipelines automate parameter sweeps, enabling reproducible, tracked experiments.

## Why Use Loop Pipelines?

**Manual Approach** (tedious):
```bash
# Edit config, change seed to 42
java -jar ERENO.jar config1.json
# Edit config, change seed to 100
java -jar ERENO.jar config2.json
# Edit config, change seed to 200
java -jar ERENO.jar config3.json
# ... repeat 100 times ...
```

**Loop Pipeline** (automated):
```bash
# One command, 100 iterations
java -jar ERENO.jar config/pipelines/pipeline_loop_random_seeds.json
```

## Core Architecture

### Loop Pipeline Structure

```
┌─────────────────────────────────────────┐
│      Loop Pipeline Configuration        │
├─────────────────────────────────────────┤
│  Pre-Loop Steps (Optional)              │
│  ├─ Run once before loop starts         │
│  └─ E.g., create shared benign data     │
├─────────────────────────────────────────┤
│  Loop Configuration                     │
│  ├─ Variation Type (seed/attack/param)  │
│  ├─ Values Array [v1, v2, v3, ...]     │
│  └─ Loop Steps (repeated actions)       │
│      ├─ Step 1: Action + Overrides      │
│      ├─ Step 2: Action + Overrides      │
│      └─ Step N: Action + Overrides      │
└─────────────────────────────────────────┘

Execution Flow:
  Pre-Loop → Iter 1 (all steps) → Iter 2 (all steps) → ... → Iter N (all steps)
```

## Three Variation Types

### 1. Random Seed Variation

**Purpose**: Test reproducibility, measure statistical variance, ensure consistent results

**Use Case**: "Do my models perform similarly with different random seeds?"

**Configuration**:
```json
{
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 100, 200, 300, 500],
    "steps": [ /* actions to repeat */ ]
  }
}
```

**What Gets Varied**: The random seed injected into all randomized operations:
- Dataset shuffling
- Attack parameter randomization
- Train/test splits
- Classifier random initialization

**Example Results**:
```
Seed 42  → Accuracy: 95.2%
Seed 100 → Accuracy: 94.8%
Seed 200 → Accuracy: 95.5%
Mean: 95.17% ± 0.29%
```

**File**: `config/pipelines/pipeline_loop_random_seeds.json`

### 2. Attack Segments Variation

**Purpose**: Test different attack types, find hardest-to-detect attacks, test combinations

**Use Case**: "Which attack is hardest to detect?" or "Do attack combinations confuse the classifier?"

**Configuration**:
```json
{
  "loop": {
    "variationType": "attackSegments",
    "values": [
      ["uc01_random_replay"],                          // Single attack
      ["uc03_masquerade_fault"],                       // Different attack
      ["uc01_random_replay", "uc03_masquerade_fault"], // Combination
      ["uc05_injection", "uc07_flooding"],             // Multi-attack
      ["uc01_random_replay", "uc03_masquerade_fault", 
       "uc05_injection", "uc07_flooding"]              // All attacks
    ],
    "steps": [ /* actions to repeat */ ]
  }
}
```

**What Gets Varied**: The `attackSegments` array in dataset creation configs

**Example Results**:
```
uc01 only        → Accuracy: 98.5% (easy to detect)
uc03 only        → Accuracy: 92.1% (harder)
uc01 + uc03      → Accuracy: 95.3% (combined)
uc01+uc03+uc05   → Accuracy: 91.7% (most challenging)
```

**File**: `config/pipelines/pipeline_loop_attack_segments.json`

### 3. Custom Parameters Variation

**Purpose**: Test structural/algorithmic parameters, hyperparameter tuning, ablation studies

**Use Case**: "How does dataset size affect accuracy?" or "What's the optimal tree depth?"

**Configuration**:
```json
{
  "loop": {
    "variationType": "parameters",
    "values": [
      {"messagesPerSegment": 500, "faultProbability": 3},
      {"messagesPerSegment": 1000, "faultProbability": 5},
      {"messagesPerSegment": 2000, "faultProbability": 10},
      {"messagesPerSegment": 5000, "faultProbability": 15}
    ],
    "steps": [ /* actions to repeat */ ]
  }
}
```

**What Gets Varied**: Any custom parameters merged into configs

**Example Results**:
```
500 msgs/seg   → Accuracy: 88.3% (insufficient data)
1000 msgs/seg  → Accuracy: 94.1% (good)
2000 msgs/seg  → Accuracy: 95.8% (better)
5000 msgs/seg  → Accuracy: 96.1% (diminishing returns)
```

**File**: `config/pipelines/pipeline_loop_parameters.json`

## Parameter Override Mechanism

The key to loop pipelines is the **override system**, which modifies base configurations for each iteration.

### Override Process

```
1. Load Base Config
   ↓
   config/actions/action_create_attack_dataset.json
   
2. Apply Loop-Specific Override
   ↓
   variationType = "randomSeed", value = 42
   → config.randomSeed = 42
   
3. Apply Step-Specific Overrides
   ↓
   parameterOverrides: {
     output: { filename: "dataset_seed_{iteration}.arff" }
   }
   → config.output.filename = "dataset_seed_1.arff"
   
4. Resolve Template Variables
   ↓
   {iteration} → 1
   {randomSeed} → 42
   {value} → "42"
   
5. Write Temporary Config
   ↓
   temp_config_12345.json
   
6. Execute Action
   ↓
   CreateAttackDatasetAction.execute(temp_config_12345.json)
   
7. Clean Up
   ↓
   Delete temp_config_12345.json
```

### Template Variables

Loop pipelines support dynamic path generation via template variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `{iteration}` | Current iteration number (1-based) | `1`, `2`, `3` |
| `{randomSeed}` | Current random seed (randomSeed variation only) | `42`, `100` |
| `{value}` | String representation of current loop value | `"42"`, `"[uc01, uc03]"` |

**Usage Examples**:
```json
{
  "parameterOverrides": {
    "output": {
      "directory": "target/results_iteration_{iteration}",
      "filename": "dataset_seed_{randomSeed}_iter_{iteration}.arff"
    }
  }
}
```

**Resolves to** (iteration 1, seed 42):
```
directory: "target/results_iteration_1"
filename: "dataset_seed_42_iter_1.arff"
```

## Complete Loop Pipeline Example

### Random Seed Experiment

**Goal**: Test if model performance is consistent across 5 different random seeds

**Config**: `config/pipelines/pipeline_loop_random_seeds.json`

```json
{
  "action": "pipeline",
  "description": "Random seed variation experiment - test reproducibility",
  
  "commonConfig": {
    "randomSeed": 42,
    "outputFormat": "arff"
  },
  
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "description": "Generate shared benign data (runs once, pre-loop)"
    }
  ],
  
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 100, 200, 300, 500],
    
    "steps": [
      {
        "action": "create_attack_dataset",
        "actionConfigFile": "config/actions/action_create_attack_dataset.json",
        "description": "Create training dataset with seed variation",
        "parameterOverrides": {
          "output": {
            "directory": "target/datasets_seeds",
            "filename": "training_seed_{iteration}.arff"
          }
        }
      },
      {
        "action": "train_model",
        "actionConfigFile": "config/actions/action_train_model.json",
        "description": "Train models on varied dataset",
        "parameterOverrides": {
          "input": {
            "trainingDatasetPath": "target/datasets_seeds/training_seed_{iteration}.arff"
          },
          "output": {
            "modelDirectory": "target/models_seed_{iteration}"
          }
        }
      },
      {
        "action": "evaluate",
        "actionConfigFile": "config/actions/action_evaluate.json",
        "description": "Evaluate models on test set",
        "parameterOverrides": {
          "input": {
            "modelsDirectory": "target/models_seed_{iteration}",
            "testDatasetPath": "target/datasets_seeds/training_seed_{iteration}.arff"
          },
          "output": {
            "directory": "target/evaluation_seed_{iteration}",
            "resultsFilename": "results_seed_{iteration}.json"
          }
        }
      }
    ]
  }
}
```

### Execution Timeline

```
┌─────────────────────────────────────────────────────────────┐
│ Pre-Loop Phase                                              │
└─────────────────────────────────────────────────────────────┘
  [1] Create Benign Data
      → target/benign_data/benign_data.arff
      ✓ Completed in 2.3s

┌─────────────────────────────────────────────────────────────┐
│ Iteration 1/5 (Random Seed: 42)                            │
└─────────────────────────────────────────────────────────────┘
  [1] Create Attack Dataset (seed=42)
      → target/datasets_seeds/training_seed_1.arff
      ✓ Completed in 8.7s
  
  [2] Train Models (seed=42)
      → target/models_seed_1/J48_model.model
      → target/models_seed_1/RandomForest_model.model
      ✓ Completed in 45.2s
  
  [3] Evaluate Models (seed=42)
      → target/evaluation_seed_1/results_seed_1.json
      ✓ Completed in 5.1s

┌─────────────────────────────────────────────────────────────┐
│ Iteration 2/5 (Random Seed: 100)                           │
└─────────────────────────────────────────────────────────────┘
  [1] Create Attack Dataset (seed=100)
      → target/datasets_seeds/training_seed_2.arff
      ✓ Completed in 8.9s
  
  [2] Train Models (seed=100)
      → target/models_seed_2/J48_model.model
      → target/models_seed_2/RandomForest_model.model
      ✓ Completed in 44.8s
  
  [3] Evaluate Models (seed=100)
      → target/evaluation_seed_2/results_seed_2.json
      ✓ Completed in 5.0s

... (iterations 3, 4, 5) ...

┌─────────────────────────────────────────────────────────────┐
│ Pipeline Completed Successfully                             │
└─────────────────────────────────────────────────────────────┘
  Total Time: 328.5s
  Iterations: 5
  Total Datasets: 5
  Total Models: 10 (2 classifiers × 5 seeds)
  Total Evaluations: 10
```

## Experiment Tracking

All loop iterations are automatically tracked in the database, linked by a shared experiment ID.

### Database Entries

**experiments.csv**:
```csv
experiment_id,timestamp,experiment_type,description,status
EXP_1234567890_1234,2026-01-02 10:30:00,random_seed_analysis,Random seed variation experiment,completed
```

**datasets.csv**:
```csv
dataset_id,experiment_id,dataset_type,random_seed,file_path
DS_1001,EXP_1234567890_1234,training,42,target/datasets_seeds/training_seed_1.arff
DS_1002,EXP_1234567890_1234,training,100,target/datasets_seeds/training_seed_2.arff
DS_1003,EXP_1234567890_1234,training,200,target/datasets_seeds/training_seed_3.arff
...
```

**models.csv**:
```csv
model_id,experiment_id,training_dataset_id,classifier_name,model_path
MDL_2001,EXP_1234567890_1234,DS_1001,J48,target/models_seed_1/J48_model.model
MDL_2002,EXP_1234567890_1234,DS_1001,RandomForest,target/models_seed_1/RandomForest_model.model
MDL_2003,EXP_1234567890_1234,DS_1002,J48,target/models_seed_2/J48_model.model
...
```

**results.csv**:
```csv
result_id,experiment_id,model_id,classifier_name,accuracy,precision,recall,f_measure
RES_3001,EXP_1234567890_1234,MDL_2001,J48,0.9523,0.9531,0.9523,0.9522
RES_3002,EXP_1234567890_1234,MDL_2002,RandomForest,0.9678,0.9682,0.9678,0.9677
...
```

### Querying Results

**Python Analysis**:
```python
import pandas as pd

# Load results for experiment
results = pd.read_csv('target/tracking/results.csv')
exp_results = results[results['experiment_id'] == 'EXP_1234567890_1234']

# Group by classifier
grouped = exp_results.groupby('classifier_name')['accuracy'].agg(['mean', 'std', 'min', 'max'])

print(grouped)
# Output:
#                   mean       std       min       max
# classifier_name                                      
# J48            0.9523  0.0031  0.9489  0.9567
# RandomForest   0.9678  0.0021  0.9652  0.9701
```

## Advanced Loop Patterns

### Multi-Stage Loops

Create baseline, then loop over variations:

```json
{
  "pipeline": [
    {
      "action": "create_benign",
      "description": "Shared benign data"
    },
    {
      "action": "create_attack_dataset",
      "description": "Baseline test dataset"
    }
  ],
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 100, 200],
    "baselineDataset": "target/training/baseline_dataset.arff",
    "steps": [
      {
        "action": "create_attack_dataset",
        "description": "Training with seed variation",
        "parameterOverrides": {
          "output": {
            "filename": "training_seed_{iteration}.arff"
          }
        }
      },
      {
        "action": "train_model",
        "description": "Train on varied data"
      },
      {
        "action": "evaluate",
        "description": "Evaluate on baseline test set",
        "parameterOverrides": {
          "input": {
            "testDatasetPath": "target/training/baseline_dataset.arff"
          }
        }
      }
    ]
  }
}
```

**Key Insight**: Pre-loop creates a fixed test set, loop creates varied training sets, all evaluated on same test set for fair comparison.

### Nested Experiments (Manual)

While ERENO doesn't support nested loops in a single config, you can manually orchestrate:

```bash
# Outer loop: Attack types
for attack in uc01 uc03 uc05; do
  # Edit config to use specific attack
  sed -i "s/ATTACK_PLACEHOLDER/$attack/" config_template.json
  
  # Inner loop: Random seeds (via loop pipeline)
  java -jar ERENO.jar config/pipelines/pipeline_loop_seeds_$attack.json
done
```

Or use Python:
```python
import subprocess
import json

for attack in ['uc01', 'uc03', 'uc05']:
    for seed in [42, 100, 200]:
        config = generate_config(attack, seed)
        with open(f'config_temp_{attack}_{seed}.json', 'w') as f:
            json.dump(config, f)
        
        subprocess.run(['java', '-jar', 'ERENO.jar', 
                       f'config_temp_{attack}_{seed}.json'])
```

### Conditional Steps

Enable/disable steps based on iteration:

```json
{
  "loop": {
    "steps": [
      {
        "action": "create_attack_dataset",
        "enabled": true
      },
      {
        "action": "train_model",
        "enabled": true
      },
      {
        "action": "evaluate",
        "enabled": "${iteration} > 1"  // Only after first iteration
      }
    ]
  }
}
```

*Note: Conditional execution currently requires manual config editing between iterations or custom scripting.*

## Common Experiment Designs

### 1. Reproducibility Study

**Question**: "Are my results reproducible?"

**Design**: 10 random seeds, full pipeline

```json
{
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 24, 23, 25, 26, 27, 28, 29, 31, 32],
    "steps": [
      "create_attack_dataset",
      "train_model", 
      "evaluate"
    ]
  }
}
```

**Analysis**: Mean ± std of accuracy, confidence intervals

### 2. Attack Difficulty Analysis

**Question**: "Which attacks are hardest to detect?"

**Design**: Each attack individually, plus combinations

```json
{
  "loop": {
    "variationType": "attackSegments",
    "values": [
      ["uc01"], ["uc02"], ["uc03"], ["uc04"],
      ["uc05"], ["uc06"], ["uc07"], ["uc08"],
      ["uc01", "uc03"], ["uc05", "uc07"]
    ],
    "steps": [
      "create_attack_dataset",
      "train_model",
      "evaluate"
    ]
  }
}
```

**Analysis**: Rank attacks by detection accuracy

### 3. Dataset Size Study

**Question**: "How much data do I need?"

**Design**: Vary messages per segment

```json
{
  "loop": {
    "variationType": "parameters",
    "values": [
      {"messagesPerSegment": 100},
      {"messagesPerSegment": 500},
      {"messagesPerSegment": 1000},
      {"messagesPerSegment": 2000},
      {"messagesPerSegment": 5000}
    ],
    "steps": [
      "create_attack_dataset",
      "train_model",
      "evaluate"
    ]
  }
}
```

**Analysis**: Plot accuracy vs dataset size, find knee point

### 4. Hyperparameter Tuning

**Question**: "What are optimal classifier parameters?"

**Design**: Grid search over hyperparameters

```json
{
  "loop": {
    "variationType": "parameters",
    "values": [
      {"j48": {"confidenceFactor": 0.1}},
      {"j48": {"confidenceFactor": 0.25}},
      {"j48": {"confidenceFactor": 0.5}},
      {"randomForest": {"numIterations": 50}},
      {"randomForest": {"numIterations": 100}},
      {"randomForest": {"numIterations": 200}}
    ],
    "steps": [
      "train_model",
      "evaluate"
    ]
  }
}
```

**Analysis**: Find parameter combination with highest accuracy

### 5. Cross-Validation

**Question**: "How does model generalize?"

**Design**: Multiple train/test splits

```json
{
  "loop": {
    "variationType": "parameters",
    "values": [
      {"trainTestSplit": {"fold": 1}},
      {"trainTestSplit": {"fold": 2}},
      {"trainTestSplit": {"fold": 3}},
      {"trainTestSplit": {"fold": 4}},
      {"trainTestSplit": {"fold": 5}}
    ],
    "steps": [
      "create_attack_dataset",
      "train_model",
      "evaluate"
    ]
  }
}
```

**Analysis**: K-fold cross-validation statistics

## Performance Considerations

### Execution Time

Loop pipelines can be time-intensive:

**Example Timing** (5 iterations):
- Create benign: 2s (once)
- Create attack dataset: 10s × 5 = 50s
- Train models (2 classifiers): 45s × 5 = 225s
- Evaluate: 5s × 5 = 25s
- **Total**: ~5 minutes

**Scaling**: 100 iterations = ~1.5 hours

### Parallelization

Currently, loop iterations execute sequentially. For faster execution:

**Option 1: Manual Parallelization**
```bash
# Split values into chunks, run in parallel
java -jar ERENO.jar config_chunk1.json &
java -jar ERENO.jar config_chunk2.json &
java -jar ERENO.jar config_chunk3.json &
wait
```

**Option 2: Use HPC/Grid**
```bash
# Submit as array job
sbatch --array=1-100 run_experiment.sh
```

### Disk Space

Monitor output directories:
- Each dataset: ~10-50 MB
- Each model: ~1-10 MB
- 100 iterations × 8 attacks × 2 files = 1.6K files

**Best Practice**: Clean up intermediate files after experiments

## Troubleshooting

### Loop Not Executing

**Symptom**: Only pre-loop steps run

**Cause**: Missing `loop` key or empty `loop.steps`

**Fix**: Verify loop structure:
```json
{
  "action": "pipeline",
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 100],
    "steps": [ /* must not be empty */ ]
  }
}
```

### Template Variables Not Resolved

**Symptom**: Filename contains `{iteration}` literally

**Cause**: Using simple pipeline instead of loop pipeline

**Fix**: Ensure config has `loop` section with `variationType` and `values`

### Inconsistent Results Across Iterations

**Symptom**: Same seed produces different results

**Cause**: External randomness not controlled by seed

**Fix**: 
- Ensure all randomness uses `ConfigLoader.RNG`
- Check for time-based randomness
- Verify seed is actually being applied

### Out of Memory

**Symptom**: JVM crashes during loop

**Cause**: Models/datasets accumulating in memory

**Fix**: Increase heap size:
```bash
java -Xmx8g -jar ERENO.jar config.json
```

### Path Template Errors

**Symptom**: File not found errors in later steps

**Cause**: Template didn't resolve correctly

**Fix**: Test path generation:
```json
"filename": "test_iter_{iteration}.arff"  // ✓ Good
"filename": "test_iter_${iteration}.arff" // ✗ Wrong syntax
```

## Best Practices

### 1. Start Small
Test loop with 2-3 iterations before running 100

### 2. Use Descriptive Names
```json
"description": "Random seed analysis (seeds 42-500) for reproducibility study"
```

### 3. Enable Tracking
```json
"output": {
  "enableTracking": true,
  "experimentId": "EXP_reproducibility_2026"
}
```

### 4. Organize Output
```
target/
  experiments/
    exp_seeds_2026/
      datasets/
      models/
      evaluation/
```

### 5. Document Parameters
Keep a lab notebook or comments file:
```json
// experiment_notes.json
{
  "experiment_id": "EXP_2026_seeds",
  "date": "2026-01-02",
  "goal": "Test reproducibility with 10 seeds",
  "hypothesis": "Variance should be < 1%",
  "results": "Mean 95.2% ± 0.3%, hypothesis confirmed"
}
```

### 6. Version Control Configs
```bash
git add config/pipelines/exp_reproducibility.json
git commit -m "Add reproducibility experiment config"
git tag exp-reproducibility-v1
```

## See Also

- [ACTION_RUNNER_GUIDE.md](ACTION_RUNNER_GUIDE.md) - How loops are executed
- [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md) - Full config reference
- [DATABASE_GUIDE.md](DATABASE_GUIDE.md) - Tracking and querying results
- [README.md](README.md) - Project overview
