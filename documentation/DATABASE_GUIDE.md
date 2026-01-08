# ERENO Database System Guide

## Overview

ERENO uses a CSV-based tracking database system to record experiments, datasets, models, and evaluation results. This lightweight system provides comprehensive experiment tracking without requiring external database dependencies.

## Database Architecture

### Storage Location
- **Default Directory**: `target/tracking/`
- All databases are stored as CSV files for easy inspection and analysis

### Database Files

The system maintains four interconnected CSV databases:

1. **experiments.csv** - High-level experiment tracking
2. **datasets.csv** - Dataset and configuration tracking
3. **models.csv** - Trained model tracking
4. **results.csv** - Evaluation results tracking

## Database Schemas

### 1. Experiments Database (`experiments.csv`)

Tracks overall experiment execution and metadata.

**Columns:**
- `experiment_id` - Unique identifier (format: EXP_timestamp_random)
- `timestamp` - Creation timestamp (format: yyyy-MM-dd HH:mm:ss)
- `experiment_type` - Type of experiment (e.g., "random_seed_analysis", "attack_specific", "parameter_optimization")
- `description` - Human-readable description
- `pipeline_config_path` - Path to the pipeline configuration file used
- `status` - Current status: "running", "completed", "failed"
- `notes` - Additional notes or metadata

**Purpose:** Link related datasets, models, and results together under a single experiment context.

### 2. Datasets Database (`datasets.csv`)

Tracks all generated datasets with their configurations.

**Columns:**
- `dataset_id` - Unique identifier (format: DS_timestamp_random)
- `timestamp` - Creation timestamp
- `experiment_id` - Foreign key to experiments.csv
- `dataset_type` - Type: "benign", "attack", "test", "training"
- `file_path` - Absolute path to the dataset file
- `format` - File format: "arff", "csv"
- `num_instances` - Number of data instances/rows
- `num_attributes` - Number of features/columns
- `config_path` - Path to the configuration file used
- `attack_types` - Comma-separated list of attack types included
- `random_seed` - Random seed used for generation
- `dataset_structure` - JSON string with structural parameters
- `source_files` - Reference to input files used (e.g., benign data source)
- `notes` - Additional notes

**Purpose:** Track dataset provenance and enable reproducibility.

### 3. Models Database (`models.csv`)

Tracks trained machine learning models.

**Columns:**
- `model_id` - Unique identifier (format: MDL_timestamp_random)
- `timestamp` - Training timestamp
- `experiment_id` - Foreign key to experiments.csv
- `training_dataset_id` - Foreign key to datasets.csv
- `classifier_name` - Classifier type (e.g., "J48", "RandomForest", "NaiveBayes")
- `model_path` - Path to serialized model file (.model)
- `config_path` - Path to training configuration file
- `training_time_ms` - Training duration in milliseconds
- `num_instances` - Number of training instances
- `num_attributes` - Number of features
- `parameters` - JSON string with classifier hyperparameters
- `notes` - Additional notes

**Purpose:** Track model training details and link models to their training data.

### 4. Results Database (`results.csv`)

Tracks model evaluation results and performance metrics.

**Columns:**
- `result_id` - Unique identifier (format: RES_timestamp_random)
- `timestamp` - Evaluation timestamp
- `experiment_id` - Foreign key to experiments.csv
- `model_id` - Foreign key to models.csv
- `test_dataset_id` - Foreign key to datasets.csv (test set used)
- `classifier_name` - Classifier name
- `accuracy` - Overall accuracy (0.0-1.0)
- `precision` - Weighted precision
- `recall` - Weighted recall
- `f_measure` - Weighted F1 score
- `true_positive_rate` - TPR (sensitivity)
- `false_positive_rate` - FPR
- `roc_area` - Area under ROC curve
- `confusion_matrix` - JSON string with confusion matrix
- `per_class_metrics` - JSON string with per-class detailed metrics
- `evaluation_time_ms` - Evaluation duration
- `config_path` - Path to evaluation configuration
- `notes` - Additional notes

**Purpose:** Store comprehensive evaluation metrics for analysis and comparison.

## Database Manager API

### Initialization

```java
// Use default directory (target/tracking)
DatabaseManager dbManager = new DatabaseManager();

// Or specify custom directory
DatabaseManager dbManager = new DatabaseManager("path/to/tracking/db");
```

### Logging Operations

#### Log an Experiment
```java
String experimentId = dbManager.logExperiment(
    "random_seed_analysis",                    // experiment type
    "Testing effect of random seeds",          // description
    "config/pipelines/pipeline_loop.json",     // config path
    "Testing seeds 42, 100, 200"               // notes
);
```

#### Update Experiment Status
```java
dbManager.updateExperimentStatus(experimentId, "completed");
// or "failed"
```

#### Log a Dataset
```java
String datasetId = dbManager.logDataset(
    experimentId,                               // experiment ID
    "training",                                 // dataset type
    "/path/to/dataset.arff",                   // file path
    "arff",                                    // format
    5000,                                      // num instances
    25,                                        // num attributes
    "config/action_create_dataset.json",       // config path
    "uc01_random_replay,uc03_masquerade",     // attack types
    "42",                                      // random seed
    "{\"messagesPerSegment\":1000}",          // structure JSON
    "/path/to/benign_data.arff",              // source files
    "Training set with 5 attack types"         // notes
);
```

#### Log a Model
```java
String modelId = dbManager.logModel(
    experimentId,                               // experiment ID
    datasetId,                                  // training dataset ID
    "RandomForest",                             // classifier name
    "/path/to/RandomForest_model.model",       // model path
    "config/action_train.json",                // config path
    5432,                                      // training time (ms)
    5000,                                      // num instances
    25,                                        // num attributes
    "{\"numIterations\":100}",                 // parameters JSON
    "Default RandomForest configuration"        // notes
);
```

#### Log Evaluation Results
```java
String resultId = dbManager.logResult(
    experimentId,                               // experiment ID
    modelId,                                    // model ID
    testDatasetId,                              // test dataset ID
    "RandomForest",                             // classifier name
    0.9523,                                    // accuracy
    0.9531,                                    // precision
    0.9523,                                    // recall
    0.9522,                                    // f-measure
    0.9523,                                    // TPR
    0.0477,                                    // FPR
    0.9912,                                    // ROC area
    "[[1200,15],[8,1277]]",                   // confusion matrix JSON
    "{\"benign\":{\"precision\":0.99},\"attack\":{\"precision\":0.98}}", // per-class metrics
    342,                                       // evaluation time (ms)
    "config/action_evaluate.json",             // config path
    "Test set evaluation"                       // notes
);
```

### Querying Operations

#### Query Experiments
```java
List<ExperimentEntry> experiments = dbManager.queryExperiments("random_seed_analysis");
List<ExperimentEntry> running = dbManager.queryExperimentsByStatus("running");
ExperimentEntry experiment = dbManager.getExperimentById(experimentId);
```

#### Query Datasets
```java
List<DatasetEntry> datasets = dbManager.queryDatasetsByExperiment(experimentId);
List<DatasetEntry> attackDatasets = dbManager.queryDatasetsByType("attack");
DatasetEntry dataset = dbManager.getDatasetById(datasetId);
```

#### Query Models
```java
List<ModelEntry> models = dbManager.queryModelsByExperiment(experimentId);
List<ModelEntry> rfModels = dbManager.queryModelsByClassifier("RandomForest");
ModelEntry model = dbManager.getModelById(modelId);
```

#### Query Results
```java
List<ResultEntry> results = dbManager.queryResultsByExperiment(experimentId);
List<ResultEntry> modelResults = dbManager.queryResultsByModel(modelId);
ResultEntry result = dbManager.getResultById(resultId);
```

## Integration with Actions

All action classes automatically integrate with the tracking system:

### CreateBenignAction
- Logs dataset with type "benign"
- Records generation parameters (messages, fault probability)
- Links to experiment if experimentId provided

### CreateAttackDatasetAction
- Logs dataset with type "attack" or "training"/"test"
- Records attack types, structure, and source files
- Tracks segment configuration

### TrainModelAction
- Logs each trained model
- Records training time and hyperparameters
- Links models to training datasets

### EvaluateAction
- Logs comprehensive evaluation results
- Records all performance metrics
- Links results to models and test datasets

## Enabling/Disabling Tracking

In action configuration files:

```json
{
  "output": {
    "enableTracking": true,  // Set to false to disable tracking
    "experimentId": "EXP_1234567890_1234"  // Optional: link to existing experiment
  }
}
```

## Querying and Analysis

### Manual Inspection
CSV files can be opened in any spreadsheet application or text editor for manual inspection.

### Python Analysis
Use the provided Python tools for advanced querying:

```bash
# Query experiments
python tools/analyze_tracking.py --experiment-id EXP_1234567890_1234

# Compare results across experiments
python tools/analyze_tracking.py --compare-experiments EXP_111 EXP_222

# Export results for a specific attack type
python tools/analyze_tracking.py --attack-type uc01_random_replay
```

### Java Programmatic Access
```java
DatabaseManager db = new DatabaseManager();

// Get all completed experiments
List<ExperimentEntry> completed = db.queryExperimentsByStatus("completed");

// For each experiment, get best model by accuracy
for (ExperimentEntry exp : completed) {
    List<ResultEntry> results = db.queryResultsByExperiment(exp.experimentId);
    ResultEntry best = results.stream()
        .max(Comparator.comparing(r -> Double.parseDouble(r.accuracy)))
        .orElse(null);
    
    if (best != null) {
        System.out.println("Best model for " + exp.experimentId + ": " + 
                          best.classifierName + " with accuracy " + best.accuracy);
    }
}
```

## Database Maintenance

### Backup
```bash
# Copy entire tracking directory
cp -r target/tracking target/tracking_backup_$(date +%Y%m%d)
```

### Reset
```bash
# Delete and re-initialize (CAUTION: loses all data)
rm -rf target/tracking
# Tracking will auto-initialize on next run
```

### Export to Different Format
```python
import pandas as pd

# Convert to SQLite
experiments = pd.read_csv('target/tracking/experiments.csv')
datasets = pd.read_csv('target/tracking/datasets.csv')
models = pd.read_csv('target/tracking/models.csv')
results = pd.read_csv('target/tracking/results.csv')

import sqlite3
conn = sqlite3.connect('ereno_tracking.db')

experiments.to_sql('experiments', conn, if_exists='replace', index=False)
datasets.to_sql('datasets', conn, if_exists='replace', index=False)
models.to_sql('models', conn, if_exists='replace', index=False)
results.to_sql('results', conn, if_exists='replace', index=False)

conn.close()
```

## Best Practices

1. **Use Meaningful Experiment IDs**: Link related actions to the same experiment
2. **Add Descriptive Notes**: Future you will thank current you
3. **Regular Backups**: Especially before major experimental runs
4. **Verify Tracking Enabled**: Check `enableTracking: true` in configs
5. **Cross-Reference Correctly**: Ensure IDs are properly linked across databases

## Troubleshooting

### Database Not Created
- Check write permissions for `target/tracking/` directory
- Verify `enableTracking: true` in configuration
- Check logs for initialization messages

### Missing Entries
- Verify action completed successfully (check logs)
- Ensure `enableTracking` was not disabled
- Check if experimentId was provided when needed

### Corrupt CSV Files
- Manual editing may break CSV structure
- Use proper CSV escaping for fields with commas/quotes
- Restore from backup if severely corrupted

## See Also

- [ACTION_RUNNER_GUIDE.md](ACTION_RUNNER_GUIDE.md) - How actions use the database
- [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md) - Tracking configuration options
- [README.md](README.md) - Main project documentation
