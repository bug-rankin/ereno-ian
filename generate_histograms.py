import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Load the comprehensive results
df = pd.read_csv('target/comprehensive_evaluation/comprehensive_results.csv')

print("Generating histograms...")

# ============================================================================
# 1. HISTOGRAM: Simple vs Combined Training Performance Difference
# ============================================================================

# Calculate accuracy difference for each test record
simple_data = df[df['trainingPattern'] == 'simple'].copy()
combined_data = df[df['trainingPattern'] == 'combined'].copy()

# Create a merge key to match corresponding simple/combined tests
simple_data['merge_key'] = (simple_data['trainingAttack1'] + '_' + 
                             simple_data['trainingAttack2'] + '_' + 
                             simple_data['testAttack'] + '_' + 
                             simple_data['modelName'])
combined_data['merge_key'] = (combined_data['trainingAttack1'] + '_' + 
                               combined_data['trainingAttack2'] + '_' + 
                               combined_data['testAttack'] + '_' + 
                               combined_data['modelName'])

# Merge to get paired comparisons
comparison = pd.merge(
    simple_data[['merge_key', 'accuracy', 'recall', 'f1']],
    combined_data[['merge_key', 'accuracy', 'recall', 'f1']],
    on='merge_key',
    suffixes=('_simple', '_combined')
)

# Calculate differences (combined - simple)
comparison['acc_diff'] = comparison['accuracy_combined'] - comparison['accuracy_simple']
comparison['recall_diff'] = comparison['recall_combined'] - comparison['recall_simple']
comparison['f1_diff'] = comparison['f1_combined'] - comparison['f1_simple']

# Create the comparison histogram
fig, axes = plt.subplots(2, 2, figsize=(15, 12))
fig.suptitle('Simple vs Combined Training Pattern Comparison', fontsize=16, fontweight='bold')

# Accuracy difference
axes[0, 0].hist(comparison['acc_diff'], bins=50, color='steelblue', edgecolor='black', alpha=0.7)
axes[0, 0].axvline(x=0, color='red', linestyle='--', linewidth=2, label='No difference')
axes[0, 0].axvline(x=comparison['acc_diff'].mean(), color='green', linestyle='-', linewidth=2, 
                    label=f'Mean: {comparison["acc_diff"].mean():.4f}')
axes[0, 0].set_xlabel('Accuracy Difference (Combined - Simple)', fontsize=11)
axes[0, 0].set_ylabel('Frequency', fontsize=11)
axes[0, 0].set_title('Accuracy: Combined vs Simple', fontsize=12, fontweight='bold')
axes[0, 0].legend()
axes[0, 0].grid(True, alpha=0.3)

# Add statistics text
stats_text = f'Mean: {comparison["acc_diff"].mean():.4f}\n'
stats_text += f'Median: {comparison["acc_diff"].median():.4f}\n'
stats_text += f'Std: {comparison["acc_diff"].std():.4f}\n'
stats_text += f'Combined Better: {(comparison["acc_diff"] > 0).sum()} ({100*(comparison["acc_diff"] > 0).sum()/len(comparison):.1f}%)'
axes[0, 0].text(0.02, 0.98, stats_text, transform=axes[0, 0].transAxes,
                fontsize=9, verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

# Recall difference
axes[0, 1].hist(comparison['recall_diff'], bins=50, color='coral', edgecolor='black', alpha=0.7)
axes[0, 1].axvline(x=0, color='red', linestyle='--', linewidth=2, label='No difference')
axes[0, 1].axvline(x=comparison['recall_diff'].mean(), color='green', linestyle='-', linewidth=2,
                    label=f'Mean: {comparison["recall_diff"].mean():.4f}')
axes[0, 1].set_xlabel('Recall Difference (Combined - Simple)', fontsize=11)
axes[0, 1].set_ylabel('Frequency', fontsize=11)
axes[0, 1].set_title('Recall: Combined vs Simple', fontsize=12, fontweight='bold')
axes[0, 1].legend()
axes[0, 1].grid(True, alpha=0.3)

stats_text = f'Mean: {comparison["recall_diff"].mean():.4f}\n'
stats_text += f'Median: {comparison["recall_diff"].median():.4f}\n'
stats_text += f'Std: {comparison["recall_diff"].std():.4f}\n'
stats_text += f'Combined Better: {(comparison["recall_diff"] > 0).sum()} ({100*(comparison["recall_diff"] > 0).sum()/len(comparison):.1f}%)'
axes[0, 1].text(0.02, 0.98, stats_text, transform=axes[0, 1].transAxes,
                fontsize=9, verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

# F1 difference
axes[1, 0].hist(comparison['f1_diff'], bins=50, color='mediumseagreen', edgecolor='black', alpha=0.7)
axes[1, 0].axvline(x=0, color='red', linestyle='--', linewidth=2, label='No difference')
axes[1, 0].axvline(x=comparison['f1_diff'].mean(), color='green', linestyle='-', linewidth=2,
                    label=f'Mean: {comparison["f1_diff"].mean():.4f}')
axes[1, 0].set_xlabel('F1 Difference (Combined - Simple)', fontsize=11)
axes[1, 0].set_ylabel('Frequency', fontsize=11)
axes[1, 0].set_title('F1 Score: Combined vs Simple', fontsize=12, fontweight='bold')
axes[1, 0].legend()
axes[1, 0].grid(True, alpha=0.3)

stats_text = f'Mean: {comparison["f1_diff"].mean():.4f}\n'
stats_text += f'Median: {comparison["f1_diff"].median():.4f}\n'
stats_text += f'Std: {comparison["f1_diff"].std():.4f}\n'
stats_text += f'Combined Better: {(comparison["f1_diff"] > 0).sum()} ({100*(comparison["f1_diff"] > 0).sum()/len(comparison):.1f}%)'
axes[1, 0].text(0.02, 0.98, stats_text, transform=axes[1, 0].transAxes,
                fontsize=9, verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

# Summary scatter plot
axes[1, 1].scatter(comparison['accuracy_simple'], comparison['accuracy_combined'], 
                   alpha=0.3, s=20, c='purple')
axes[1, 1].plot([0, 1], [0, 1], 'r--', linewidth=2, label='Equal performance')
axes[1, 1].set_xlabel('Simple Training Accuracy', fontsize=11)
axes[1, 1].set_ylabel('Combined Training Accuracy', fontsize=11)
axes[1, 1].set_title('Accuracy: Simple vs Combined (Scatter)', fontsize=12, fontweight='bold')
axes[1, 1].legend()
axes[1, 1].grid(True, alpha=0.3)
axes[1, 1].set_xlim([0.2, 1.05])
axes[1, 1].set_ylim([0.2, 1.05])

# Add text showing which quadrant has more points
above_line = (comparison['accuracy_combined'] > comparison['accuracy_simple']).sum()
below_line = (comparison['accuracy_combined'] < comparison['accuracy_simple']).sum()
equal_line = (comparison['accuracy_combined'] == comparison['accuracy_simple']).sum()
axes[1, 1].text(0.05, 0.95, f'Above line (Combined better): {above_line}\n'
                            f'Below line (Simple better): {below_line}\n'
                            f'On line (Equal): {equal_line}',
                transform=axes[1, 1].transAxes, fontsize=9, verticalalignment='top',
                bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

plt.tight_layout()
plt.savefig('target/comprehensive_evaluation/simple_vs_combined_histogram.png', dpi=300, bbox_inches='tight')
print("✓ Saved: target/comprehensive_evaluation/simple_vs_combined_histogram.png")
plt.close()

# ============================================================================
# 2. INDIVIDUAL ATTACK HISTOGRAMS
# ============================================================================

single_attacks = ['uc01_random_replay', 'uc02_inverse_replay', 'uc03_masquerade_fault',
                  'uc04_masquerade_normal', 'uc05_injection', 'uc06_high_stnum_injection',
                  'uc07_flooding', 'uc08_grayhole']

# Create a large figure with subplots for all attacks
fig, axes = plt.subplots(4, 2, figsize=(16, 20))
fig.suptitle('Attack Performance Distribution (Accuracy)', fontsize=18, fontweight='bold')

for idx, attack in enumerate(single_attacks):
    row = idx // 2
    col = idx % 2
    ax = axes[row, col]
    
    attack_data = df[df['testAttack'] == attack]
    
    if len(attack_data) == 0:
        ax.text(0.5, 0.5, 'No data', ha='center', va='center')
        ax.set_title(attack.replace('_', ' ').title())
        continue
    
    # Create histogram
    n, bins, patches = ax.hist(attack_data['accuracy'], bins=30, color='steelblue', 
                                edgecolor='black', alpha=0.7)
    
    # Color code the bars based on accuracy ranges
    for i, patch in enumerate(patches):
        if bins[i] < 0.70:
            patch.set_facecolor('darkred')
        elif bins[i] < 0.80:
            patch.set_facecolor('red')
        elif bins[i] < 0.90:
            patch.set_facecolor('orange')
        elif bins[i] < 0.95:
            patch.set_facecolor('yellow')
        else:
            patch.set_facecolor('green')
    
    # Add mean and median lines
    mean_acc = attack_data['accuracy'].mean()
    median_acc = attack_data['accuracy'].median()
    ax.axvline(x=mean_acc, color='blue', linestyle='-', linewidth=2, label=f'Mean: {mean_acc:.4f}')
    ax.axvline(x=median_acc, color='red', linestyle='--', linewidth=2, label=f'Median: {median_acc:.4f}')
    
    # Labels and title
    ax.set_xlabel('Accuracy', fontsize=10)
    ax.set_ylabel('Frequency', fontsize=10)
    ax.set_title(attack.replace('_', ' ').title(), fontsize=11, fontweight='bold')
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)
    ax.set_xlim([0, 1.05])
    
    # Add statistics text box
    min_acc = attack_data['accuracy'].min()
    max_acc = attack_data['accuracy'].max()
    std_acc = attack_data['accuracy'].std()
    
    stats_text = f'Min: {min_acc:.4f}\n'
    stats_text += f'Max: {max_acc:.4f}\n'
    stats_text += f'Std: {std_acc:.4f}\n'
    stats_text += f'N: {len(attack_data)}'
    
    ax.text(0.02, 0.98, stats_text, transform=ax.transAxes,
            fontsize=8, verticalalignment='top',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.7))

plt.tight_layout()
plt.savefig('target/comprehensive_evaluation/individual_attack_histograms.png', dpi=300, bbox_inches='tight')
print("✓ Saved: target/comprehensive_evaluation/individual_attack_histograms.png")
plt.close()

# ============================================================================
# 3. ATTACK COMPARISON - All attacks overlaid
# ============================================================================

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
fig.suptitle('Attack Performance Comparison', fontsize=16, fontweight='bold')

# Accuracy distributions
colors = plt.cm.tab10(np.linspace(0, 1, len(single_attacks)))
for idx, attack in enumerate(single_attacks):
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        ax1.hist(attack_data['accuracy'], bins=30, alpha=0.5, label=attack.replace('uc0', 'UC').replace('_', ' '),
                 color=colors[idx], edgecolor='black', linewidth=0.5)

ax1.set_xlabel('Accuracy', fontsize=12)
ax1.set_ylabel('Frequency', fontsize=12)
ax1.set_title('Accuracy Distribution by Attack', fontsize=13, fontweight='bold')
ax1.legend(fontsize=8, loc='upper left')
ax1.grid(True, alpha=0.3)
ax1.set_xlim([0.4, 1.05])

# Recall distributions
for idx, attack in enumerate(single_attacks):
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        ax2.hist(attack_data['recall'], bins=30, alpha=0.5, label=attack.replace('uc0', 'UC').replace('_', ' '),
                 color=colors[idx], edgecolor='black', linewidth=0.5)

ax2.set_xlabel('Recall', fontsize=12)
ax2.set_ylabel('Frequency', fontsize=12)
ax2.set_title('Recall Distribution by Attack', fontsize=13, fontweight='bold')
ax2.legend(fontsize=8, loc='upper left')
ax2.grid(True, alpha=0.3)
ax2.set_xlim([0, 1.05])

plt.tight_layout()
plt.savefig('target/comprehensive_evaluation/attack_comparison_overlay.png', dpi=300, bbox_inches='tight')
print("✓ Saved: target/comprehensive_evaluation/attack_comparison_overlay.png")
plt.close()

# ============================================================================
# 4. Box plots for better comparison
# ============================================================================

fig, axes = plt.subplots(3, 1, figsize=(14, 15))
fig.suptitle('Attack Performance Box Plots', fontsize=16, fontweight='bold')

# Prepare data for box plots
attack_accuracy = []
attack_recall = []
attack_f1 = []
attack_labels = []

for attack in single_attacks:
    attack_data = df[df['testAttack'] == attack]
    if len(attack_data) > 0:
        attack_accuracy.append(attack_data['accuracy'].values)
        attack_recall.append(attack_data['recall'].values)
        attack_f1.append(attack_data['f1'].values)
        attack_labels.append(attack.replace('_', '\n'))

# Accuracy box plot
bp1 = axes[0].boxplot(attack_accuracy, labels=attack_labels, patch_artist=True,
                       showmeans=True, meanline=True)
for patch in bp1['boxes']:
    patch.set_facecolor('lightblue')
axes[0].set_ylabel('Accuracy', fontsize=12)
axes[0].set_title('Accuracy Distribution by Attack', fontsize=13, fontweight='bold')
axes[0].grid(True, alpha=0.3, axis='y')
axes[0].tick_params(axis='x', rotation=45, labelsize=9)

# Recall box plot
bp2 = axes[1].boxplot(attack_recall, labels=attack_labels, patch_artist=True,
                       showmeans=True, meanline=True)
for patch in bp2['boxes']:
    patch.set_facecolor('lightcoral')
axes[1].set_ylabel('Recall', fontsize=12)
axes[1].set_title('Recall Distribution by Attack', fontsize=13, fontweight='bold')
axes[1].grid(True, alpha=0.3, axis='y')
axes[1].tick_params(axis='x', rotation=45, labelsize=9)

# F1 box plot
bp3 = axes[2].boxplot(attack_f1, labels=attack_labels, patch_artist=True,
                       showmeans=True, meanline=True)
for patch in bp3['boxes']:
    patch.set_facecolor('lightgreen')
axes[2].set_ylabel('F1 Score', fontsize=12)
axes[2].set_title('F1 Score Distribution by Attack', fontsize=13, fontweight='bold')
axes[2].grid(True, alpha=0.3, axis='y')
axes[2].tick_params(axis='x', rotation=45, labelsize=9)

plt.tight_layout()
plt.savefig('target/comprehensive_evaluation/attack_boxplots.png', dpi=300, bbox_inches='tight')
print("✓ Saved: target/comprehensive_evaluation/attack_boxplots.png")
plt.close()

# ============================================================================
# 5. Simple vs Combined for Each Attack
# ============================================================================

fig, axes = plt.subplots(4, 2, figsize=(16, 20))
fig.suptitle('Simple vs Combined Training by Attack', fontsize=18, fontweight='bold')

for idx, attack in enumerate(single_attacks):
    row = idx // 2
    col = idx % 2
    ax = axes[row, col]
    
    attack_data = df[df['testAttack'] == attack]
    
    if len(attack_data) == 0:
        ax.text(0.5, 0.5, 'No data', ha='center', va='center')
        ax.set_title(attack.replace('_', ' ').title())
        continue
    
    simple = attack_data[attack_data['trainingPattern'] == 'simple']['accuracy']
    combined = attack_data[attack_data['trainingPattern'] == 'combined']['accuracy']
    
    # Create side-by-side histograms
    bins = np.linspace(min(attack_data['accuracy']), max(attack_data['accuracy']), 25)
    ax.hist(simple, bins=bins, alpha=0.6, label=f'Simple (μ={simple.mean():.4f})', 
            color='orange', edgecolor='black')
    ax.hist(combined, bins=bins, alpha=0.6, label=f'Combined (μ={combined.mean():.4f})', 
            color='blue', edgecolor='black')
    
    ax.set_xlabel('Accuracy', fontsize=10)
    ax.set_ylabel('Frequency', fontsize=10)
    ax.set_title(attack.replace('_', ' ').title(), fontsize=11, fontweight='bold')
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)
    
    # Add difference text
    diff = combined.mean() - simple.mean()
    better = "Combined" if diff > 0 else "Simple"
    diff_text = f'Difference: {diff:+.4f}\nBetter: {better}'
    ax.text(0.98, 0.98, diff_text, transform=ax.transAxes,
            fontsize=9, verticalalignment='top', horizontalalignment='right',
            bbox=dict(boxstyle='round', facecolor='yellow' if abs(diff) > 0.01 else 'white', alpha=0.7))

plt.tight_layout()
plt.savefig('target/comprehensive_evaluation/simple_vs_combined_by_attack.png', dpi=300, bbox_inches='tight')
print("✓ Saved: target/comprehensive_evaluation/simple_vs_combined_by_attack.png")
plt.close()

print("\n" + "="*80)
print("All histograms generated successfully!")
print("="*80)
print("\nGenerated files:")
print("1. simple_vs_combined_histogram.png - Overall comparison of training patterns")
print("2. individual_attack_histograms.png - Individual attack performance distributions")
print("3. attack_comparison_overlay.png - All attacks overlaid for comparison")
print("4. attack_boxplots.png - Box plots showing distribution statistics")
print("5. simple_vs_combined_by_attack.png - Pattern comparison for each attack")
