package com.cabdispatch.model;

/**
 * Represents the lifecycle status of a ride request.
 */
public enum RideStatus {
    REQUESTED,
    CONFIRMED,
    COMPLETED,
    CANCELLED_NO_DRIVER
}
