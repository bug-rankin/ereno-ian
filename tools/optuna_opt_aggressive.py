#!/usr/bin/env python3
"""Aggressive Optuna optimizer with wider search spaces for finding stealthy attacks.

This version uses much wider parameter ranges to explore extreme configurations
that might evade detection better.

Usage: python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import optuna
from optuna.samplers import TPESampler, CmaEsSampler, NSGAIISampler
from optuna.pruners import MedianPruner

from optimizer_db import OptimizerDatabasePython

REPO_ROOT = Path(__file__).resolve().parent.parent
BASE_CONFIG = REPO_ROOT / "config" / "configparams.json"
JAR = REPO_ROOT / "target" / "ERENO-1.0-SNAPSHOT-uber.jar"

# Map attack keys to their uc file paths
ATTACK_FILE_MAP = {
    'randomReplay': 'config/attacks/uc01_random_replay.json',
    'inverseReplay': 'config/attacks/uc02_inverse_replay.json',
    'masqFault': 'config/attacks/uc03_masquerade_fault.json',
    'masqNormal': 'config/attacks/uc04_masquerade_normal.json',
    'randomInjection': 'config/attacks/uc05_injection.json',
    'highStNumInjection': 'config/attacks/uc06_high_stnum_injection.json',
    'flooding': 'config/attacks/uc07_flooding.json',
    'greyhole': 'config/attacks/uc08_grayhole.json'
}

# list to record mapping from optuna param names to config paths
PARAM_MAP = []

# selected attack (set in main)
SELECT_ATTACK = None
ATTACK_CONFIG_JSON = None  # Will hold the loaded attack config


def write_config(base_cfg_path, out_path, patch):
    with open(base_cfg_path, 'r', encoding='utf-8') as f:
        cfg = json.load(f)
    # deep merge patch into cfg (simple nested dict assignment)
    def merge(d, u):
        for k, v in u.items():
            if isinstance(v, dict):
                d[k] = merge(d.get(k, {}), v)
            else:
                d[k] = v
        return d

    cfg = merge(cfg, patch)
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(cfg, f, indent=2)


def run_experiment(config_path: Path, outdir: Path, seed: int, classifier: str = 'j48', attack: str = 'random_replay'):
    outdir.mkdir(parents=True, exist_ok=True)
    cmd = [
        'java', '-cp', str(JAR),
        'br.ufu.facom.ereno.experiment.ExperimentRunner',
        str(config_path), str(outdir), classifier, attack, str(seed), str(0.7)
    ]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    out = proc.stdout
    # find first JSON blob in output
    jstart = out.find('{')
    if jstart < 0:
        raise RuntimeError(f"No JSON output from runner. stdout:\n{out}")
    jtxt = out[jstart:].strip()
    try:
        # Sanitize Windows backslashes before parsing (same as JavaOptimizer does)
        sanitized = jtxt.replace('\\', '\\\\')
        res = json.loads(sanitized)
    except json.JSONDecodeError:
        # try to trim trailing garbage
        for i in range(len(jtxt), 0, -1):
            try:
                candidate = jtxt[:i]
                sanitized = candidate.replace('\\', '\\\\')
                return json.loads(sanitized)
            except Exception:
                continue
        raise
    return res


def objective(trial: optuna.trial.Trial):
    # objective builds suggestions dynamically for the selected attack only
    if SELECT_ATTACK is None or ATTACK_CONFIG_JSON is None:
        raise RuntimeError("SELECT_ATTACK not set; pass --attack in CLI and set SELECT_ATTACK in main()")

    attack_key = SELECT_ATTACK
    attack_cfg = ATTACK_CONFIG_JSON  # Use the loaded attack file config

    # Build optimized attack config (flat structure like uc files)
    optimized_attack = {}

    def set_in_config(path, value):
        # path is like ['delayMs','min']
        if len(path) == 0:
            return
        node = optimized_attack
        for k in path[:-1]:
            if k not in node or not isinstance(node[k], dict):
                node[k] = {}
            node = node[k]
        node[path[-1]] = value

    # AGGRESSIVE search space configuration
    # Define much wider ranges for each parameter type
    def traverse(path_prefix, node):
        # path_prefix is list of keys from attack root
        if isinstance(node, dict):
            # min/max pair special handling with MUCH wider ranges
            if 'min' in node and 'max' in node and isinstance(node['min'], (int, float)) and isinstance(node['max'], (int, float)):
                base_min = node['min']
                base_max = node['max']
                param_min_name = '_'.join(path_prefix + ['min'])
                
                # AGGRESSIVE: Search from 0.1x to 10x the base range
                if isinstance(base_min, int) and isinstance(base_max, int):
                    # For integers, use much wider range
                    search_low = max(0, int(base_min * 0.1))
                    search_high = int(base_max * 10)
                    
                    v_min = trial.suggest_int(param_min_name, search_low, search_high)
                    param_max_name = param_min_name.replace('_min', '_max')
                    # Allow max to be much larger than min
                    v_max = trial.suggest_int(param_max_name, v_min + 1, max(v_min + 1, search_high))
                else:
                    # For floats, even wider range
                    search_low = max(0.0, float(base_min) * 0.05)
                    search_high = float(base_max) * 20.0
                    
                    v_min = trial.suggest_float(param_min_name, search_low, search_high)
                    param_max_name = param_min_name.replace('_min', '_max')
                    v_max = trial.suggest_float(param_max_name, v_min + 0.01, max(v_min + 0.01, search_high))
                
                # record and set
                PARAM_MAP.append((param_min_name, path_prefix + ['min']))
                PARAM_MAP.append((param_max_name, path_prefix + ['max']))
                set_in_config(path_prefix + ['min'], v_min)
                set_in_config(path_prefix + ['max'], v_max)
                return
            # otherwise iterate children
            for k, v in node.items():
                traverse(path_prefix + [k], v)
        elif isinstance(node, bool):
            param_name = '_'.join(path_prefix)
            val = trial.suggest_categorical(param_name, [True, False])
            PARAM_MAP.append((param_name, path_prefix))
            set_in_config(path_prefix, val)
        elif isinstance(node, (int, float)):
            param_name = '_'.join(path_prefix)
            # heuristic for probability-like values
            if 0.0 <= float(node) <= 1.0:
                # Full range for probabilities
                val = trial.suggest_float(param_name, 0.0, 1.0)
            else:
                # AGGRESSIVE: Much wider range
                if isinstance(node, int):
                    low = max(1, int(node * 0.1))  # Down to 10% of base
                    high = int(node * 20)  # Up to 20x base
                    val = trial.suggest_int(param_name, low, high)
                else:
                    low = max(0.001, float(node) * 0.05)  # Down to 5% of base
                    high = float(node) * 50.0  # Up to 50x base
                    val = trial.suggest_float(param_name, low, high)
            PARAM_MAP.append((param_name, path_prefix))
            set_in_config(path_prefix, val)
        else:
            # skip arrays and strings for now
            return

    traverse([], attack_cfg)

    # Add attackType and enabled from base config
    if 'attackType' in attack_cfg:
        optimized_attack['attackType'] = attack_cfg['attackType']
    if 'enabled' not in optimized_attack:
        optimized_attack['enabled'] = True

    # Load base config and merge in optimized attack
    with open(BASE_CONFIG, 'r', encoding='utf-8') as f:
        full_config = json.load(f)
    
    # Update the attack params with optimized values
    if 'attacksParams' not in full_config:
        full_config['attacksParams'] = {}
    full_config['attacksParams'][attack_key] = optimized_attack
    full_config['gooseFlow']['numberOfMessages'] = 5000
    full_config['randomSeed'] = trial.number + int(time.time())

    tmpdir = Path(tempfile.mkdtemp(prefix='optuna_aggressive_'))
    cfg_path = tmpdir / 'cfg.json'
    with open(cfg_path, 'w', encoding='utf-8') as f:
        json.dump(full_config, f, indent=2)

    seed = full_config['randomSeed']

    try:
        res = run_experiment(cfg_path, tmpdir, seed=seed)
    except Exception as e:
        print('Experiment failed:', e)
        # return high penalty
        shutil.rmtree(tmpdir, ignore_errors=True)
        # return an out-of-band penalty > 1.0 so it cannot be confused with a valid F1 score
        return 2.0

    # read f1 metric printed by ExperimentRunner
    metric = float(res.get('metric_f1', 1.0))
    # minimize F1 (lower F1 means attack is harder to detect)
    # save trial artifacts
    with open(tmpdir / 'result.json', 'w', encoding='utf-8') as f:
        json.dump({'params': trial.params, 'result': res}, f, indent=2)

    # keep the tmpdir for inspection; optuna will manage best candidate separately
    return metric


def main():
    parser = argparse.ArgumentParser(
        description='Aggressively optimize attack parameters with wider search spaces',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Sampler details:
  tpe     - Tree-structured Parzen Estimator (default, Bayesian)
  cmaes   - Covariance Matrix Adaptation Evolution Strategy (excellent for continuous params)
  nsgaii  - Non-dominated Sorting Genetic Algorithm II (genetic algorithm approach)

This aggressive version uses 10-50x wider search ranges than the standard optimizer.

Example:
  python tools/optuna_opt_aggressive.py --attack randomReplay --trials 200 --sampler cmaes
''')
    parser.add_argument('--trials', type=int, default=100, help='Number of optimization trials')
    parser.add_argument('--study-name', type=str, default='ereno_aggressive', help='Name of the Optuna study')
    parser.add_argument('--attack', type=str, default='randomReplay', 
                        help='Attack key inside attacksParams to optimize (e.g. randomReplay, greyhole)')
    parser.add_argument('--storage', type=str, default=None,
                        help='Optional sqlite storage for resuming studies, e.g. sqlite:///optuna.db')
    parser.add_argument('--sampler', type=str, default='cmaes', choices=['tpe', 'cmaes', 'nsgaii'],
                        help='Optimization algorithm: tpe (Bayesian), cmaes (Evolution Strategy), nsgaii (Genetic Algorithm)')
    parser.add_argument('--pruner', action='store_true',
                        help='Enable median pruner to terminate unpromising trials early')
    parser.add_argument('--n-startup-trials', type=int, default=20,
                        help='Number of random trials before sampler starts (for TPE/CMA-ES)')
    args = parser.parse_args()

    # set selected attack globally for the objective
    global SELECT_ATTACK, ATTACK_CONFIG_JSON
    SELECT_ATTACK = args.attack
    
    # Initialize optimizer database
    optimizer_db = OptimizerDatabasePython()
    
    # Check for previous best result
    previous_best = optimizer_db.get_best_for_attack(args.attack)
    if previous_best:
        print(f"\nWill use previous best as starting point for exploration.")
        print(f"Previous best params: {len(previous_best['best_parameters'])} parameters")
    else:
        print(f"\nNo previous results found for '{args.attack}'. Starting fresh.")
    
    # Load the attack config from uc file
    if args.attack not in ATTACK_FILE_MAP:
        print(f"Error: Unknown attack '{args.attack}'. Available: {list(ATTACK_FILE_MAP.keys())}")
        sys.exit(1)
    
    attack_file = REPO_ROOT / ATTACK_FILE_MAP[args.attack]
    if not attack_file.exists():
        print(f"Error: Attack file not found: {attack_file}")
        sys.exit(1)
    
    with open(attack_file, 'r', encoding='utf-8') as f:
        ATTACK_CONFIG_JSON = json.load(f)
    
    print(f"Loaded attack config from: {attack_file}")

    # Create sampler based on user choice
    if args.sampler == 'tpe':
        sampler = TPESampler(
            n_startup_trials=args.n_startup_trials,
            multivariate=True,  # Use multivariate TPE for better performance
            seed=42  # For reproducibility
        )
        print(f"Using TPE (Bayesian) sampler with {args.n_startup_trials} random startup trials")
    elif args.sampler == 'cmaes':
        sampler = CmaEsSampler(
            n_startup_trials=args.n_startup_trials,
            seed=42,
            warn_independent_sampling=False  # Suppress warnings for categorical params
        )
        print(f"Using CMA-ES (Evolution Strategy) sampler with {args.n_startup_trials} random startup trials")
    elif args.sampler == 'nsgaii':
        sampler = NSGAIISampler(
            population_size=min(50, args.trials // 2),  # Adaptive population size
            crossover_prob=0.9,
            mutation_prob=0.1,
            seed=42
        )
        print(f"Using NSGA-II (Genetic Algorithm) sampler with population size {min(50, args.trials // 2)}")
    else:
        sampler = TPESampler(n_startup_trials=args.n_startup_trials, seed=42)

    # Create pruner if requested
    pruner = MedianPruner(n_startup_trials=5, n_warmup_steps=3) if args.pruner else None
    if args.pruner:
        print("Median pruner enabled - will terminate unpromising trials early")

    study = optuna.create_study(
        direction='minimize',
        study_name=args.study_name,
        storage=args.storage,
        load_if_exists=True,
        sampler=sampler,
        pruner=pruner
    )
    
    # Enqueue previous best parameters if available
    if previous_best and previous_best['best_parameters']:
        print(f"\nEnqueuing previous best parameters as trial 0...")
        try:
            study.enqueue_trial(previous_best['best_parameters'])
            print(f"Successfully enqueued {len(previous_best['best_parameters'])} parameters")
        except Exception as e:
            print(f"Warning: Could not enqueue previous best: {e}")
    
    print(f"\nAGGRESSIVE OPTIMIZATION MODE")
    print(f"Search ranges: 0.05x - 50x base values")
    print(f"Dataset size: 5000 messages (for better accuracy)")
    print(f"Optimizing attack: {args.attack}")
    print(f"Total trials: {args.trials}")
    print(f"Study: {args.study_name}\n")
    
    study.optimize(objective, n_trials=args.trials, show_progress_bar=True)

    print('\n' + '='*60)
    print('OPTIMIZATION COMPLETE')
    print('='*60)
    print(f'Best F1 score (lower=better): {study.best_value:.6f}')
    print(f'Best trial number: {study.best_trial.number}')
    print(f'Total trials completed: {len(study.trials)}')
    print(f'\nBest parameters:')
    for param, value in study.best_params.items():
        print(f'  {param}: {value}')

    # Write best config in uc01 attack file format
    best_cfg = Path('target') / f'opt_{SELECT_ATTACK}_best.json'
    
    # Start with the original attack config structure
    best_attack_config = json.loads(json.dumps(ATTACK_CONFIG_JSON))  # Deep copy
    
    # Build a mapping of expected param names -> config paths
    def build_param_map(node, path_prefix=None, out=None):
        if out is None:
            out = {}
        if path_prefix is None:
            path_prefix = []
        if isinstance(node, dict):
            if 'min' in node and 'max' in node and isinstance(node['min'], (int, float)) and isinstance(node['max'], (int, float)):
                param_min = '_'.join(path_prefix + ['min'])
                param_max = param_min.replace('_min', '_max')
                out[param_min] = path_prefix + ['min']
                out[param_max] = path_prefix + ['max']
                return out
            for k, v in node.items():
                build_param_map(v, path_prefix + [k], out)
        elif isinstance(node, bool) or isinstance(node, (int, float)):
            param = '_'.join(path_prefix)
            out[param] = path_prefix
        return out

    param_map = build_param_map(ATTACK_CONFIG_JSON)

    # Update best_attack_config with optimized values
    for pname, path in param_map.items():
        if pname in study.best_params:
            # Navigate and set value in nested dict
            node = best_attack_config
            for k in path[:-1]:
                if k not in node:
                    node[k] = {}
                node = node[k]
            node[path[-1]] = study.best_params[pname]

    # Write the optimized attack config
    with open(best_cfg, 'w', encoding='utf-8') as f:
        json.dump(best_attack_config, f, indent=2)
    
    print(f'\nOptimized attack config written to: {best_cfg}')
    print(f'This file can be used directly with ActionRunner in:')
    print(f'  config/actions/action_create_attack_dataset.json')
    
    # Save result to optimizer database
    try:
        sampler_name = f"optuna_{args.sampler}"
        optimizer_db.save_result(
            attack_key=SELECT_ATTACK,
            optimizer_type=sampler_name,
            num_trials=len(study.trials),
            best_f1=study.best_value,
            best_params=study.best_params,
            attack_combination="",
            config_base_path=str(BASE_CONFIG),
            notes=f"Aggressive optimization with {args.sampler} sampler"
        )
        print(f'\nResult saved to optimizer database for future runs.')
    except Exception as e:
        print(f'\nWarning: Could not save to optimizer database: {e}')


if __name__ == '__main__':
    main()
