import pandas as pd
import numpy as np
from scipy import stats

# Load the comprehensive results
df = pd.read_csv('target/comprehensive_evaluation/comprehensive_results.csv')

print("=" * 80)
print("COMPREHENSIVE RESULTS ANALYSIS")
print("=" * 80)

# 1. Analyze single attack performance (baseline)
print("\n1. BASELINE PERFORMANCE (Single Attacks Only):")
print("-" * 80)
single_attacks = df[~df['testAttack'].str.contains('+', regex=False)]
perf = single_attacks.groupby('testAttack')[['accuracy', 'recall', 'f1']].agg(['mean', 'min', 'max', 'std'])
print(perf.round(4).to_string())

# 2. Benign-only models vs attack-trained models
print("\n\n2. BENIGN-ONLY MODELS VS ATTACK-TRAINED MODELS:")
print("-" * 80)
benign_models = df[df['trainingPattern'] == 'benign_only']
attack_models = df[df['trainingPattern'] != 'benign_only']

print(f"\nBenign-only models performance (n={len(benign_models)}):")
print(f"  Mean accuracy: {benign_models['accuracy'].mean():.4f} ± {benign_models['accuracy'].std():.4f}")
print(f"  Mean recall:   {benign_models['recall'].mean():.4f} ± {benign_models['recall'].std():.4f}")
print(f"  Mean F1:       {benign_models['f1'].mean():.4f} ± {benign_models['f1'].std():.4f}")

print(f"\nAttack-trained models performance (n={len(attack_models)}):")
print(f"  Mean accuracy: {attack_models['accuracy'].mean():.4f} ± {attack_models['accuracy'].std():.4f}")
print(f"  Mean recall:   {attack_models['recall'].mean():.4f} ± {attack_models['recall'].std():.4f}")
print(f"  Mean F1:       {attack_models['f1'].mean():.4f} ± {attack_models['f1'].std():.4f}")

# Statistical significance
stat, p_value = stats.mannwhitneyu(benign_models['accuracy'], attack_models['accuracy'], alternative='two-sided')
print(f"\nMann-Whitney U test for accuracy: p-value = {p_value:.6f}")
print(f"  {'SIGNIFICANT' if p_value < 0.05 else 'NOT SIGNIFICANT'} at α=0.05")

# Breakdown by test attack type
print("\nBenign models on single attacks vs dual attacks:")
benign_single = benign_models[~benign_models['testAttack'].str.contains('+', regex=False)]
benign_dual = benign_models[benign_models['testAttack'].str.contains('+', regex=False)]
print(f"  Single attacks: {benign_single['accuracy'].mean():.4f} (n={len(benign_single)})")
print(f"  Dual attacks:   {benign_dual['accuracy'].mean():.4f} (n={len(benign_dual)})")

# 3. Individual vs Combined models on unrelated attacks
print("\n\n3. INDIVIDUAL VS COMBINED MODELS ON UNRELATED ATTACKS:")
print("-" * 80)

# Extract attack names from test datasets
def extract_attacks_from_test(test_name):
    """Extract attack names from test dataset name"""
    if '+' not in test_name:
        return {test_name.split('_simple')[0].split('_combined')[0]}
    # For dual attacks, parse the pattern
    parts = test_name.split('+')
    attack1 = parts[0]
    rest = parts[1].split('_simple')[0].split('_combined')[0]
    return {attack1, rest}

def categorize_attack_relationship(train_a1, train_a2, test_attacks):
    """Categorize relationship between training and test attacks"""
    train_attacks = {train_a1, train_a2}
    
    # Skip benign models
    if train_a1 == 'benign':
        return 'benign'
    
    # Count overlap
    overlap = len(train_attacks & test_attacks)
    
    if overlap == len(test_attacks) and len(test_attacks) == 2:
        return 'same'  # Both training attacks match both test attacks
    elif overlap == 1:
        return 'half-same'  # One attack matches
    elif overlap == 0:
        return 'unrelated'  # No matches
    elif overlap == 2 and len(test_attacks) == 1:
        return 'half-same'  # Testing single attack that appears in dual training
    else:
        return 'other'

# Add relationship category to dataframe
df['test_attacks'] = df['testAttack'].apply(extract_attacks_from_test)
df['relationship'] = df.apply(lambda row: categorize_attack_relationship(
    row['trainingAttack1'], row['trainingAttack2'], row['test_attacks']), axis=1)

# Filter for unrelated attacks only (exclude benign)
unrelated = df[(df['relationship'] == 'unrelated') & (df['trainingPattern'] != 'benign_only')]

# Compare simple vs combined on unrelated attacks
simple_unrelated = unrelated[unrelated['trainingPattern'] == 'simple']
combined_unrelated = unrelated[unrelated['trainingPattern'] == 'combined']

print(f"\nUnrelated attacks (training attacks don't match test attacks):")
print(f"\nSimple training pattern (n={len(simple_unrelated)}):")
print(f"  Mean accuracy: {simple_unrelated['accuracy'].mean():.4f} ± {simple_unrelated['accuracy'].std():.4f}")
print(f"  Mean recall:   {simple_unrelated['recall'].mean():.4f} ± {simple_unrelated['recall'].std():.4f}")
print(f"  Mean F1:       {simple_unrelated['f1'].mean():.4f} ± {simple_unrelated['f1'].std():.4f}")

print(f"\nCombined training pattern (n={len(combined_unrelated)}):")
print(f"  Mean accuracy: {combined_unrelated['accuracy'].mean():.4f} ± {combined_unrelated['accuracy'].std():.4f}")
print(f"  Mean recall:   {combined_unrelated['recall'].mean():.4f} ± {combined_unrelated['recall'].std():.4f}")
print(f"  Mean F1:       {combined_unrelated['f1'].mean():.4f} ± {combined_unrelated['f1'].std():.4f}")

if len(simple_unrelated) > 0 and len(combined_unrelated) > 0:
    stat, p_value = stats.mannwhitneyu(simple_unrelated['accuracy'], combined_unrelated['accuracy'], alternative='two-sided')
    print(f"\nMann-Whitney U test for accuracy: p-value = {p_value:.6f}")
    print(f"  {'SIGNIFICANT' if p_value < 0.05 else 'NOT SIGNIFICANT'} at α=0.05")
    
    diff = combined_unrelated['accuracy'].mean() - simple_unrelated['accuracy'].mean()
    print(f"  Combined is {abs(diff):.4f} {'better' if diff > 0 else 'worse'} than simple")

# 4. Simple vs Combined on Same, Half-Same, and Unrelated Attacks
print("\n\n4. SIMPLE VS COMBINED TRAINING PATTERNS BY ATTACK RELATIONSHIP:")
print("=" * 80)

relationships = ['same', 'half-same', 'unrelated']
for rel in relationships:
    print(f"\n{rel.upper()} ATTACKS:")
    print("-" * 80)
    
    rel_data = df[(df['relationship'] == rel) & (df['trainingPattern'] != 'benign_only')]
    simple_data = rel_data[rel_data['trainingPattern'] == 'simple']
    combined_data = rel_data[rel_data['trainingPattern'] == 'combined']
    
    if len(simple_data) == 0 or len(combined_data) == 0:
        print(f"  Insufficient data (simple: {len(simple_data)}, combined: {len(combined_data)})")
        continue
    
    print(f"\nSimple pattern (n={len(simple_data)}):")
    print(f"  Accuracy: {simple_data['accuracy'].mean():.4f} ± {simple_data['accuracy'].std():.4f}")
    print(f"  Recall:   {simple_data['recall'].mean():.4f} ± {simple_data['recall'].std():.4f}")
    print(f"  F1:       {simple_data['f1'].mean():.4f} ± {simple_data['f1'].std():.4f}")
    
    print(f"\nCombined pattern (n={len(combined_data)}):")
    print(f"  Accuracy: {combined_data['accuracy'].mean():.4f} ± {combined_data['accuracy'].std():.4f}")
    print(f"  Recall:   {combined_data['recall'].mean():.4f} ± {combined_data['recall'].std():.4f}")
    print(f"  F1:       {combined_data['f1'].mean():.4f} ± {combined_data['f1'].std():.4f}")
    
    # Statistical tests for all metrics
    for metric in ['accuracy', 'recall', 'f1']:
        stat, p_value = stats.mannwhitneyu(simple_data[metric], combined_data[metric], alternative='two-sided')
        diff = combined_data[metric].mean() - simple_data[metric].mean()
        sig_marker = '***' if p_value < 0.001 else '**' if p_value < 0.01 else '*' if p_value < 0.05 else 'ns'
        
        print(f"\n{metric.capitalize()} comparison:")
        print(f"  Difference: {diff:+.4f} (combined - simple)")
        print(f"  Mann-Whitney U p-value: {p_value:.6f} [{sig_marker}]")
        
        # Effect size (Cohen's d)
        pooled_std = np.sqrt((simple_data[metric].std()**2 + combined_data[metric].std()**2) / 2)
        if pooled_std > 0:
            cohens_d = diff / pooled_std
            print(f"  Effect size (Cohen's d): {cohens_d:.4f}")

# 5. Summary by classifier
print("\n\n5. PERFORMANCE BY CLASSIFIER:")
print("=" * 80)
non_benign = df[df['trainingPattern'] != 'benign_only']
for classifier in df['modelName'].unique():
    clf_data = non_benign[non_benign['modelName'] == classifier]
    print(f"\n{classifier}:")
    print(f"  Mean accuracy: {clf_data['accuracy'].mean():.4f} ± {clf_data['accuracy'].std():.4f}")
    print(f"  Mean recall:   {clf_data['recall'].mean():.4f} ± {clf_data['recall'].std():.4f}")
    print(f"  Mean F1:       {clf_data['f1'].mean():.4f} ± {clf_data['f1'].std():.4f}")

print("\n" + "=" * 80)
print("LEGEND: *** p<0.001, ** p<0.01, * p<0.05, ns = not significant")
print("=" * 80)
