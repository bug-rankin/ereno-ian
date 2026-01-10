# ERENO Attack Parameter Optimization Guide

## Overview

ERENO includes a sophisticated optimization system for finding attack parameters that evade machine learning detection. The system uses multiple optimization algorithms to minimize the F1 score (lower F1 = harder to detect = more stealthy attack).

### Why Optimize?

Default attack parameters are often easily detected by ML classifiers. Optimization helps find:
- **Parameter ranges** that produce subtle, realistic-looking attacks
- **Timing patterns** that blend with legitimate traffic
- **Message characteristics** that minimize classification accuracy
- **Stealthy configurations** that achieve high evasion rates

### Key Concept: Minimizing F1

Unlike typical ML optimization (maximize accuracy), we **minimize F1 score**:
- F1 = 1.0 means perfect detection (bad for attacker)
- F1 = 0.0 means complete evasion (ideal for attacker)
- Lower F1 = more stealthy attack

## Optimization Algorithms

ERENO provides three optimizer implementations with different strategies:

### 1. Optuna (Standard)

**File**: `tools/optuna_opt.py`

**Algorithm Options**:
- **TPE** (Tree-structured Parzen Estimator) - Bayesian optimization, default
- **CMA-ES** (Covariance Matrix Adaptation Evolution Strategy) - Evolution strategy
- **NSGA-II** (Non-dominated Sorting Genetic Algorithm II) - Genetic algorithm

**Search Space**: Conservative ranges around baseline values

**Usage**:
```bash
# TPE (Bayesian) - good for quick convergence
python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler tpe

# CMA-ES - excellent for continuous parameters
python tools/optuna_opt.py --attack randomReplay --trials 100 --sampler cmaes

# NSGA-II - genetic algorithm approach
python tools/optuna_opt.py --attack randomReplay --trials 100 --sampler nsgaii
```

**When to Use**:
- Standard exploration of parameter space
- 20-100 trials for quick results
- Testing multiple algorithms
- Moderate computational budget

### 2. Optuna (Aggressive)

**File**: `tools/optuna_opt_aggressive.py`

**Algorithm Options**: Same as standard (TPE, CMA-ES, NSGA-II)

**Search Space**: **10-50x wider ranges** than standard
- Explores extreme configurations
- Can find highly unconventional attack patterns
- More likely to discover novel evasion techniques

**Usage**:
```bash
# Aggressive exploration with CMA-ES (recommended)
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes

# Aggressive with TPE
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 150 --sampler tpe
```

**When to Use**:
- Seeking maximum evasion
- Previous optimization plateaued
- 100-200+ trials for thorough search
- Research into extreme attack configurations

**Key Differences from Standard**:
```python
# Standard: Search near baseline
search_range = (baseline * 0.5, baseline * 2.0)

# Aggressive: Much wider exploration  
search_range = (baseline * 0.05, baseline * 50.0)
```

### 3. JavaOptimizer (Hill Climbing)

**File**: `src/main/java/br/ufu/facom/ereno/tools/JavaOptimizer.java`

**Algorithm**: Three-phase hill climbing
1. **Trial 0**: Baseline (no changes)
2. **Random Init**: Random exploration (20% of trials)
3. **Hill Climb**: Mutate current best (remaining 80%)

**Usage**:
```bash
java -cp target/ERENO-1.0-SNAPSHOT-uber.jar \
  br.ufu.facom.ereno.tools.JavaOptimizer \
  --attack randomReplay --trials 50 --seed 42
```

**When to Use**:
- Pure Java environment (no Python dependencies)
- Fast local search around good configurations
- Complementary to Optuna (different algorithm)
- Integration with Java-based workflows

**Advantages**:
- No external dependencies
- Fast execution
- Good for local refinement
- Deterministic with seed

## Optimizer Database System

### Automatic Iterative Improvement

All optimizers integrate with a **central database** that tracks the best parameters found for each attack. This enables **continuous improvement** across multiple optimization runs.

**Database Location**: `target/tracking/optimizer_results.csv`

### How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                    Optimizer Run Flow                        │
├─────────────────────────────────────────────────────────────┤
│  1. Start optimizer for attack "randomReplay"               │
│  2. Query database for previous best                         │
│  3. If found: Load best parameters (e.g., F1=0.234)         │
│  4. Use as starting point (trial 0)                         │
│  5. Run optimization trials                                  │
│  6. Find new best (e.g., F1=0.198)                          │
│  7. Save to database                                         │
│  8. Next run automatically starts from F1=0.198             │
└─────────────────────────────────────────────────────────────┘
```

### Database Schema

```csv
optimizer_id,timestamp,attack_key,attack_combination,optimizer_type,
num_trials,best_metric_f1,best_parameters_json,config_base_path,notes
```

**Fields**:
- `optimizer_id`: Unique ID (format: `OPT_<timestamp>_<random>`)
- `timestamp`: When optimization completed
- `attack_key`: Attack being optimized (e.g., `randomReplay`)
- `attack_combination`: For multi-attack optimization (comma-separated)
- `optimizer_type`: `optuna_tpe`, `optuna_cmaes`, `java_hillclimb`, etc.
- `num_trials`: Number of optimization trials
- `best_metric_f1`: Best F1 score achieved (lower is better)
- `best_parameters_json`: JSON of optimized parameters
- `config_base_path`: Base config file used
- `notes`: Additional information

### Querying the Database

**List all results**:
```bash
python tools/query_optimizer_db.py list
```

**Best result for specific attack**:
```bash
python tools/query_optimizer_db.py best --attack randomReplay
```

**Database statistics**:
```bash
python tools/query_optimizer_db.py stats
```

### Example: Iterative Improvement

```bash
# Monday: First optimization
$ python tools/optuna_opt_aggressive.py --attack randomReplay --trials 100 --sampler cmaes
Best F1: 0.234
Saved to database: OPT_1736121234567_1234

# Tuesday: Continue with different algorithm
$ python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler tpe
Found previous best: F1=0.234 from 2026-01-06
Starting from previous best...
Best F1: 0.198 (improved!)
Saved to database: OPT_1736207634567_5678

# Wednesday: Aggressive exploration
$ python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
Found previous best: F1=0.198 from 2026-01-07
Starting from previous best...
Best F1: 0.187 (further improvement!)
Saved to database: OPT_1736294034567_9012
```

## Optimization Workflow

### Basic Workflow

```bash
# 1. Build the project
mvn clean package

# 2. Run initial optimization (standard, quick)
python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler tpe

# 3. Check results
python tools/query_optimizer_db.py best --attack randomReplay

# 4. Run aggressive optimization for deeper search
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes

# 5. Compare results
python tools/query_optimizer_db.py stats
```

### Advanced Workflow

```bash
# Try multiple algorithms and let database track best
python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler tpe
python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler cmaes
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 100 --sampler cmaes
java -cp target/ERENO-1.0-SNAPSHOT-uber.jar \
  br.ufu.facom.ereno.tools.JavaOptimizer --attack randomReplay --trials 50

# Database automatically tracks best across all runs
python tools/query_optimizer_db.py best --attack randomReplay
```

## Parameter Types Optimized

### Random Replay Attack (UC01)

**Parameters**:
- `count.lambda`: Number of replayed messages (Poisson distribution)
- `windowS.min/max`: Time window for message selection (seconds)
- `delayMs.min/max`: Replay delay range (milliseconds)
- `burst.prob`: Probability of burst replays
- `burst.min/max`: Burst size range
- `burst.gapMs.min/max`: Gap between burst messages
- `reorderProb`: Probability of reordering messages
- `ttlOverride.prob`: Probability of TTL manipulation
- `ttlOverride.valuesMs`: Array of TTL values
- `ethSpoof.srcProb`: Source MAC spoofing probability
- `ethSpoof.dstProb`: Destination MAC spoofing probability

### Inverse Replay (UC02)

**Parameters**:
- `count.lambda`: Number of inverse replays
- `blockLen.min/max`: Block length for reversal
- `delayMs.min/max`: Replay delay range
- `burst.*`: Burst configuration
- `ttlOverride.*`: TTL manipulation

### Masquerade Fault (UC03)

**Parameters**:
- `fault.prob`: Fault occurrence probability
- `fault.durationMs.min/max`: Fault duration
- `cbStatus`: Circuit breaker status
- `incrementStNumOnFault`: StNum increment behavior
- `sqnumMode`: Sequence number mode (`fast`, `slow`)
- `ttlMsValues`: Array of TTL values
- `analog.deltaAbs.min/max`: Analog value perturbation
- `trapArea.multiplier.min/max`: Trap area manipulation
- `trapArea.spikeProb`: Spike probability

### Other Attacks

Similar parameter structures for:
- **UC04**: Masquerade Normal
- **UC05**: Random Injection
- **UC06**: High StNum Injection
- **UC07**: Flooding
- **UC08**: Gray Hole

## Optimization Output Files

### Per-Attack Best Config

**File**: `target/opt_<attack>_best.json`

Contains attack-specific configuration in uc file format:
```json
{
  "attackType": "random_replay",
  "enabled": true,
  "count": {
    "lambda": 823
  },
  "windowS": {
    "min": 2.34,
    "max": 8.76
  },
  "delayMs": {
    "min": 1.23,
    "max": 5.67
  },
  ...
}
```

**Usage**: Can be directly referenced in `action_create_attack_dataset.json`:
```json
{
  "attackSegments": [
    {
      "name": "optimized_random_replay",
      "enabled": true,
      "attackConfig": "target/opt_randomReplay_best.json"
    }
  ]
}
```

### Full Best Config

**File**: `target/opt_best_config.json`

Complete merged configuration file ready to use with ExperimentRunner.

### Trial Artifacts

Temporary directories preserve trial details:
- Config files used
- Generated datasets
- Evaluation results
- Useful for debugging specific trials

## Best Practices

### 1. Start Small, Scale Up

```bash
# Quick exploration (10-20 minutes)
python tools/optuna_opt.py --attack randomReplay --trials 20 --sampler tpe

# Medium search (1-2 hours)
python tools/optuna_opt.py --attack randomReplay --trials 100 --sampler cmaes

# Deep search (4-8 hours)
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
```

### 2. Try Multiple Algorithms

Different algorithms find different optima:
- **TPE**: Fast convergence, good for initial search
- **CMA-ES**: Excellent for continuous parameters, recommended for deep search
- **NSGA-II**: Good for multi-objective (if extended)
- **Java Hill Climb**: Fast local refinement

### 3. Leverage Database Auto-Resume

```bash
# Run overnight optimizations
nohup python tools/optuna_opt_aggressive.py --attack randomReplay --trials 500 --sampler cmaes &

# Check progress next day
python tools/query_optimizer_db.py best --attack randomReplay

# Continue with different algorithm (auto-resumes from best)
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler tpe
```

### 4. Monitor F1 Score Trends

```bash
# Track improvement over time
python tools/query_optimizer_db.py list | grep randomReplay
```

Look for:
- Diminishing returns (plateau)
- Which algorithm works best for each attack
- When to switch strategies

### 5. Use Aggressive Mode Strategically

**Standard mode** for:
- Initial exploration
- Quick iterations
- Staying close to realistic parameters

**Aggressive mode** for:
- Breaking through plateaus
- Research into extreme evasion
- Finding unconventional attack patterns

### 6. Experiment Tracking Integration

Link optimizer results to experiments:
```json
{
  "experiment": {
    "notes": "Used optimized parameters from OPT_1736121234567_1234"
  }
}
```

### 7. Attack-Specific Strategies

**Random Replay**: Focus on timing parameters (`windowS`, `delayMs`)
**Masquerade**: Optimize fault probabilities and analog perturbations
**Injection**: Fine-tune injection rates and sequence numbers
**Flooding**: Balance message rate with stealth

## Common Optimization Scenarios

### Scenario 1: First-Time Attack Optimization

```bash
# Start with standard optimizer, moderate trials
python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler cmaes

# Check result
python tools/query_optimizer_db.py best --attack randomReplay

# If F1 > 0.3, run aggressive
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 150 --sampler cmaes
```

### Scenario 2: Refining Existing Configuration

```bash
# Database has F1=0.25, want to improve further

# Try different algorithm
python tools/optuna_opt.py --attack randomReplay --trials 100 --sampler tpe

# If stuck, go aggressive
python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
```

### Scenario 3: Cross-Algorithm Comparison

```bash
# Run all algorithms with database tracking
for sampler in tpe cmaes nsgaii; do
  python tools/optuna_opt.py --attack randomReplay --trials 50 --sampler $sampler
done

# Compare results
python tools/query_optimizer_db.py stats
```

### Scenario 4: Batch Optimization of All Attacks

```bash
# Optimize all 8 attack types
for attack in randomReplay inverseReplay masqFault masqNormal \
              randomInjection highStNumInjection flooding greyhole; do
  echo "Optimizing $attack..."
  python tools/optuna_opt_aggressive.py --attack $attack --trials 100 --sampler cmaes
done

# Generate report
python tools/query_optimizer_db.py stats
```

## Advanced Features

### Optuna Storage (Resume Studies)

Persist Optuna studies across runs:
```bash
python tools/optuna_opt.py \
  --attack randomReplay \
  --trials 50 \
  --storage sqlite:///optuna_studies.db \
  --study-name random_replay_v1
```

Resume later:
```bash
python tools/optuna_opt.py \
  --attack randomReplay \
  --trials 50 \
  --storage sqlite:///optuna_studies.db \
  --study-name random_replay_v1  # Same name = resume
```

### Early Stopping (Pruning)

Stop unpromising trials early:
```bash
python tools/optuna_opt.py \
  --attack randomReplay \
  --trials 100 \
  --sampler cmaes \
  --pruner  # Enable median pruner
```

### Startup Trials

Control random exploration phase:
```bash
# More random trials before optimization
python tools/optuna_opt.py \
  --attack randomReplay \
  --trials 100 \
  --sampler cmaes \
  --n-startup-trials 30  # Default: 10
```

## Troubleshooting

### Issue: Optimizer Not Finding Database

**Symptom**: "No previous results found" despite previous runs

**Solutions**:
- Check database exists: `ls target/tracking/optimizer_results.csv`
- Verify attack key matches: exact spelling (e.g., `randomReplay` not `random_replay`)
- Check database permissions

### Issue: F1 Not Improving

**Symptom**: F1 score stuck around same value

**Solutions**:
1. Switch to aggressive mode
2. Try different algorithm (CMA-ES if using TPE, vice versa)
3. Increase trials (200+)
4. Check if hitting fundamental detection limit

### Issue: ExperimentRunner Fails

**Symptom**: Trials return F1=2.0 (penalty)

**Solutions**:
- Check JAR is built: `mvn clean package`
- Verify Java heap space: Add `-Xmx4g` to Java command
- Check generated ARFF files for corruption

### Issue: Very Long Runtime

**Symptom**: Each trial takes > 5 minutes

**Solutions**:
- Reduce dataset size in optimizer (already set to 2000-5000)
- Use faster classifier (J48 is default, fastest)
- Run on machine with better CPU
- Use Java optimizer (faster than Python wrapper)

## Performance Expectations

### Typical F1 Score Ranges

**Excellent Evasion**: F1 < 0.15 (very hard to detect)
**Good Evasion**: F1 = 0.15 - 0.25
**Moderate Evasion**: F1 = 0.25 - 0.40
**Poor Evasion**: F1 > 0.40 (easily detected)

### Runtime Estimates

**Per Trial** (2000 messages, J48 classifier):
- ~30-90 seconds per trial
- Varies by attack complexity

**Full Optimization**:
- 50 trials: ~30-60 minutes
- 100 trials: 1-2 hours
- 200 trials: 2-4 hours

### Improvement Expectations

**Initial Runs**: Often see 20-40% F1 reduction
**Refinement Runs**: 5-15% improvement
**Plateauing**: < 5% improvement after multiple runs

## Integration with ERENO Workflow

### Using Optimized Parameters

1. **Optimize attack**:
   ```bash
   python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
   ```

2. **Update attack config**:
   Copy `target/opt_randomReplay_best.json` to `config/attacks/` or reference directly

3. **Create dataset**:
   ```bash
   java -jar ERENO.jar config/actions/action_create_attack_dataset.json
   ```

4. **Train model**:
   ```bash
   java -jar ERENO.jar config/actions/action_train_model.json
   ```

5. **Evaluate**:
   ```bash
   java -jar ERENO.jar config/actions/action_evaluate.json
   ```

### Pipeline Integration

Use optimized configs in pipeline:
```json
{
  "action": "pipeline",
  "pipeline": [
    {
      "action": "create_attack_dataset",
      "actionConfigFile": "config/actions/action_with_optimized_params.json"
    },
    {
      "action": "train_model",
      "actionConfigFile": "config/actions/action_train_model.json"
    },
    {
      "action": "evaluate",
      "actionConfigFile": "config/actions/action_evaluate.json"
    }
  ]
}
```

## Research Applications

### Finding Maximum Evasion

```bash
# Deep search for best possible evasion
python tools/optuna_opt_aggressive.py \
  --attack randomReplay \
  --trials 500 \
  --sampler cmaes \
  --storage sqlite:///max_evasion.db
```

### Algorithm Comparison Studies

```bash
# Compare optimizer effectiveness
for algo in tpe cmaes nsgaii; do
  for attack in randomReplay inverseReplay masqFault; do
    python tools/optuna_opt_aggressive.py \
      --attack $attack \
      --trials 100 \
      --sampler $algo \
      --storage sqlite:///comparison.db \
      --study-name "${attack}_${algo}"
  done
done

# Analyze with Optuna's built-in tools
```

### Parameter Sensitivity Analysis

Use optimizer results to identify critical parameters:
```bash
python tools/query_optimizer_db.py best --attack randomReplay > params.txt
# Manually analyze which parameters vary most between runs
```

## Future Enhancements

Potential additions to optimization system:
- Multi-objective optimization (evasion + realism)
- Constraint-based optimization (parameter bounds)
- Ensemble optimization (combine multiple attacks)
- Real-time optimization feedback
- Visualization of optimization progress
- Automated hyperparameter tuning for optimizers
- Distributed optimization across multiple machines

## References

- **Optuna Documentation**: https://optuna.readthedocs.io/
- **CMA-ES**: Hansen, N. (2016). "The CMA Evolution Strategy"
- **ERENO Paper**: [Original ERENO publication]
- **IEC 61850**: Smart grid communication protocol standard

## Summary

The ERENO optimization system provides:
- ✅ Multiple optimization algorithms (TPE, CMA-ES, NSGA-II, Hill Climbing)
- ✅ Automatic iterative improvement via database
- ✅ Standard and aggressive search modes
- ✅ Cross-optimizer learning and comparison
- ✅ Complete parameter tracking and reproducibility
- ✅ Easy integration with ERENO workflows
- ✅ Command-line tools for querying and analysis

**Key Takeaway**: Run multiple optimizations with different algorithms, let the database track the best results, and continuously improve attack stealthiness over time.
