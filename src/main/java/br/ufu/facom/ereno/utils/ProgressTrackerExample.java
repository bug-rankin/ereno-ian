package br.ufu.facom.ereno.utils;

/**
 * Example demonstrating the usage of ProgressTracker.
 * This is for testing and demonstration purposes.
 */
@SuppressWarnings("BusyWait")
public class ProgressTrackerExample {
    
    public static void main(String[] args) throws InterruptedException {
        // Example 1: Simple linear progress
        System.out.println("\n=== Example 1: Simple Progress Tracking ===\n");
        simpleProgressExample();
        
        Thread.sleep(2000);
        
        // Example 2: Nested progress (like pipelines with loops)
        System.out.println("\n=== Example 2: Nested Progress Tracking ===\n");
        nestedProgressExample();
    }
    
    private static void simpleProgressExample() throws InterruptedException {
        ProgressTracker tracker = new ProgressTracker("Simple Task", 5);
        tracker.start();
        
        for (int i = 1; i <= 5; i++) {
            String stepDesc = "Processing step " + i;
            tracker.incrementStep(stepDesc);
            
            // Simulate work
            Thread.sleep(1000);
            
            tracker.completeCurrentStep("Step " + i + " done");
        }
        
        tracker.complete();
    }
    
    private static void nestedProgressExample() throws InterruptedException {
        // Main tracker for overall pipeline
        ProgressTracker mainTracker = new ProgressTracker("Pipeline with Loops", 12);
        mainTracker.start();
        
        // Pre-loop steps (2 steps)
        mainTracker.incrementStep("Pre-loop: Generate benign data");
        Thread.sleep(800);
        mainTracker.completeCurrentStep();
        
        mainTracker.incrementStep("Pre-loop: Create baseline test dataset");
        Thread.sleep(800);
        mainTracker.completeCurrentStep();
        
        // Loop iterations (3 iterations × 2 steps = 6 steps)
        ProgressTracker loopTracker = mainTracker.createSubTracker("Attack Combinations", 3);
        loopTracker.start();
        
        String[] attacks = {"RandomReplay + InverseReplay", "RandomReplay + Injection", "Flooding + Grayhole"};
        
        for (int i = 0; i < 3; i++) {
            loopTracker.incrementStep("Processing: " + attacks[i]);
            
            // Step 1: Create attack dataset
            mainTracker.incrementStep("  └─ Create attack dataset for " + attacks[i]);
            Thread.sleep(600);
            mainTracker.completeCurrentStep();
            
            // Step 2: Train model
            mainTracker.incrementStep("  └─ Train model for " + attacks[i]);
            Thread.sleep(600);
            mainTracker.completeCurrentStep();
            
            loopTracker.completeCurrentStep();
        }
        
        loopTracker.complete();
        
        // Post-loop steps (4 evaluation steps)
        for (int i = 1; i <= 4; i++) {
            mainTracker.incrementStep("Evaluating model " + i + " against all test datasets");
            Thread.sleep(500);
            mainTracker.completeCurrentStep();
        }
        
        mainTracker.complete();
    }
}
