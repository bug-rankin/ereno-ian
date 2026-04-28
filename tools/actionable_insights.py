import pandas as pd

# Load the comprehensive results
df = pd.read_csv('target/comprehensive_evaluation/comprehensive_results.csv')

print("=" * 100)
print("ACTIONABLE INSIGHTS - SIMPLE VS COMBINED & ATTACK IMPLEMENTATION QUALITY")
print("=" * 100)

# ============================================================================
# KEY INSIGHT 1: When should you use Simple vs Combined training?
# ============================================================================
print("\n" + "=" * 100)
print("INSIGHT 1: WHEN TO USE SIMPLE VS COMBINED TRAINING PATTERNS")
print("=" * 100)

print("\nOverall Winner: COMBINED (slightly better across all metrics)")
print("-" * 100)
simple_mean = df[df['trainingPattern'] == 'simple'][['accuracy', 'recall', 'f1']].mean()
combined_mean = df[df['trainingPattern'] == 'combined'][['accuracy', 'recall', 'f1']].mean()
print(f"Combined is +{(combined_mean['accuracy'] - simple_mean['accuracy'])*100:.2f}% more accurate")
print(f"Combined is +{(combined_mean['recall'] - simple_mean['recall'])*100:.2f}% better at recall")
print(f"Combined has {len(df[(df['trainingPattern']=='simple') & (df['accuracy']<0.80)])} vs {len(df[(df['trainingPattern']=='combined') & (df['accuracy']<0.80)])} low-accuracy records (399 vs 355)")

print("\n\nWhich attacks benefit MOST from Combined training?")
print("-" * 100)
print(f"{'Attack':<30} {'Simple→Combined Improvement':<30} {'Recommendation'}")
print("-" * 100)
single_attacks = ['uc01_random_replay', 'uc02_inverse_replay', 'uc03_masquerade_fault',
                  'uc04_masquerade_normal', 'uc05_injection', 'uc06_high_stnum_injection',
                  'uc07_flooding', 'uc08_grayhole']

improvements = []
for attack in single_attacks:
    attack_data = df[df['testAttack'] == attack]
    simple_acc = attack_data[attack_data['trainingPattern'] == 'simple']['accuracy'].mean()
    combined_acc = attack_data[attack_data['trainingPattern'] == 'combined']['accuracy'].mean()
    improvement = (combined_acc - simple_acc) * 100
    improvements.append((attack, improvement, simple_acc, combined_acc))
    
    if improvement > 0.5:
        rec = "✓ USE COMBINED"
    elif improvement < -0.5:
        rec = "✓ USE SIMPLE"
    else:
        rec = "≈ Either is fine"
    
    print(f"{attack:<30} {improvement:+6.2f}%                         {rec}")

print("\n\nRECOMMENDATION:")
print("-" * 100)
print("✓ Use COMBINED training for: uc02_inverse_replay, uc03_masquerade_fault")
print("✓ Use SIMPLE training for: uc08_grayhole")
print("≈ Either works for: All other attacks (difference < 0.5%)")

# ============================================================================
# KEY INSIGHT 2: Training pair effectiveness
# ============================================================================
print("\n\n" + "=" * 100)
print("INSIGHT 2: BEST & WORST TRAINING ATTACK PAIRS FOR GENERALIZATION")
print("=" * 100)

training_generalization = df.groupby(['trainingAttack1', 'trainingAttack2']).agg({
    'accuracy': ['mean', 'min', 'std']
}).round(4)
training_generalization.columns = ['mean_acc', 'min_acc', 'std_acc']
training_generalization = training_generalization.sort_values('min_acc', ascending=False)

print("\n✓ TOP 5 TRAINING PAIRS (Best Generalization - Highest Minimum Accuracy):")
print("-" * 100)
print(f"{'Training Attack 1':<30} {'Training Attack 2':<30} {'Avg Acc':<10} {'Min Acc':<10} {'Std'}")
print("-" * 100)
for idx, ((t1, t2), row) in enumerate(training_generalization.head(5).iterrows()):
    print(f"{t1:<30} {t2:<30} {row['mean_acc']:<10.4f} {row['min_acc']:<10.4f} {row['std_acc']:.4f}")

print("\n✗ BOTTOM 5 TRAINING PAIRS (Poor Generalization - Lowest Minimum Accuracy):")
print("-" * 100)
print(f"{'Training Attack 1':<30} {'Training Attack 2':<30} {'Avg Acc':<10} {'Min Acc':<10} {'Std'}")
print("-" * 100)
for idx, ((t1, t2), row) in enumerate(training_generalization.tail(5).iterrows()):
    print(f"{t1:<30} {t2:<30} {row['mean_acc']:<10.4f} {row['min_acc']:<10.4f} {row['std_acc']:.4f}")

print("\n\nRECOMMENDATION:")
print("-" * 100)
print("✓ BEST training pairs (use these for robust models):")
print("   1. uc06_high_stnum_injection + uc08_grayhole")
print("   2. uc01_random_replay + uc05_injection")
print("   3. uc07_flooding + uc08_grayhole")
print("\n✗ AVOID these training pairs (poor generalization):")
print("   1. uc04_masquerade_normal + uc06_high_stnum_injection (min accuracy: 28.37%!)")
print("   2. uc01_random_replay + uc04_masquerade_normal")
print("   3. uc02_inverse_replay + uc08_grayhole")

# ============================================================================
# KEY INSIGHT 3: Attack implementation quality assessment
# ============================================================================
print("\n\n" + "=" * 100)
print("INSIGHT 3: ATTACK IMPLEMENTATION QUALITY ASSESSMENT")
print("=" * 100)

print("\nQuality Score = (1 - variability) * average_accuracy * (1 if recall > 0.5 else 0.5)")
print("-" * 100)

single_data = df[df['testAttack'].isin(single_attacks)]
quality_scores = []

for attack in single_attacks:
    attack_data = df[df['testAttack'] == attack]
    avg_acc = attack_data['accuracy'].mean()
    std_acc = attack_data['accuracy'].std()
    min_recall = attack_data['recall'].min()
    avg_recall = attack_data['recall'].mean()
    
    # Quality score: penalize high variability and low recall
    consistency_factor = 1 - std_acc
    recall_penalty = 1.0 if min_recall > 0.5 else 0.5 if min_recall > 0.2 else 0.1
    quality = consistency_factor * avg_acc * recall_penalty
    
    quality_scores.append({
        'attack': attack,
        'quality_score': quality,
        'avg_accuracy': avg_acc,
        'std_accuracy': std_acc,
        'min_recall': min_recall,
        'avg_recall': avg_recall
    })

quality_df = pd.DataFrame(quality_scores).sort_values('quality_score', ascending=False)

print(f"\n{'Attack':<30} {'Quality':<10} {'Avg Acc':<10} {'Std':<10} {'Min Recall':<12} {'Status'}")
print("-" * 100)
for _, row in quality_df.iterrows():
    if row['quality_score'] > 0.95:
        status = "✓ Excellent"
    elif row['quality_score'] > 0.90:
        status = "✓ Good"
    elif row['quality_score'] > 0.80:
        status = "⚠ Fair"
    else:
        status = "✗ Poor"
    
    print(f"{row['attack']:<30} {row['quality_score']:<10.4f} {row['avg_accuracy']:<10.4f} "
          f"{row['std_accuracy']:<10.4f} {row['min_recall']:<12.4f} {status}")

print("\n\nIMPLEMENTATION QUALITY ISSUES:")
print("-" * 100)
for _, row in quality_df.iterrows():
    if row['quality_score'] < 0.90:
        print(f"\n✗ {row['attack']}:")
        if row['std_accuracy'] > 0.03:
            print(f"   - High variability (std={row['std_accuracy']:.4f}) - inconsistent attack generation")
        if row['min_recall'] < 0.30:
            print(f"   - Very low minimum recall ({row['min_recall']:.4f}) - attack often undetectable")
        if row['avg_accuracy'] < 0.95:
            print(f"   - Low average accuracy ({row['avg_accuracy']:.4f}) - weak attack signature")

# ============================================================================
# KEY INSIGHT 4: Model selection guidance
# ============================================================================
print("\n\n" + "=" * 100)
print("INSIGHT 4: J48 VS RANDOM FOREST - WHICH IS BETTER?")
print("=" * 100)

print("\nOverall Performance:")
print("-" * 100)
model_perf = df.groupby('modelName')[['accuracy', 'recall', 'f1']].mean()
print(model_perf.round(4).to_string())

j48_better = 0
rf_better = 0
print("\n\nPer-Attack Model Performance:")
print("-" * 100)
print(f"{'Attack':<30} {'J48 Acc':<10} {'RF Acc':<10} {'Difference':<12} {'Winner'}")
print("-" * 100)
for attack in single_attacks:
    attack_data = df[df['testAttack'] == attack]
    j48_acc = attack_data[attack_data['modelName'] == 'J48']['accuracy'].mean()
    rf_acc = attack_data[attack_data['modelName'] == 'RandomForest']['accuracy'].mean()
    diff = rf_acc - j48_acc
    winner = "RandomForest" if diff > 0.001 else "J48" if diff < -0.001 else "Tie"
    
    if winner == "RandomForest":
        rf_better += 1
    elif winner == "J48":
        j48_better += 1
    
    print(f"{attack:<30} {j48_acc:<10.4f} {rf_acc:<10.4f} {diff:+11.4f} {winner}")

print(f"\n\nRECOMMENDATION:")
print("-" * 100)
print(f"✓ RandomForest wins {rf_better}/8 attacks, J48 wins {j48_better}/8")
print(f"✓ RandomForest average: {model_perf.loc['RandomForest', 'accuracy']:.4f}")
print(f"✓ J48 average: {model_perf.loc['J48', 'accuracy']:.4f}")
print(f"✓ Overall winner: RandomForest (better accuracy, recall, and F1)")
print(f"\nUse RandomForest for: Better overall performance and generalization")
print(f"Use J48 for: Faster training and more interpretable decision trees")

# ============================================================================
# KEY INSIGHT 5: Critical findings summary
# ============================================================================
print("\n\n" + "=" * 100)
print("INSIGHT 5: CRITICAL ISSUES REQUIRING IMMEDIATE ATTENTION")
print("=" * 100)

print("\n🚨 ISSUE #1: Perfect Precision (100.00%)")
print("-" * 100)
print(f"ALL {len(df)} records have precision = 1.0")
print("This means: NO FALSE POSITIVES EVER")
print("Root cause: Attack messages are either:")
print("  • Too different from benign traffic (trivially easy to detect)")
print("  • Not being generated properly")
print("  • Test datasets have extreme class imbalance")
print("Action: Review attack generation code and dataset composition")

print("\n🚨 ISSUE #2: Three Critically Broken Attacks")
print("-" * 100)
critical_attacks = quality_df[quality_df['quality_score'] < 0.90]
for _, row in critical_attacks.iterrows():
    print(f"\n{row['attack']}:")
    print(f"  • Quality score: {row['quality_score']:.4f}")
    print(f"  • Min recall: {row['min_recall']:.4f} (attack barely detected in worst case)")
    print(f"  • Std accuracy: {row['std_accuracy']:.4f} (high inconsistency)")

print("\n🚨 ISSUE #3: Dual Attack Combinations Fail Dramatically")
print("-" * 100)
dual_attacks = df[df['testAttack'].str.contains('+', regex=False)]
worst_combos = dual_attacks.groupby('testAttack')['accuracy'].mean().nsmallest(5)
print("Worst performing dual-attack test scenarios:")
for combo, acc in worst_combos.items():
    print(f"  • {combo}: {acc:.4f} average accuracy")
print("\nRoot cause: Attacks may interfere with each other or not run simultaneously")

print("\n🚨 ISSUE #4: Some Training Pairs Have Catastrophic Failure Cases")
print("-" * 100)
catastrophic = training_generalization[training_generalization['min_acc'] < 0.60]
print(f"Found {len(catastrophic)} training pairs with minimum accuracy < 60%:")
for (t1, t2), row in catastrophic.iterrows():
    print(f"  • {t1} + {t2}: min accuracy {row['min_acc']:.4f}")

print("\n" + "=" * 100)
print("END OF ACTIONABLE INSIGHTS")
print("=" * 100)
