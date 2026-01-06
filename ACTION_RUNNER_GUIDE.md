# ERENO Action Runner System Guide

## Overview

The **ActionRunner** is the main entry point for ERENO's modular execution system. It provides a flexible, action-based architecture that supports individual tasks, sequential pipelines, and parameterized loops for systematic experimentation.

## System Architecture

### Entry Point
```
java -jar ERENO.jar <config-file.json>
```

The ActionRunner loads a configuration file that specifies what to execute and orchestrates the entire workflow.

### Design Philosophy

**Action-Based**: Each major task is an independent "action" with its own configuration
**Composable**: Actions can be chained into pipelines
**Parameterized**: Loop constructs enable systematic parameter variation
**Trackable**: All actions integrate with the tracking database

## Core Components

### 1. ActionRunner (Main Orchestrator)

**Location**: `src/main/java/br/ufu/facom/ereno/ActionRunner.java`

**Responsibilities:**
- Parse and validate configuration files
- Dispatch execution to appropriate action handlers
- Manage pipeline and loop execution
- Handle errors and logging

**Entry Point:**
```java
public static void main(String[] args) {
    // 1. Load configuration
    ActionConfigLoader actionLoader = new ActionConfigLoader();
    actionLoader.load(mainConfigPath);
    
    // 2. Dispatch to action
    switch (actionLoader.getCurrentAction()) {
        case CREATE_BENIGN:
            CreateBenignAction.execute(config);
            break;
        case PIPELINE:
            executePipeline(actionLoader);
            break;
        // ... other actions
    }
}
```

### 2. Action Types

ERENO supports six core action types:

#### CREATE_BENIGN
Generate legitimate (benign) GOOSE protocol traffic

**Handler**: `br.ufu.facom.ereno.actions.CreateBenignAction`

**Purpose**: 
- Simulate normal IEC 61850 substation communication
- Generate baseline "normal" traffic for training data
- Configurable fault probability and message count

**Key Features**:
- Generates GOOSE messages from legitimate protection IED
- Supports ARFF and CSV output formats
- Configurable output directory and filename prefix
- Integrated tracking (logs to datasets.csv)

**Config Example**: `config/actions/action_create_benign.json`

#### CREATE_ATTACK_DATASET
Generate labeled attack datasets from benign data

**Handler**: `br.ufu.facom.ereno.actions.CreateAttackDatasetAction`

**Purpose**:
- Load benign traffic as baseline
- Simulate various attack scenarios
- Combine benign and attack segments into labeled datasets
- Support attack combinations and custom segment structures

**Attack Types Supported**:
- UC01: Random Replay
- UC02: Inverse Replay
- UC03: Masquerade Fault
- UC04: Masquerade Normal
- UC05: Message Injection
- UC06: High Sequence Number Injection
- UC07: Message Flooding
- UC08: Grayhole

**Key Features**:
- Attack segment composition (benign + multiple attacks)
- Configurable messages per segment
- Optional segment shuffling
- Attack combination support (multiple simultaneous attacks)
- Automatic labeling

**Config Example**: `config/actions/action_create_attack_dataset.json`

#### TRAIN_MODEL
Train machine learning classifiers on attack datasets

**Handler**: `br.ufu.facom.ereno.actions.TrainModelAction`

**Purpose**:
- Load training dataset (ARFF/CSV)
- Train multiple classifiers in parallel
- Serialize models to disk
- Generate training metadata

**Supported Classifiers**:
- **J48** (C4.5 decision tree)
- **RandomForest**
- **NaiveBayes**
- **REPTree** (Reduced Error Pruning Tree)
- **IBk** (k-Nearest Neighbors)

**Key Features**:
- Multiple models trained simultaneously
- Configurable hyperparameters per classifier
- Model serialization for reuse
- Training time tracking
- Metadata JSON output (training_metadata.json)
- Database integration (logs to models.csv)

**Config Example**: `config/actions/action_train_model.json`

#### EVALUATE
Evaluate trained models against test datasets

**Handler**: `br.ufu.facom.ereno.actions.EvaluateAction`

**Purpose**:
- Load trained models from disk
- Evaluate on test dataset
- Compute comprehensive metrics
- Generate evaluation reports

**Metrics Computed**:
- Overall: Accuracy, Precision, Recall, F1-Score
- ROC: True Positive Rate, False Positive Rate, AUC
- Confusion Matrix
- Per-Class: Precision, Recall, F1 for each attack type
- Misclassification analysis

**Output Formats**:
- JSON results file
- Human-readable text report
- Database entries (results.csv)

**Key Features**:
- Multi-model evaluation
- Detailed per-class metrics
- Confusion matrix generation
- Baseline comparison support
- Statistical analysis

**Config Example**: `config/actions/action_evaluate.json`

#### COMPARE
Compare benign data with attack datasets (analysis tool)

**Handler**: `br.ufu.facom.ereno.actions.CompareAction`

**Purpose**:
- Statistical comparison between datasets
- Identify distinguishing features
- Validate attack injection effectiveness

**Config Example**: `config/actions/action_compare.json`

#### PIPELINE
Execute multiple actions sequentially (orchestration)

**Handler**: Built into ActionRunner (no separate action class)

**Purpose**:
- Chain multiple actions into workflows
- Support parameter variation loops
- Enable systematic experimentation
- Maintain context across actions

**Two Modes**:
1. **Simple Pipeline**: Sequential action execution
2. **Loop Pipeline**: Parameter variation across iterations

### 3. Configuration System

**ActionConfigLoader**: Parses JSON configurations

**Configuration Hierarchy**:
```
Main Config
├── action: "pipeline" | "create_benign" | ...
├── description: Human-readable description
├── actionConfigFile: Path to action-specific config
├── commonConfig: Shared parameters (randomSeed, etc.)
├── pipeline: [PipelineStep, PipelineStep, ...]  // For pipelines
└── loop: LoopConfig  // For parameterized loops
```

## Execution Flows

### Single Action Execution

```
User → ActionRunner → ActionConfigLoader → Action Handler → Output
                                              ↓
                                        Database Tracker
```

**Steps**:
1. User provides config file path
2. ActionRunner loads and parses config
3. Dispatches to appropriate action handler
4. Action executes and produces output
5. Tracking database updated (if enabled)

**Example**:
```bash
java -jar ERENO.jar config/actions/action_create_benign.json
```

### Pipeline Execution

```
User → ActionRunner → executePipeline()
                         ├── Step 1: Create Benign
                         ├── Step 2: Create Attack Dataset
                         ├── Step 3: Train Models
                         └── Step 4: Evaluate
```

**Steps**:
1. Load pipeline configuration
2. Iterate through pipeline steps
3. For each step:
   - Load step's action config
   - Execute action
   - Log results
   - Continue to next step
4. Complete pipeline with summary

**Example**:
```bash
java -jar ERENO.jar config/pipelines/pipeline_complete.json
```

**Pipeline Config Structure**:
```json
{
  "action": "pipeline",
  "description": "Complete ML workflow",
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "description": "Generate benign data"
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

### Loop Pipeline Execution (Advanced)

Enables systematic parameter variation for experiments.

```
User → ActionRunner → executePipelineWithLoop()
                         ├── [Pre-loop steps if any]
                         ├── Loop Iteration 1 (value[0])
                         │     ├── Step 1 (with overrides)
                         │     ├── Step 2 (with overrides)
                         │     └── Step 3 (with overrides)
                         ├── Loop Iteration 2 (value[1])
                         │     ├── Step 1 (with overrides)
                         │     └── ...
                         └── Loop Iteration N (value[N-1])
                               └── ...
```

**Loop Variation Types**:

1. **randomSeed**: Test reproducibility and variance
   ```json
   {
     "variationType": "randomSeed",
     "values": [42, 100, 200, 300, 500]
   }
   ```

2. **attackSegments**: Test different attack combinations
   ```json
   {
     "variationType": "attackSegments",
     "values": [
       ["uc01_random_replay"],
       ["uc03_masquerade_fault"],
       ["uc01_random_replay", "uc03_masquerade_fault"]
     ]
   }
   ```

3. **parameters**: Test structural variations
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

**Parameter Override Mechanism**:

For each loop iteration, ActionRunner:
1. Loads base action config
2. Applies loop-specific overrides (seed, segments, parameters)
3. Applies step-specific overrides (output paths, experiment IDs)
4. Writes temporary config file
5. Executes action with modified config
6. Cleans up temporary config

**Example Loop Config**:
```json
{
  "action": "pipeline",
  "description": "Test random seed variations",
  "loop": {
    "variationType": "randomSeed",
    "values": [42, 100, 200],
    "steps": [
      {
        "action": "create_attack_dataset",
        "actionConfigFile": "config/actions/action_create_attack_dataset.json",
        "parameterOverrides": {
          "randomSeed": null,  // Will be filled by loop value
          "output": {
            "directory": "target/datasets_loop",
            "filenameTemplate": "attack_dataset_seed_{iteration}.arff"
          }
        }
      },
      {
        "action": "train_model",
        "actionConfigFile": "config/actions/action_train_model.json",
        "parameterOverrides": {
          "input": {
            "trainingDatasetPath": "target/datasets_loop/attack_dataset_seed_{iteration}.arff"
          },
          "output": {
            "modelDirectory": "target/models_seed_{iteration}"
          }
        }
      },
      {
        "action": "evaluate",
        "actionConfigFile": "config/actions/action_evaluate.json",
        "parameterOverrides": {
          "input": {
            "modelsDirectory": "target/models_seed_{iteration}"
          }
        }
      }
    ]
  }
}
```

## Advanced Features

### Experiment Linking

All actions support linking to a shared experiment for coherent tracking:

```json
{
  "output": {
    "experimentId": "EXP_1234567890_1234"
  }
}
```

When omitted, each action creates its own standalone experiment.

### Output Path Templates

Loop pipelines support dynamic path generation:

```json
{
  "parameterOverrides": {
    "output": {
      "filenameTemplate": "dataset_iter_{iteration}_seed_{randomSeed}.arff"
    }
  }
}
```

**Template Variables**:
- `{iteration}` - Loop iteration number (1-based)
- `{randomSeed}` - Current random seed value
- `{value}` - Generic current loop value

### Baseline Datasets

Evaluation actions support baseline comparison:

```json
{
  "parameterOverrides": {
    "baselineDatasetPath": "target/datasets/baseline_dataset.arff"
  }
}
```

Enables statistical comparison between test and baseline distributions.

### Conditional Execution

Pipeline steps can be conditionally enabled/disabled:

```json
{
  "pipeline": [
    {
      "action": "create_benign",
      "actionConfigFile": "config/actions/action_create_benign.json",
      "enabled": false  // Skip this step
    }
  ]
}
```

## Error Handling

### Exit Codes
- **0**: Success
- **1**: Invalid arguments or unknown action
- **2**: Configuration error (file not found, malformed JSON)
- **3**: Execution error (runtime exception)

### Logging

All actions use Java logging framework:

```java
Logger LOGGER = Logger.getLogger(ActionRunner.class.getName());
LOGGER.info("Action started");
LOGGER.severe("Error occurred: " + e.getMessage());
```

**Log Levels**:
- `INFO`: Normal execution flow
- `WARNING`: Non-critical issues
- `SEVERE`: Errors and failures

### Failure Recovery

**Pipeline Behavior**:
- By default, pipeline stops on first error
- Experiment status set to "failed" in database
- Partial results preserved in database

**Best Practices**:
- Test individual actions before building pipelines
- Use small datasets for testing
- Verify output paths exist and are writable
- Check database tracking is enabled

## Performance Considerations

### Memory Management

**Weka Classifiers**: May require significant heap space for large datasets

```bash
java -Xmx8g -jar ERENO.jar config.json  # 8GB heap
```

### Parallelization

**Current State**: Actions execute sequentially
**Future**: Could parallelize independent loop iterations

### File I/O

- ARFF writing is streaming (low memory footprint)
- Model serialization uses Weka's native format
- Database writes are append-only (thread-safe)

## Development Guide

### Adding a New Action

1. **Create Action Handler Class**:
   ```java
   package br.ufu.facom.ereno.actions;
   
   public class MyNewAction {
       public static class Config {
           // Define configuration structure
       }
       
       public static void execute(String configPath) throws Exception {
           // Implementation
       }
   }
   ```

2. **Add to ActionConfigLoader**:
   ```java
   public enum Action {
       // ...existing actions...
       MY_NEW_ACTION,
   }
   ```

3. **Add to ActionRunner Dispatch**:
   ```java
   case MY_NEW_ACTION:
       MyNewAction.execute(config.actionConfigFile);
       break;
   ```

4. **Create Configuration Template**:
   ```json
   {
     "action": "my_new_action",
     "description": "Does something new",
     "input": { /* ... */ },
     "output": { /* ... */ }
   }
   ```

### Debugging Tips

**Enable Verbose Logging**:
```bash
java -Djava.util.logging.config.file=logging.properties -jar ERENO.jar config.json
```

**Inspect Temporary Configs**:
Loop executions create temporary config files - comment out cleanup to inspect:
```java
// new File(tempConfigPath).delete();  // Comment this line
```

**Database Inspection**:
```bash
# View recent experiments
tail -n 10 target/tracking/experiments.csv

# Check dataset creation
grep "attack" target/tracking/datasets.csv
```

## Common Workflows

### Complete ML Workflow
```bash
java -jar ERENO.jar config/pipelines/pipeline_complete.json
```
Creates benign → attack dataset → trains models → evaluates

### Random Seed Analysis
```bash
java -jar ERENO.jar config/pipelines/pipeline_loop_random_seeds.json
```
Tests 5 different random seeds through full workflow

### Attack-Specific Analysis
```bash
java -jar ERENO.jar config/pipelines/pipeline_loop_attack_segments.json
```
Tests individual attacks and combinations

### Hyperparameter Optimization
```bash
# Use Python optimizer that generates configs
python tools/optuna_opt.py
```
Systematic hyperparameter search using Optuna

## See Also

- [DATABASE_GUIDE.md](DATABASE_GUIDE.md) - Tracking database details
- [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md) - Configuration file reference
- [README.md](README.md) - Project overview and setup
