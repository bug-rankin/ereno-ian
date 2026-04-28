import pandas as pd
import numpy as np
import random

def permutation_test(group1, group2, n_permutations=10000):
    """
    Perform permutation test to compare two groups.
    Returns p-value for two-tailed test.
    """
    group1 = np.array(group1)
    group2 = np.array(group2)
    
    # Observed difference in means
    observed_diff = abs(group1.mean() - group2.mean())
    
    # Combine all data
    combined = np.concatenate([group1, group2])
    n1 = len(group1)
    
    # Count how many permutations have difference >= observed
    extreme_count = 0
    
    for _ in range(n_permutations):
        # Shuffle and split
        np.random.shuffle(combined)
        perm_group1 = combined[:n1]
        perm_group2 = combined[n1:]
        
        # Calculate difference
        perm_diff = abs(perm_group1.mean() - perm_group2.mean())
        
        if perm_diff >= observed_diff:
            extreme_count += 1
    
    p_value = extreme_count / n_permutations
    return p_value

def cohens_d(group1, group2):
    """Calculate Cohen's d effect size"""
    group1 = np.array(group1)
    group2 = np.array(group2)
    
    pooled_std = np.sqrt((group1.std()**2 + group2.std()**2) / 2)
    if pooled_std == 0:
        return 0
    return (group1.mean() - group2.mean()) / pooled_std

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

# 2. Individual vs Combined models on unrelated attacks
print("\n\n2. INDIVIDUAL VS COMBINED MODELS ON UNRELATED ATTACKS:")
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

# Filter for unrelated attacks only
unrelated = df[df['relationship'] == 'unrelated']

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
    p_value = permutation_test(simple_unrelated['accuracy'], combined_unrelated['accuracy'])
    print(f"\nPermutation test for accuracy: p-value = {p_value:.4f}")
    print(f"  {'SIGNIFICANT' if p_value < 0.05 else 'NOT SIGNIFICANT'} at α=0.05")
    
    diff = combined_unrelated['accuracy'].mean() - simple_unrelated['accuracy'].mean()
    effect = cohens_d(combined_unrelated['accuracy'], simple_unrelated['accuracy'])
    print(f"  Combined is {abs(diff):.4f} {'better' if diff > 0 else 'worse'} than simple")
    print(f"  Effect size (Cohen's d): {effect:.4f}")

# 3. Simple vs Combined on Same, Half-Same, and Unrelated Attacks
print("\n\n3. SIMPLE VS COMBINED TRAINING PATTERNS BY ATTACK RELATIONSHIP:")
print("=" * 80)

relationships = ['same', 'half-same', 'unrelated']
for rel in relationships:
    print(f"\n{rel.upper()} ATTACKS:")
    print("-" * 80)
    
    rel_data = df[df['relationship'] == rel]
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
        p_value = permutation_test(simple_data[metric], combined_data[metric])
        diff = combined_data[metric].mean() - simple_data[metric].mean()
        sig_marker = '***' if p_value < 0.001 else '**' if p_value < 0.01 else '*' if p_value < 0.05 else 'ns'
        
        print(f"\n{metric.capitalize()} comparison:")
        print(f"  Difference: {diff:+.4f} (combined - simple)")
        print(f"  Permutation test p-value: {p_value:.4f} [{sig_marker}]")
        
        # Effect size (Cohen's d)
        effect = cohens_d(combined_data[metric], simple_data[metric])
        print(f"  Effect size (Cohen's d): {effect:.4f}")

# 4. Summary by classifier
print("\n\n4. PERFORMANCE BY CLASSIFIER:")
print("=" * 80)
for classifier in df['modelName'].unique():
    clf_data = df[df['modelName'] == classifier]
    print(f"\n{classifier}:")
    print(f"  Mean accuracy: {clf_data['accuracy'].mean():.4f} ± {clf_data['accuracy'].std():.4f}")
    print(f"  Mean recall:   {clf_data['recall'].mean():.4f} ± {clf_data['recall'].std():.4f}")
    print(f"  Mean F1:       {clf_data['f1'].mean():.4f} ± {clf_data['f1'].std():.4f}")

print("\n" + "=" * 80)
print("LEGEND: *** p<0.001, ** p<0.01, * p<0.05, ns = not significant")
print("=" * 80)
