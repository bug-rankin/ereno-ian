package br.ufu.facom.ereno.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Utility class for tracking progress, displaying progress bars, and estimating time remaining.
 * Supports nested progress tracking for pipelines with loops.
 */
public class ProgressTracker {
    
    private static final Logger LOGGER = Logger.getLogger(ProgressTracker.class.getName());
    
    private final String taskName;
    private final int totalSteps;
    private int currentStep;
    private Instant startTime;
    private Instant lastUpdateTime;
    
    private ProgressTracker parentTracker;
    
    // Progress bar configuration
    private static final int PROGRESS_BAR_WIDTH = 50;
    private static final String PROGRESS_CHAR = "█";
    private static final String EMPTY_CHAR = "░";
    
    public ProgressTracker(String taskName, int totalSteps) {
        this(taskName, totalSteps, null);
    }
    
    public ProgressTracker(String taskName, int totalSteps, ProgressTracker parentTracker) {
        this.taskName = taskName;
        this.totalSteps = totalSteps;
        this.currentStep = 0;
        this.startTime = Instant.now();
        this.lastUpdateTime = this.startTime;
        this.parentTracker = parentTracker;
    }
    
    /**
     * Start tracking progress for a new task.
     */
    public void start() {
        this.startTime = Instant.now();
        this.lastUpdateTime = this.startTime;
        this.currentStep = 0;
        
        printSeparator();
        LOGGER.info(String.format("Starting: %s (Total steps: %d)", taskName, totalSteps));
        printSeparator();
        displayProgress();
    }
    
    /**
     * Increment the current step and display updated progress.
     */
    public void incrementStep() {
        incrementStep(null);
    }
    
    /**
     * Increment the current step with a description and display updated progress.
     */
    public void incrementStep(String stepDescription) {
        currentStep++;
        lastUpdateTime = Instant.now();
        
        if (stepDescription != null && !stepDescription.isEmpty()) {
            LOGGER.info(String.format("[Step %d/%d] %s", currentStep, totalSteps, stepDescription));
        }
        
        displayProgress();
    }
    
    /**
     * Mark the current step as complete (without incrementing).
     */
    public void completeCurrentStep() {
        completeCurrentStep(null);
    }
    
    /**
     * Mark the current step as complete with a message.
     */
    public void completeCurrentStep(String message) {
        if (message != null && !message.isEmpty()) {
            LOGGER.info(() -> "✓ " + message);
        }
        displayProgress();
    }
    
    /**
     * Complete the entire task.
     */
    public void complete() {
        currentStep = totalSteps;
        lastUpdateTime = Instant.now();
        
        displayProgress();
        printSeparator();
        
        Duration totalDuration = Duration.between(startTime, lastUpdateTime);
        LOGGER.info(String.format("Completed: %s", taskName));
        LOGGER.info(String.format("Total time: %s", formatDuration(totalDuration)));
        printSeparator();
    }
    
    /**
     * Display the current progress bar and time estimates.
     */
    private void displayProgress() {
        double percentComplete = (double) currentStep / totalSteps * 100.0;
        String progressBar = createProgressBar(percentComplete);
        
        Duration elapsed = Duration.between(startTime, lastUpdateTime);
        Duration estimated = estimateTimeRemaining(elapsed);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n[%s] %.1f%% (%d/%d)", 
            progressBar, percentComplete, currentStep, totalSteps));
        sb.append(String.format("\nElapsed: %s", formatDuration(elapsed)));
        
        if (currentStep < totalSteps && currentStep > 0) {
            sb.append(String.format(" | Remaining: ~%s", formatDuration(estimated)));
            Instant eta = lastUpdateTime.plus(estimated);
            sb.append(String.format(" | ETA: %s", 
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(eta)));
        }
        sb.append("\n");
        
        System.out.println(sb.toString());
        System.out.flush();
    }
    
    /**
     * Create a visual progress bar.
     */
    private String createProgressBar(double percentComplete) {
        int filledChars = (int) Math.round(PROGRESS_BAR_WIDTH * percentComplete / 100.0);
        int emptyChars = PROGRESS_BAR_WIDTH - filledChars;
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filledChars; i++) {
            bar.append(PROGRESS_CHAR);
        }
        for (int i = 0; i < emptyChars; i++) {
            bar.append(EMPTY_CHAR);
        }
        
        return bar.toString();
    }
    
    /**
     * Estimate time remaining based on current progress.
     */
    private Duration estimateTimeRemaining(Duration elapsed) {
        if (currentStep == 0) {
            return Duration.ZERO;
        }
        
        long elapsedSeconds = elapsed.getSeconds();
        double avgSecondsPerStep = (double) elapsedSeconds / currentStep;
        int remainingSteps = totalSteps - currentStep;
        long estimatedSeconds = (long) (avgSecondsPerStep * remainingSteps);
        
        return Duration.ofSeconds(estimatedSeconds);
    }
    
    /**
     * Format a duration in a human-readable format.
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
    
    /**
     * Print a visual separator line.
     */
    private void printSeparator() {
        System.out.println("═".repeat(80));
    }
    
    /**
     * Get the current step number.
     */
    public int getCurrentStep() {
        return currentStep;
    }
    
    /**
     * Get the total number of steps.
     */
    public int getTotalSteps() {
        return totalSteps;
    }
    
    /**
     * Get the task name.
     */
    public String getTaskName() {
        return taskName;
    }
    
    /**
     * Get the percentage complete.
     */
    public double getPercentComplete() {
        return (double) currentStep / totalSteps * 100.0;
    }
    
    /**
     * Get the elapsed time.
     */
    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }
    
    /**
     * Create a nested progress tracker for sub-tasks.
     */
    public ProgressTracker createSubTracker(String subTaskName, int subTotalSteps) {
        return new ProgressTracker(subTaskName, subTotalSteps, this);
    }
    
    /**
     * Get the parent tracker (if this is a nested tracker).
     */
    public ProgressTracker getParentTracker() {
        return parentTracker;
    }
    
    /**
     * Check if this tracker is complete.
     */
    public boolean isComplete() {
        return currentStep >= totalSteps;
    }
    
    /**
     * Reset the tracker to start again.
     */
    public void reset() {
        this.currentStep = 0;
        this.startTime = Instant.now();
        this.lastUpdateTime = this.startTime;
    }
}
