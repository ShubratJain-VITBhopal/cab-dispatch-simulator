package com.cabdispatch.model;

/**
 * Entity representing a rider initiating a ride request.
 */
public class Rider {
    private String riderId;
    private String name;
    private String pickupLocation;
    private String dropLocation;
    private double distanceKm;

    public Rider(String riderId, String name, String pickupLocation, String dropLocation, double distanceKm) {
        this.riderId = riderId;
        this.name = name;
        this.pickupLocation = pickupLocation;
        this.dropLocation = dropLocation;
        this.distanceKm = distanceKm;
    }

    public String getRiderId() {
        return riderId;
    }

    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDropLocation() {
        return dropLocation;
    }

    public void setDropLocation(String dropLocation) {
        this.dropLocation = dropLocation;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    @Override
    public String toString() {
        return String.format("Rider[ID=%s, Name=%s, %s -> %s (%.1f km)]",
                riderId, name, pickupLocation, dropLocation, distanceKm);
    }
}
