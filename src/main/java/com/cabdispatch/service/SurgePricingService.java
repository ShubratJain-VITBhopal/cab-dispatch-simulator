package com.cabdispatch.service;

/**
 * Service for calculating dynamic surge pricing multipliers based on
 * real-time supply-and-demand ratio.
 */
public class SurgePricingService {
    private static final double MIN_SURGE = 1.0;
    private static final double MAX_SURGE = 3.0;

    /**
     * Calculates the dynamic surge multiplier:
     * Surge Multiplier = activeRequests / availableDrivers
     * Clamped between 1.0x (normal) and 3.0x (maximum surge).
     *
     * @param activeRequests  Current number of active/pending ride requests.
     * @param availableDrivers Number of drivers currently marked AVAILABLE.
     * @return Surge multiplier rounded to 2 decimal places.
     */
    public double calculateSurgeMultiplier(int activeRequests, int availableDrivers) {
        if (availableDrivers <= 0) {
            return MAX_SURGE;
        }

        if (activeRequests <= availableDrivers) {
            return MIN_SURGE;
        }

        double ratio = (double) activeRequests / availableDrivers;
        double surge = Math.min(ratio, MAX_SURGE);
        surge = Math.max(surge, MIN_SURGE);

        // Round to 2 decimal places (e.g., 1.67)
        return Math.round(surge * 100.0) / 100.0;
    }
}
