import pandas as pd
import numpy as np

# Load the comprehensive results
df = pd.read_csv('target/comprehensive_evaluation/comprehensive_results.csv')

print("=" * 100)
print("COMPREHENSIVE METRICS ANALYSIS")
print("=" * 100)

# ============================================================================
# PART 1: SIMPLE VS COMBINED TRAINING PATTERN COMPARISON
# ============================================================================
print("\n" + "=" * 100)
print("PART 1: SIMPLE VS COMBINED TRAINING PATTERN COMPARISON")
print("=" * 100)

print("\n1.1 OVERALL PERFORMANCE BY TRAINING PATTERN:")
print("-" * 100)
pattern_stats = df.groupby('trainingPattern')[['accuracy', 'recall', 'f1']].agg(['mean', 'std', 'min', 'max'])
print(pattern_stats.round(4).to_string())

# Calculate difference
simple_mean = df[df['trainingPattern'] == 'simple'][['accuracy', 'recall', 'f1']].mean()
combined_mean = df[df['trainingPattern'] == 'combined'][['accuracy', 'recall', 'f1']].mean()
difference = combined_mean - simple_mean
print(f"\n{'Metric':<15} {'Simple':<12} {'Combined':<12} {'Difference':<12} {'Winner'}")
print("-" * 65)
for metric in ['accuracy', 'recall', 'f1']:
    winner = "Combined" if difference[metric] > 0 else "Simple" if difference[metric] < 0 else "Tie"
    print(f"{metric:<15} {simple_mean[metric]:<12.4f} {combined_mean[metric]:<12.4f} {difference[metric]:+11.4f} {winner}")

print("\n1.2 PATTERN PERFORMANCE BY MODEL TYPE:")
print("-" * 100)
model_pattern = df.groupby(['modelName', 'trainingPattern'])[['accuracy', 'recall', 'f1']].mean().round(4)
print(model_pattern.to_string())

print("\n1.3 RECORDS WITH LOW ACCURACY (<0.80) BY PATTERN:")
print("-" * 100)
low_acc_pattern = df[df['accuracy'] < 0.80].groupby('trainingPattern').size()
print(low_acc_pattern.to_string())
print(f"\nSimple has {low_acc_pattern.get('simple', 0)} low-accuracy records")
print(f"Combined has {low_acc_pattern.get('combined', 0)} low-accuracy records")

print("\n1.4 TRAINING PATTERN PERFORMANCE ON SINGLE ATTACKS:")
print("-" * 100)
single_attacks = df[~df['testAttack'].str.contains('+', regex=False)]
pattern_single = single_attacks.groupby('trainingPattern')[['accuracy', 'recall', 'f1']].mean()
print(pattern_single.round(4).to_string())

print("\n1.5 TRAINING PATTERN PERFORMANCE ON DUAL ATTACKS:")
print("-" * 100)
dual_attacks = df[df['testAttack'].str.contains('+', regex=False)]
pattern_dual = dual_attacks.groupby('trainingPattern')[['accuracy', 'recall', 'f1']].mean()
print(pattern_dual.round(4).to_string())

# ============================================================================
# PART 2: ATTACK-BY-ATTACK DETAILED METRICS
# ============================================================================
print("\n\n" + "=" * 100)
print("PART 2: ATTACK-BY-ATTACK DETAILED METRICS")
print("=" * 100)

# Get list of unique single attacks
single_attack_list = ['uc01_random_replay', 'uc02_inverse_replay', 'uc03_masquerade_fault',
                      'uc04_masquerade_normal', 'uc05_injection', 'uc06_high_stnum_injection',
                      'uc07_flooding', 'uc08_grayhole']

print("\n2.1 SINGLE ATTACK COMPREHENSIVE STATISTICS:")
print("-" * 100)
print(f"{'Attack':<30} {'Avg Acc':<10} {'Min Acc':<10} {'Max Acc':<10} {'Std':<10} {'Avg Recall':<12} {'Min Recall':<12}")
print("-" * 100)
for attack in single_attack_list:
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        print(f"{attack:<30} {attack_data['accuracy'].mean():<10.4f} {attack_data['accuracy'].min():<10.4f} "
              f"{attack_data['accuracy'].max():<10.4f} {attack_data['accuracy'].std():<10.4f} "
              f"{attack_data['recall'].mean():<12.4f} {attack_data['recall'].min():<12.4f}")

print("\n2.2 ATTACK DETECTABILITY RANKING (by average accuracy):")
print("-" * 100)
single_data = df[df['testAttack'].isin(single_attack_list)]
attack_ranking = single_data.groupby('testAttack')[['accuracy', 'recall', 'f1']].mean().sort_values('accuracy', ascending=False)
print(attack_ranking.round(4).to_string())

print("\n2.3 ATTACK CONSISTENCY RANKING (by std deviation - lower is better):")
print("-" * 100)
attack_consistency = single_data.groupby('testAttack')['accuracy'].std().sort_values()
print(attack_consistency.round(4).to_string())

print("\n2.4 PROBLEMATIC ATTACKS (High Variability + Low Performance):")
print("-" * 100)
attack_stats = single_data.groupby('testAttack').agg({
    'accuracy': ['mean', 'std', 'min'],
    'recall': ['mean', 'min']
}).round(4)
attack_stats.columns = ['acc_mean', 'acc_std', 'acc_min', 'recall_mean', 'recall_min']
attack_stats['problem_score'] = (1 - attack_stats['acc_mean']) + attack_stats['acc_std']
attack_stats = attack_stats.sort_values('problem_score', ascending=False)
print(attack_stats.to_string())

print("\n2.5 EACH ATTACK: SIMPLE VS COMBINED TRAINING PERFORMANCE:")
print("-" * 100)
print(f"{'Attack':<30} {'Simple Acc':<12} {'Combined Acc':<12} {'Diff':<10} {'Better Pattern'}")
print("-" * 100)
for attack in single_attack_list:
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        simple_acc = attack_data[attack_data['trainingPattern'] == 'simple']['accuracy'].mean()
        combined_acc = attack_data[attack_data['trainingPattern'] == 'combined']['accuracy'].mean()
        diff = combined_acc - simple_acc
        better = "Combined" if diff > 0.001 else "Simple" if diff < -0.001 else "Similar"
        print(f"{attack:<30} {simple_acc:<12.4f} {combined_acc:<12.4f} {diff:+9.4f} {better}")

print("\n2.6 ATTACK PERFORMANCE BY MODEL TYPE:")
print("-" * 100)
for attack in single_attack_list:
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        model_perf = attack_data.groupby('modelName')[['accuracy', 'recall']].mean()
        print(f"\n{attack}:")
        print(model_perf.round(4).to_string())

print("\n2.7 WORST PERFORMING SCENARIOS PER ATTACK:")
print("-" * 100)
for attack in single_attack_list:
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        worst = attack_data.nsmallest(3, 'accuracy')
        print(f"\n{attack} - Worst 3 scenarios:")
        print(worst[['trainingAttack1', 'trainingAttack2', 'trainingPattern', 'modelName', 'accuracy', 'recall']].to_string())

# ============================================================================
# PART 3: DUAL ATTACK ANALYSIS
# ============================================================================
print("\n\n" + "=" * 100)
print("PART 3: DUAL ATTACK PATTERN ANALYSIS (Simple vs Combined Test Attacks)")
print("=" * 100)

# Extract dual attacks and their patterns
dual_attacks = df[df['testAttack'].str.contains('+', regex=False)].copy()
dual_attacks['attack_pair'] = dual_attacks['testAttack'].str.replace('_simple', '').str.replace('_combined', '')
dual_attacks['test_pattern'] = dual_attacks['testAttack'].apply(lambda x: 'simple' if 'simple' in x else 'combined')

print("\n3.1 DUAL ATTACK PERFORMANCE: SIMPLE VS COMBINED TEST PATTERNS:")
print("-" * 100)
dual_pattern_perf = dual_attacks.groupby('test_pattern')[['accuracy', 'recall', 'f1']].agg(['mean', 'std', 'min', 'max'])
print(dual_pattern_perf.round(4).to_string())

print("\n3.2 TOP 10 BEST PERFORMING DUAL ATTACK COMBINATIONS:")
print("-" * 100)
best_dual = dual_attacks.groupby('attack_pair')[['accuracy', 'recall', 'f1']].mean().nlargest(10, 'accuracy')
print(best_dual.round(4).to_string())

print("\n3.3 TOP 10 WORST PERFORMING DUAL ATTACK COMBINATIONS:")
print("-" * 100)
worst_dual = dual_attacks.groupby('attack_pair')[['accuracy', 'recall', 'f1']].mean().nsmallest(10, 'accuracy')
print(worst_dual.round(4).to_string())

print("\n3.4 DUAL ATTACKS WITH HIGHEST VARIABILITY:")
print("-" * 100)
dual_variability = dual_attacks.groupby('attack_pair')['accuracy'].std().nlargest(10)
print(dual_variability.round(4).to_string())

# ============================================================================
# PART 4: CROSS-ATTACK GENERALIZATION
# ============================================================================
print("\n\n" + "=" * 100)
print("PART 4: CROSS-ATTACK GENERALIZATION ANALYSIS")
print("=" * 100)

print("\n4.1 HOW WELL DOES EACH TRAINING PAIR GENERALIZE TO OTHER ATTACKS?")
print("-" * 100)
training_pairs = df.groupby(['trainingAttack1', 'trainingAttack2'])
print(f"{'Training Pair':<60} {'Avg Acc':<10} {'Min Acc':<10} {'Std':<10}")
print("-" * 100)
for (t1, t2), group in training_pairs:
    pair_name = f"{t1} + {t2}"
    avg_acc = group['accuracy'].mean()
    min_acc = group['accuracy'].min()
    std_acc = group['accuracy'].std()
    print(f"{pair_name:<60} {avg_acc:<10.4f} {min_acc:<10.4f} {std_acc:<10.4f}")

print("\n4.2 ATTACK PAIRS THAT GENERALIZE BEST (highest min accuracy):")
print("-" * 100)
generalization = df.groupby(['trainingAttack1', 'trainingAttack2']).agg({
    'accuracy': ['mean', 'min', 'std']
}).round(4)
generalization.columns = ['mean_acc', 'min_acc', 'std_acc']
generalization = generalization.sort_values('min_acc', ascending=False)
print(generalization.head(10).to_string())

print("\n4.3 ATTACK PAIRS THAT GENERALIZE WORST:")
print("-" * 100)
print(generalization.tail(10).to_string())

# ============================================================================
# PART 5: SUMMARY STATISTICS
# ============================================================================
print("\n\n" + "=" * 100)
print("PART 5: EXECUTIVE SUMMARY")
print("=" * 100)

print("\nKEY FINDINGS:")
print("-" * 100)

# Best and worst attacks
best_attack = attack_ranking.index[0]
worst_attack = attack_ranking.index[-1]
print(f"✓ Best Performing Attack: {best_attack} ({attack_ranking.loc[best_attack, 'accuracy']:.4f} avg accuracy)")
print(f"✗ Worst Performing Attack: {worst_attack} ({attack_ranking.loc[worst_attack, 'accuracy']:.4f} avg accuracy)")

# Pattern comparison
if combined_mean['accuracy'] > simple_mean['accuracy']:
    print(f"✓ Combined training pattern performs {difference['accuracy']:.4f} better than simple")
else:
    print(f"✗ Simple training pattern performs {abs(difference['accuracy']):.4f} better than combined")

# Model comparison
model_perf = df.groupby('modelName')['accuracy'].mean()
best_model = model_perf.idxmax()
print(f"✓ Best Model: {best_model} ({model_perf[best_model]:.4f} avg accuracy)")

# Problem areas
print(f"\n✗ {len(df[df['accuracy'] < 0.80])} records ({100*len(df[df['accuracy'] < 0.80])/len(df):.2f}%) have accuracy below 80%")
print(f"✗ {len(df[df['recall'] < 0.50])} records have recall below 50% (attacks barely detected)")
print(f"✗ {len(df[df['precision'] == 1.0])} records ({100*len(df[df['precision'] == 1.0])/len(df):.2f}%) have perfect precision = 1.0")
print("   This indicates potential issues with attack implementation or class imbalance")

# Most problematic combinations
print("\n✗ Most Problematic Attack Combinations (lowest average accuracy):")
problem_combos = df.groupby('testAttack')['accuracy'].mean().nsmallest(5)
for attack, acc in problem_combos.items():
    print(f"   - {attack}: {acc:.4f}")

print("\n" + "=" * 100)
