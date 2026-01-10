#!/usr/bin/env python3
"""
ERENO Experiment Tracking Analysis Tool

This script provides advanced analysis and visualization of ERENO tracking databases.

Usage:
    python analyze_tracking.py [command] [options]

Commands:
    summary                 - Print overall summary
    compare-experiments     - Compare results across experiments
    plot-performance        - Plot model performance metrics
    export-report           - Generate HTML report
    find-best               - Find best performing models

Examples:
    python analyze_tracking.py summary
    python analyze_tracking.py compare-experiments EXP_001 EXP_002
    python analyze_tracking.py plot-performance --output plots/
    python analyze_tracking.py find-best --metric f1_score --top 10
"""

import pandas as pd
import argparse
import sys
from pathlib import Path
from datetime import datetime

# Default tracking directory
TRACKING_DIR = Path("target/tracking")


def load_databases(tracking_dir=TRACKING_DIR):
    """Load all tracking databases."""
    databases = {}
    
    try:
        databases['experiments'] = pd.read_csv(tracking_dir / "experiments.csv")
        databases['datasets'] = pd.read_csv(tracking_dir / "datasets.csv")
        databases['models'] = pd.read_csv(tracking_dir / "models.csv")
        databases['results'] = pd.read_csv(tracking_dir / "results.csv")
    except FileNotFoundError as e:
        print(f"Error: Could not find tracking database: {e}")
        print(f"Make sure tracking databases exist in: {tracking_dir}")
        sys.exit(1)
    
    return databases


def print_summary(dbs):
    """Print overall summary of tracking databases."""
    print("=" * 70)
    print("ERENO EXPERIMENT TRACKING SUMMARY")
    print("=" * 70)
    print()
    
    # Counts
    print("Database Statistics:")
    print(f"  Experiments: {len(dbs['experiments'])}")
    print(f"  Datasets:    {len(dbs['datasets'])}")
    print(f"  Models:      {len(dbs['models'])}")
    print(f"  Results:     {len(dbs['results'])}")
    print()
    
    # Experiment status breakdown
    if not dbs['experiments'].empty:
        print("Experiment Status:")
        status_counts = dbs['experiments']['status'].value_counts()
        for status, count in status_counts.items():
            print(f"  {status}: {count}")
        print()
    
    # Dataset types
    if not dbs['datasets'].empty:
        print("Dataset Types:")
        type_counts = dbs['datasets']['dataset_type'].value_counts()
        for dtype, count in type_counts.items():
            print(f"  {dtype}: {count}")
        print()
    
    # Classifiers used
    if not dbs['models'].empty:
        print("Classifiers Used:")
        classifier_counts = dbs['models']['classifier_name'].value_counts()
        for classifier, count in classifier_counts.items():
            print(f"  {classifier}: {count}")
        print()
    
    # Performance statistics
    if not dbs['results'].empty:
        print("Overall Performance Metrics:")
        print(f"  Mean Accuracy:  {dbs['results']['accuracy'].mean():.2f}%")
        print(f"  Max Accuracy:   {dbs['results']['accuracy'].max():.2f}%")
        print(f"  Mean Precision: {dbs['results']['precision'].mean():.4f}")
        print(f"  Mean Recall:    {dbs['results']['recall'].mean():.4f}")
        print(f"  Mean F1 Score:  {dbs['results']['f1_score'].mean():.4f}")
        print()


def compare_experiments(dbs, exp_ids):
    """Compare results across multiple experiments."""
    print("=" * 70)
    print("EXPERIMENT COMPARISON")
    print("=" * 70)
    print()
    
    for exp_id in exp_ids:
        exp = dbs['experiments'][dbs['experiments']['experiment_id'] == exp_id]
        
        if exp.empty:
            print(f"Warning: Experiment {exp_id} not found")
            continue
        
        print(f"Experiment: {exp_id}")
        print(f"  Type: {exp['experiment_type'].values[0]}")
        print(f"  Description: {exp['description'].values[0]}")
        print(f"  Status: {exp['status'].values[0]}")
        print()
        
        # Get results for this experiment
        results = dbs['results'][dbs['results']['experiment_id'] == exp_id]
        
        if results.empty:
            print("  No results found")
        else:
            print(f"  Results: {len(results)}")
            print(f"    Avg Accuracy:  {results['accuracy'].mean():.2f}%")
            print(f"    Avg Precision: {results['precision'].mean():.4f}")
            print(f"    Avg Recall:    {results['recall'].mean():.4f}")
            print(f"    Avg F1:        {results['f1_score'].mean():.4f}")
        
        print()


def find_best_models(dbs, metric='accuracy', top_n=10):
    """Find best performing models by metric."""
    print("=" * 70)
    print(f"TOP {top_n} MODELS BY {metric.upper()}")
    print("=" * 70)
    print()
    
    # Join results with models to get classifier names
    merged = dbs['results'].merge(
        dbs['models'][['model_id', 'classifier_name', 'model_path']], 
        on='model_id', 
        how='left'
    )
    
    # Sort by metric
    top_results = merged.nlargest(top_n, metric)
    
    print(f"{'Rank':<6} {'Classifier':<15} {'Metric':<10} {'Accuracy':<10} {'F1':<10} {'Model ID':<25}")
    print("-" * 80)
    
    for idx, (rank, row) in enumerate(top_results.iterrows(), 1):
        print(f"{rank:<6} {row['classifier_name']:<15} {row[metric]:<10.4f} "
              f"{row['accuracy']:<10.2f} {row['f1_score']:<10.4f} {row['model_id']:<25}")
    
    print()


def export_html_report(dbs, output_file='target/tracking/report.html'):
    """Export comprehensive HTML report."""
    print(f"Generating HTML report: {output_file}")
    
    html = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>ERENO Tracking Report</title>
        <style>
            body {{ font-family: Arial, sans-serif; margin: 20px; }}
            h1 {{ color: #333; }}
            h2 {{ color: #666; border-bottom: 2px solid #ddd; padding-bottom: 5px; }}
            table {{ border-collapse: collapse; width: 100%; margin: 20px 0; }}
            th {{ background-color: #4CAF50; color: white; padding: 12px; text-align: left; }}
            td {{ border: 1px solid #ddd; padding: 8px; }}
            tr:nth-child(even) {{ background-color: #f2f2f2; }}
            .metric {{ font-weight: bold; color: #4CAF50; }}
            .timestamp {{ color: #666; font-size: 0.9em; }}
        </style>
    </head>
    <body>
        <h1>ERENO Experiment Tracking Report</h1>
        <p class="timestamp">Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        
        <h2>Summary</h2>
        <ul>
            <li>Total Experiments: <span class="metric">{len(dbs['experiments'])}</span></li>
            <li>Total Datasets: <span class="metric">{len(dbs['datasets'])}</span></li>
            <li>Total Models: <span class="metric">{len(dbs['models'])}</span></li>
            <li>Total Results: <span class="metric">{len(dbs['results'])}</span></li>
        </ul>
        
        <h2>Experiments</h2>
        {dbs['experiments'].to_html(index=False, classes='table')}
        
        <h2>Top 10 Results by Accuracy</h2>
        {dbs['results'].nlargest(10, 'accuracy')[['result_id', 'model_id', 'accuracy', 'precision', 'recall', 'f1_score']].to_html(index=False, classes='table')}
        
        <h2>Recent Datasets</h2>
        {dbs['datasets'].tail(10)[['dataset_id', 'dataset_type', 'file_path', 'num_instances', 'timestamp']].to_html(index=False, classes='table')}
        
        <h2>Recent Models</h2>
        {dbs['models'].tail(10)[['model_id', 'classifier_name', 'model_path', 'training_time_ms', 'timestamp']].to_html(index=False, classes='table')}
    </body>
    </html>
    """
    
    Path(output_file).parent.mkdir(parents=True, exist_ok=True)
    with open(output_file, 'w') as f:
        f.write(html)
    
    print(f"Report generated: {output_file}")


def plot_performance(dbs, output_dir='target/tracking/plots'):
    """Plot model performance metrics."""
    try:
        import matplotlib.pyplot as plt
        import seaborn as sns
    except ImportError:
        print("Error: matplotlib and seaborn are required for plotting")
        print("Install with: pip install matplotlib seaborn")
        return
    
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Set style
    sns.set_style("whitegrid")
    
    # Merge results with models
    merged = dbs['results'].merge(
        dbs['models'][['model_id', 'classifier_name']], 
        on='model_id', 
        how='left'
    )
    
    # Plot 1: Performance by classifier
    plt.figure(figsize=(12, 6))
    metrics = ['accuracy', 'precision', 'recall', 'f1_score']
    classifier_perf = merged.groupby('classifier_name')[metrics].mean()
    classifier_perf.plot(kind='bar', ax=plt.gca())
    plt.title('Average Performance by Classifier')
    plt.ylabel('Score')
    plt.xlabel('Classifier')
    plt.xticks(rotation=45)
    plt.legend(title='Metrics')
    plt.tight_layout()
    plt.savefig(output_path / 'performance_by_classifier.png', dpi=300)
    print(f"Saved: {output_path / 'performance_by_classifier.png'}")
    
    # Plot 2: Accuracy distribution
    plt.figure(figsize=(10, 6))
    plt.hist(merged['accuracy'], bins=20, edgecolor='black')
    plt.title('Distribution of Model Accuracy')
    plt.xlabel('Accuracy (%)')
    plt.ylabel('Count')
    plt.axvline(merged['accuracy'].mean(), color='red', linestyle='--', 
                label=f'Mean: {merged["accuracy"].mean():.2f}%')
    plt.legend()
    plt.tight_layout()
    plt.savefig(output_path / 'accuracy_distribution.png', dpi=300)
    print(f"Saved: {output_path / 'accuracy_distribution.png'}")
    
    # Plot 3: Training time by classifier
    if not dbs['models'].empty:
        plt.figure(figsize=(10, 6))
        sns.boxplot(data=dbs['models'], x='classifier_name', y='training_time_ms')
        plt.title('Training Time by Classifier')
        plt.ylabel('Training Time (ms)')
        plt.xlabel('Classifier')
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.savefig(output_path / 'training_time_by_classifier.png', dpi=300)
        print(f"Saved: {output_path / 'training_time_by_classifier.png'}")
    
    print(f"\nAll plots saved to: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description='ERENO Experiment Tracking Analysis Tool',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    parser.add_argument('command', 
                       choices=['summary', 'compare-experiments', 'plot-performance', 
                               'export-report', 'find-best'],
                       help='Command to execute')
    
    parser.add_argument('args', nargs='*', help='Command arguments')
    parser.add_argument('--tracking-dir', default=str(TRACKING_DIR), 
                       help='Path to tracking directory')
    parser.add_argument('--metric', default='accuracy', 
                       help='Metric to use for find-best (default: accuracy)')
    parser.add_argument('--top', type=int, default=10, 
                       help='Number of top results to show (default: 10)')
    parser.add_argument('--output', help='Output file or directory')
    
    args = parser.parse_args()
    
    # Load databases
    tracking_dir = Path(args.tracking_dir)
    dbs = load_databases(tracking_dir)
    
    # Execute command
    if args.command == 'summary':
        print_summary(dbs)
    
    elif args.command == 'compare-experiments':
        if not args.args:
            print("Error: Please provide experiment IDs to compare")
            sys.exit(1)
        compare_experiments(dbs, args.args)
    
    elif args.command == 'plot-performance':
        output_dir = args.output or 'target/tracking/plots'
        plot_performance(dbs, output_dir)
    
    elif args.command == 'export-report':
        output_file = args.output or 'target/tracking/report.html'
        export_html_report(dbs, output_file)
    
    elif args.command == 'find-best':
        find_best_models(dbs, args.metric, args.top)


if __name__ == '__main__':
    main()
