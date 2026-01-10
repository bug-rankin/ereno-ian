#!/usr/bin/env python3
"""Query and analyze optimizer results database.

Usage:
    python tools/query_optimizer_db.py list
    python tools/query_optimizer_db.py best --attack randomReplay
    python tools/query_optimizer_db.py stats
"""
import argparse
import sys
from pathlib import Path

# Add tools directory to path for optimizer_db import
sys.path.insert(0, str(Path(__file__).parent))

from optimizer_db import OptimizerDatabasePython


def list_all_results(db):
    """List all optimizer results."""
    results = db.get_all_results()
    if not results:
        print("No optimizer results found in database.")
        return
    
    print(f"\n{'='*80}")
    print(f"OPTIMIZER RESULTS DATABASE - {len(results)} entries")
    print(f"{'='*80}\n")
    
    # Group by attack
    by_attack = {}
    for r in results:
        key = r['attack_key'] if r['attack_key'] else r['attack_combination']
        if key not in by_attack:
            by_attack[key] = []
        by_attack[key].append(r)
    
    for attack, attack_results in sorted(by_attack.items()):
        print(f"\n{attack}:")
        print(f"{'-'*80}")
        
        # Sort by F1 score (best first)
        attack_results.sort(key=lambda x: x['best_metric_f1'])
        
        for i, r in enumerate(attack_results, 1):
            best_marker = " ⭐ BEST" if i == 1 else ""
            print(f"  {i}. F1: {r['best_metric_f1']:.6f}{best_marker}")
            print(f"     Date: {r['timestamp']}")
            print(f"     Optimizer: {r['optimizer_type']}")
            print(f"     Trials: {r['num_trials']}")
            print(f"     ID: {r['optimizer_id']}")
            if r['notes']:
                print(f"     Notes: {r['notes']}")
            print()


def show_best_for_attack(db, attack_key):
    """Show the best result for a specific attack."""
    best = db.get_best_for_attack(attack_key)
    if not best:
        print(f"No results found for attack '{attack_key}'")
        return
    
    print(f"\n{'='*80}")
    print(f"BEST RESULT FOR: {attack_key}")
    print(f"{'='*80}\n")
    
    print(f"Optimizer ID:  {best['optimizer_id']}")
    print(f"Timestamp:     {best['timestamp']}")
    print(f"F1 Score:      {best['best_metric_f1']:.6f} (lower is better)")
    print(f"Optimizer:     {best['optimizer_type']}")
    print(f"Trials:        {best['num_trials']}")
    print(f"Config Base:   {best['config_base_path']}")
    if best['notes']:
        print(f"Notes:         {best['notes']}")
    
    print(f"\nBest Parameters ({len(best['best_parameters'])} total):")
    print(f"{'-'*80}")
    
    # Group parameters by category for readability
    params = best['best_parameters']
    categories = {}
    for key in sorted(params.keys()):
        parts = key.split('_')
        if len(parts) > 1:
            category = parts[0]  # e.g., "count", "windowS", "delayMs"
        else:
            category = "other"
        
        if category not in categories:
            categories[category] = {}
        categories[category][key] = params[key]
    
    for category, cat_params in sorted(categories.items()):
        print(f"\n  {category}:")
        for key, value in sorted(cat_params.items()):
            if isinstance(value, float):
                print(f"    {key:40s} = {value:.4f}")
            else:
                print(f"    {key:40s} = {value}")
    
    print()


def show_stats(db):
    """Show statistics about optimizer results."""
    results = db.get_all_results()
    if not results:
        print("No optimizer results found in database.")
        return
    
    print(f"\n{'='*80}")
    print(f"OPTIMIZER DATABASE STATISTICS")
    print(f"{'='*80}\n")
    
    print(f"Total Results: {len(results)}")
    
    # Group by optimizer type
    by_optimizer = {}
    for r in results:
        opt_type = r['optimizer_type']
        if opt_type not in by_optimizer:
            by_optimizer[opt_type] = []
        by_optimizer[opt_type].append(r)
    
    print(f"\nBy Optimizer Type:")
    for opt_type, opt_results in sorted(by_optimizer.items()):
        avg_f1 = sum(r['best_metric_f1'] for r in opt_results) / len(opt_results)
        best_f1 = min(r['best_metric_f1'] for r in opt_results)
        print(f"  {opt_type:25s}: {len(opt_results):3d} runs, avg F1={avg_f1:.6f}, best F1={best_f1:.6f}")
    
    # Group by attack
    by_attack = {}
    for r in results:
        key = r['attack_key'] if r['attack_key'] else r['attack_combination']
        if key not in by_attack:
            by_attack[key] = []
        by_attack[key].append(r)
    
    print(f"\nBy Attack:")
    for attack, attack_results in sorted(by_attack.items()):
        best = min(attack_results, key=lambda x: x['best_metric_f1'])
        print(f"  {attack:25s}: {len(attack_results):3d} runs, best F1={best['best_metric_f1']:.6f} ({best['optimizer_type']})")
    
    # Overall stats
    all_f1 = [r['best_metric_f1'] for r in results]
    print(f"\nOverall F1 Scores:")
    print(f"  Best (lowest):  {min(all_f1):.6f}")
    print(f"  Worst (highest): {max(all_f1):.6f}")
    print(f"  Average:        {sum(all_f1) / len(all_f1):.6f}")
    
    # Find the most successful attack (lowest F1)
    best_overall = min(results, key=lambda x: x['best_metric_f1'])
    attack_name = best_overall['attack_key'] if best_overall['attack_key'] else best_overall['attack_combination']
    print(f"\nMost Stealthy Attack: {attack_name}")
    print(f"  F1 Score: {best_overall['best_metric_f1']:.6f}")
    print(f"  Optimizer: {best_overall['optimizer_type']}")
    print(f"  Date: {best_overall['timestamp']}")
    
    print()


def main():
    parser = argparse.ArgumentParser(
        description='Query and analyze optimizer results database',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  List all results:
    python tools/query_optimizer_db.py list
  
  Show best for specific attack:
    python tools/query_optimizer_db.py best --attack randomReplay
  
  Show statistics:
    python tools/query_optimizer_db.py stats
'''
    )
    
    subparsers = parser.add_subparsers(dest='command', help='Command to execute')
    
    # List command
    subparsers.add_parser('list', help='List all optimizer results')
    
    # Best command
    best_parser = subparsers.add_parser('best', help='Show best result for an attack')
    best_parser.add_argument('--attack', required=True, help='Attack key (e.g., randomReplay)')
    
    # Stats command
    subparsers.add_parser('stats', help='Show database statistics')
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return
    
    # Initialize database
    db = OptimizerDatabasePython()
    
    # Execute command
    if args.command == 'list':
        list_all_results(db)
    elif args.command == 'best':
        show_best_for_attack(db, args.attack)
    elif args.command == 'stats':
        show_stats(db)


if __name__ == '__main__':
    main()
