# ERENO Quick Start Usage Guide

## Introduction

**ERENO** is an IEC 61850 smart grid intrusion detection system (IDS) testing framework. It simulates GOOSE protocol traffic, generates attack datasets, trains machine learning models, and evaluates their detection performance.

This guide provides a quick start to get you running experiments immediately.

## Prerequisites

**Java**: JDK 11 or higher
```bash
java -version  # Should show 11+
```

**Python** (for optimization tools): 3.7+
```bash
python --version
```

**Build the Project**:
```bash
# On Windows
mvnw.cmd clean package

# On Linux/Mac
./mvnw clean package
```

The JAR file will be created at: `target/ERENO-1.0-SNAPSHOT-uber.jar`

## Basic Workflow

ERENO follows a simple 4-step workflow:

```
1. Generate Benign Data  →  2. Create Attack Dataset  →  3. Train Model  →  4. Evaluate Model
```

Each step is executed through the **ActionRunner** with a JSON configuration file.

## Quick Start: Running Your First Experiment

### Step 1: Generate Benign Traffic

Create legitimate (normal) GOOSE protocol traffic:

```bash
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_create_benign.json
```

**Output**: `target/benign_data/42_5%fault_benign_data.arff`

### Step 2: Create Attack Dataset

Generate a labeled dataset with attack segments:

```bash
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_create_attack_dataset.json
```

**Output**: `target/attack_data/<attack>_attack_dataset.arff`

### Step 3: Train ML Model

Train a classifier (J48, RandomForest, NaiveBayes, etc.):

```bash
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_train_model.json
```

**Output**: `target/models/<classifier>_model.model`

### Step 4: Evaluate Model

Test the trained model and get detection metrics:

```bash
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_evaluate.json
```

**Output**: `target/evaluation/evaluation_results.json`

## Running Complete Pipelines

Instead of running each step manually, use **pipelines** to execute multiple actions sequentially:

```bash
# Complete workflow: benign → attack → train → evaluate
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_complete.json
```

**Available Pipelines**:
- `pipeline_complete.json` - Full workflow (benign → attack → train → evaluate)
- `pipeline_train_evaluate.json` - Training and evaluation only
- `pipeline_loop_random_seeds.json` - Multiple iterations with different random seeds
- `pipeline_loop_attack_segments.json` - Multiple attacks with different segment compositions
- `pipeline_loop_parameters.json` - Parameter sweep experiments

## Attack Types

ERENO supports 8 attack scenarios (Use Cases):

| Attack Code | Name | Description |
|-------------|------|-------------|
| **UC01** | Random Replay | Replays captured messages with randomized stNum |
| **UC02** | Inverse Replay | Replays messages with inverted status values |
| **UC03** | Masquerade Fault | Impersonates legitimate IED sending fault messages |
| **UC04** | Masquerade Normal | Impersonates legitimate IED sending normal messages |
| **UC05** | Message Injection | Injects crafted messages into traffic |
| **UC06** | High stNum Injection | Injects messages with artificially high sequence numbers |
| **UC07** | Message Flooding | Floods network with high-frequency messages |
| **UC08** | Grayhole | Selectively drops messages while forwarding others |

**To test a specific attack**, edit the attack configuration file:
```bash
# Edit config/attacks/uc01_random_replay.json
# Then run:
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_create_attack_dataset.json
```

## Configuration Files

All ERENO behavior is controlled through JSON configuration files.

**Directory Structure**:
```
config/
├── actions/           # Individual action configs
│   ├── action_create_benign.json
│   ├── action_create_attack_dataset.json
│   ├── action_train_model.json
│   ├── action_evaluate.json
│   └── action_compare.json
├── attacks/           # Attack-specific parameters
│   ├── uc01_random_replay.json
│   ├── uc02_inverse_replay.json
│   └── ... (uc03-uc08)
└── pipelines/         # Multi-step workflows
    ├── pipeline_complete.json
    └── pipeline_loop_*.json
```

**Quick Editing**:
- **Change random seed**: Edit `commonConfig.randomSeed` in any config
- **Change attack type**: Edit `attackConfigFiles` in `action_create_attack_dataset.json`
- **Change classifier**: Edit `classifierName` in `action_train_model.json`
- **Change output directory**: Edit `output.directory` in any action config

## Database Tracking

ERENO automatically tracks all experiments in CSV databases:

**Location**: `target/tracking/`

**Files**:
- `experiments.csv` - High-level experiment tracking
- `datasets.csv` - All generated datasets
- `models.csv` - Trained models
- `results.csv` - Evaluation results
- `optimizer_results.csv` - Optimization runs

**View tracking data**:
```bash
# Open CSV files in Excel, LibreOffice, or text editor
notepad target\tracking\experiments.csv
```

## Optimization System

ERENO includes powerful optimization tools to find **stealthy attack parameters** (hardest-to-detect configurations).

### Three Optimizers Available

1. **Optuna (Standard)** - Bayesian optimization with Tree-structured Parzen Estimator
```bash
python tools/optuna_opt.py --attack randomReplay --trials 100
```

2. **Optuna Aggressive** - Wider search spaces with CMA-ES sampler
```bash
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
```

3. **JavaOptimizer** - Hill climbing with random restarts
```bash
java -cp target/ERENO-1.0-SNAPSHOT-uber.jar \
  br.ufu.facom.ereno.tools.JavaOptimizer --attack randomReplay --trials 50
```

**Goal**: Minimize F1 score (lower = harder to detect)

**Iterative Improvement**: Each optimizer run automatically starts from the previous best configuration for that attack, enabling continuous improvement across multiple runs.

**Query best results**:
```bash
python tools/query_optimizer_db.py best --attack randomReplay
```

## Common Commands

```bash
# Build project
mvnw.cmd clean package

# Run single action
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar <config-file.json>

# Run complete pipeline
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_complete.json

# Optimize attack parameters (Python)
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes

# Query optimization results
python tools/query_optimizer_db.py list
python tools/query_optimizer_db.py best --attack randomReplay
python tools/query_optimizer_db.py stats

# View tracking data
notepad target\tracking\experiments.csv
notepad target\tracking\results.csv
```

## Example Workflows

### Workflow 1: Single Attack Evaluation

Test how well a classifier detects a specific attack:

```bash
# 1. Generate benign data
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_create_benign.json

# 2. Create randomReplay attack dataset
# Edit config/actions/action_create_attack_dataset.json to use uc01_random_replay.json
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_create_attack_dataset.json

# 3. Train RandomForest classifier
# Edit config/actions/action_train_model.json to set classifierName: "RandomForest"
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_train_model.json

# 4. Evaluate
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/actions/action_evaluate.json

# 5. Check results
notepad target\evaluation\evaluation_results.json
```

### Workflow 2: Multi-Seed Reproducibility Study

Test classifier stability across multiple random seeds:

```bash
# Use loop pipeline with random seed variation
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_loop_random_seeds.json

# Results automatically tracked in database
notepad target\tracking\results.csv
```

### Workflow 3: Attack Optimization

Find the hardest-to-detect attack parameters:

```bash
# 1. Install Python dependencies
pip install -r tools/requirements.txt

# 2. Run aggressive optimization for 200 trials
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes

# 3. Query best configuration found
python tools/query_optimizer_db.py best --attack randomReplay

# 4. Run additional optimization to improve further (iterative improvement)
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 100 --sampler tpe

# 5. Check if new run improved on previous best
python tools/query_optimizer_db.py stats
```

### Workflow 4: Compare Multiple Attacks

Evaluate which attacks are hardest to detect:

```bash
# Run loop pipeline testing all 8 attack types
# Edit pipeline_loop_parameters.json to loop over attacks
java -jar target/ERENO-1.0-SNAPSHOT-uber.jar config/pipelines/pipeline_loop_parameters.json

# Compare results
notepad target\tracking\results.csv
# Sort by F1 score ascending (lowest = hardest to detect)
```

## GUIDES

**For Basic Users**:
1. Read [ACTION_RUNNER_GUIDE.md](ACTION_RUNNER_GUIDE.md) for action details
2. Read [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md) for config options
3. Run the example pipelines in `config/pipelines/`

**For Researchers**:
1. Study [EXPERIMENT_BATCHES.md](EXPERIMENT_BATCHES.md) for systematic experiments
2. Study [OPTIMIZATION_GUIDE.md](OPTIMIZATION_GUIDE.md) for attack optimization
3. Review [DATABASE_GUIDE.md](DATABASE_GUIDE.md) for data analysis
4. Design custom loop pipelines for your research questions

**For Developers**:
1. Explore source code in `src/main/java/br/ufu/facom/ereno/`
2. Review action handlers in `actions/` package
3. Study attack implementations in `attacks/` package
4. Examine evaluation system in `evaluation/` package
