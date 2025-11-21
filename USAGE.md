# ERENO Usage Guide

### 1. Build the Project
```powershell
.\mvnw.cmd clean package -DskipTests
```
### 2. Run the Application
```powershell
java -jar target\ERENO-1.0-SNAPSHOT-uber.jar config\main_config.json
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

## Future Plans with ERENO:
- Reimplement Optimization (w/ corresponding toggle)
- Reimplement Hybrid Attacks
- Increase Modularity via shift from Create_Test_Dataset & Create_Training_Dataset to Create_Dataset, Train_Models, & Evaluate_Dataset