package br.ufu.facom.ereno.util;

/**
 * Centralized definition of dataset class labels.
 * Use Labels.LABELS as the canonical array and Labels.asArffSet() when
 * writing an ARFF attribute declaration.
 */
public class Labels {
    public static final String[] LABELS = {
            "normal",
            "random_replay",
            "inverse_replay",
            "masquerade_fake_fault",
            "masquerade_fake_normal",
            "injection",
            "high_StNum",
            "poisoned_high_rate",
            "grayhole",
            "delayed_replay",
    };

    /**
     * Binary classification labels (normal vs any attack)
     */
    public static final String[] BINARY_LABELS = {
            "normal",
            "attack"
    };

    /**
     * Returns the labels joined by ", " suitable for use inside an ARFF set.
     * Example: "normal, random_replay, inverse_replay"
     */
    public static String asArffSet() {
        return String.join(", ", LABELS);
    }

    /**
     * Returns binary labels for ARFF set (normal vs attack)
     */
    public static String asArffSetBinary() {
        return String.join(", ", BINARY_LABELS);
    }
}
