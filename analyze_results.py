import pandas as pd

# Load the comprehensive results
df = pd.read_csv('target/comprehensive_evaluation/comprehensive_results.csv')

print("=" * 80)
print("COMPREHENSIVE RESULTS ANALYSIS - ATTACK IMPLEMENTATION ISSUES")
print("=" * 80)

# 1. Analyze single attack performance (baseline)
print("\n1. BASELINE PERFORMANCE (Single Attacks Only):")
print("-" * 80)
single_attacks = df[~df['testAttack'].str.contains('+', regex=False)]
perf = single_attacks.groupby('testAttack')[['accuracy', 'recall', 'f1']].agg(['mean', 'min', 'max', 'std'])
print(perf.round(4).to_string())

# 2. Find attacks with high variability (potential implementation issues)
print("\n\n2. ATTACKS WITH HIGH PERFORMANCE VARIABILITY:")
print("-" * 80)
print("(High std deviation suggests inconsistent detection)")
variability = single_attacks.groupby('testAttack')['accuracy'].std().sort_values(ascending=False)
print(variability.round(4).to_string())

# 3. Identify low-performing combinations
print("\n\n3. DUAL-ATTACK COMBINATIONS WITH LOW ACCURACY (<0.80):")
print("-" * 80)
low_acc = df[df['accuracy'] < 0.80]
print(f"Total low-accuracy records: {len(low_acc)}")
print("\nMost problematic test attack combinations:")
print(low_acc['testAttack'].value_counts().head(10).to_string())

# 4. Analyze uc02_inverse_replay specifically (appears problematic)
print("\n\n4. DETAILED ANALYSIS: uc02_inverse_replay")
print("-" * 80)
uc02 = df[df['testAttack'] == 'uc02_inverse_replay']
print(f"Total tests: {len(uc02)}")
print(f"Mean accuracy: {uc02['accuracy'].mean():.4f}")
print(f"Min accuracy: {uc02['accuracy'].min():.4f}")
print(f"Max accuracy: {uc02['accuracy'].max():.4f}")
print(f"Std deviation: {uc02['accuracy'].std():.4f}")
print("\nWorst performing models for uc02:")
print(uc02.nsmallest(5, 'accuracy')[['trainingAttack1', 'trainingAttack2', 'trainingPattern', 'modelName', 'accuracy', 'recall']].to_string())

# 5. Analyze uc04_masquerade_normal (lowest baseline performance)
print("\n\n5. DETAILED ANALYSIS: uc04_masquerade_normal")
print("-" * 80)
uc04 = df[df['testAttack'] == 'uc04_masquerade_normal']
print(f"Total tests: {len(uc04)}")
print(f"Mean accuracy: {uc04['accuracy'].mean():.4f}")
print(f"Min accuracy: {uc04['accuracy'].min():.4f}")
print(f"Max accuracy: {uc04['accuracy'].max():.4f}")
print(f"Std deviation: {uc04['accuracy'].std():.4f}")
print("\nPerformance breakdown by training attacks:")
uc04_grouped = uc04.groupby(['trainingAttack1', 'trainingAttack2'])['accuracy'].mean().sort_values()
print(uc04_grouped.head(10).to_string())

# 6. Check combined pattern performance on problematic combinations
print("\n\n6. PROBLEMATIC DUAL-ATTACK COMBINATION ANALYSIS:")
print("-" * 80)
print("\nuc02_inverse_replay + uc04_masquerade_normal combined:")
combo1 = df[df['testAttack'] == 'uc02_inverse_replay+uc04_masquerade_normal_combined']
print(f"Mean accuracy: {combo1['accuracy'].mean():.4f}")
print(f"Min accuracy: {combo1['accuracy'].min():.4f}")
print(f"Records with accuracy < 0.80: {len(combo1[combo1['accuracy'] < 0.80])}")

print("\nuc02_inverse_replay + uc07_flooding simple:")
combo2 = df[df['testAttack'] == 'uc02_inverse_replay+uc07_flooding_simple']
print(f"Mean accuracy: {combo2['accuracy'].mean():.4f}")
print(f"Min accuracy: {combo2['accuracy'].min():.4f}")
print(f"Records with accuracy < 0.80: {len(combo2[combo2['accuracy'] < 0.80])}")

# 7. Pattern analysis - simple vs combined
print("\n\n7. TRAINING PATTERN PERFORMANCE ON PROBLEM ATTACKS:")
print("-" * 80)
problem_attacks = ['uc02_inverse_replay', 'uc04_masquerade_normal', 
                   'uc02_inverse_replay+uc04_masquerade_normal_combined',
                   'uc02_inverse_replay+uc07_flooding_simple']
for attack in problem_attacks:
    attack_data = df[df['testAttack'] == attack]
    pattern_perf = attack_data.groupby('trainingPattern')['accuracy'].mean()
    print(f"\n{attack}:")
    print(pattern_perf.to_string())

# 8. Check if precision is always 1.0 (potential overfitting or dataset issue)
print("\n\n8. PRECISION ANALYSIS (checking for overfitting patterns):")
print("-" * 80)
perfect_precision = df[df['precision'] == 1.0]
print(f"Records with precision = 1.0: {len(perfect_precision)} / {len(df)} ({100*len(perfect_precision)/len(df):.2f}%)")
print("\nThis suggests potential issues with:")
print("- Class imbalance in test datasets")
print("- Attack messages not being properly generated")
print("- Model only learning to detect obvious patterns")

# 9. Recall analysis - where are we missing attacks?
print("\n\n9. LOW RECALL ANALYSIS (attacks not being detected):")
print("-" * 80)
low_recall = df[df['recall'] < 0.50]
print(f"Records with recall < 0.50: {len(low_recall)}")
if len(low_recall) > 0:
    print("\nTest attacks with lowest recall:")
    print(low_recall.groupby('testAttack')['recall'].mean().sort_values().head(10).to_string())
    print("\nWorst recall cases:")
    print(low_recall.nsmallest(10, 'recall')[['testAttack', 'trainingAttack1', 'trainingAttack2', 'trainingPattern', 'accuracy', 'recall']].to_string())

print("\n" + "=" * 80)
print("SUMMARY OF KEY FINDINGS:")
print("=" * 80)
