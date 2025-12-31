# ERENO Usage Guide

### 1. Build the Project
```powershell
.\mvnw.cmd clean package -DskipTests
```
### 2. Run the Application
```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_complete.json
```

## Available Actions:

### 1. Train and Test
**Main Config:** `config\main_config.json`  
**Action Config:** `config\action_train_and_test.json`

**What it does:**
- Generates benign data (if missing)
- Creates training dataset with attack segments
- Creates test dataset
- Trains ML models (J48, RandomForest, NaiveBayes, REPTree)
- Evaluates models and saves results

### 2. Create Benign Data
**Action Config:** `config\action_create_benign.json`

**What it does:**
- Generates legitimate GOOSE traffic
- Simulates normal operation with optional faults (configurable probability)

### 3. Create Training Dataset
**Action Config:** `config\action_create_training.json`

**What it does:**
- Loads benign data
- Generates attack segments
- Combines into labeled training dataset

### 4. Create Test Dataset
**Action Config:** `config\action_create_test.json`

**What it does:**
- Creates test dataset with attack segments
- Evaluates against existing trained models
- Generates performance metrics


### 5. Compare Datasets
**Action Config:** `config\action_compare.json`

**What it does:**
- Compares benign vs attack datasets
- Analyzes field changes, timing patterns, sequence anomalies
- Generates comparison reports

## Pipeline Execution

### Standard Pipeline
**Config:** `config\pipeline_complete.json`

Executes all actions sequentially:
1. Create benign data
2. Create training dataset
3. Create test dataset  
4. Train ML models
5. Evaluate models

```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_complete.json
```

### Loop Pipelines

Loop pipelines allow you to create multiple training datasets with variations and evaluate them systematically.

#### Random Seed Variation
**Config:** `config\pipeline_loop_random_seeds.json`

Creates multiple datasets using different random seeds for attack generation:
- Generates baseline benign data and test dataset (iteration 1)
- Loops through specified random seeds (e.g., 42, 24, 23, 25, 26, 27, 28, 29, 31, 32)
- For each seed:
  - Creates training dataset with that seed
  - Trains models on the dataset
  - Evaluates against baseline test dataset

**Use case:** Study impact of randomness in attack generation on model performance

```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_loop_random_seeds.json
```

#### Attack Segment Variation
**Config:** `config\pipeline_loop_attack_segments.json`

Creates datasets with different combinations of attack types:
- Tests specific attack combinations (e.g., replay attacks only, injection attacks, mixed scenarios)
- Enables training specialized detectors for attack categories
- Each iteration uses different enabled attack segments

**Use case:** Train and compare specialized vs. generalized attack detectors

```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_loop_attack_segments.json
```

#### Parameter Variation
**Config:** `config\pipeline_loop_parameters.json`

Varies dataset structure parameters:
- Messages per segment (500, 1000, 2000)
- Shuffle segments (true/false)
- Include benign segment (true/false)

**Use case:** Optimize dataset structure for best model performance

```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\pipeline_loop_parameters.json
```

### Loop Configuration Structure

```json
{
  "action": "pipeline",
  "pipeline": [
    // Pre-loop steps (run once)
  ],
  "loop": {
    "variationType": "randomSeed|attackSegments|parameters",
    "values": [/* list of values to iterate */],
    "baselineDataset": "path/to/test_dataset.arff",
    "steps": [
      {
        "action": "create_attack_dataset",
        "actionConfigFile": "config/action_create_attack_dataset.json",
        "parameterOverrides": {
          "output": {
            "directory": "target/training_variations",
            "filename": "training_dataset_${iteration}.arff"
          }
        }
      }
      // Additional loop steps...
    ]
  }
}
```

**Variation Types:**
- `randomSeed`: Iterate over different random seeds (values: list of integers)
- `attackSegments`: Enable specific attack combinations (values: list of attack name arrays)
- `parameters`: Vary any config parameters (values: list of parameter maps)

**Output Organization:**
- `target/training_variations/` - Training datasets from loop iterations
- `target/models_variations/seed_N/` - Models from iteration N
- `target/evaluation_variations/seed_N/` - Evaluation results from iteration N

## Future Plans with ERENO:
- Reimplement Optimization (w/ corresponding toggle)
- Reimplement Hybrid Attacks
- Increase Modularity via shift from Create_Test_Dataset & Create_Training_Dataset to Create_Dataset, Train_Models, & Evaluate_Dataset