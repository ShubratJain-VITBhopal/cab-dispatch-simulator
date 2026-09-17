package com.cabdispatch.model;

/**
 * Entity representing a ride transaction with pricing and dispatch details.
 */
public class Ride {
    private String rideId;
    private Rider rider;
    private Driver driver;
    private double baseFare;
    private double surgeMultiplier;
    private double finalFare;
    private RideStatus status;
    private String requestTime;
    private String completionTime;

    public Ride(String rideId, Rider rider, Driver driver, double baseFare,
                double surgeMultiplier, double finalFare, RideStatus status,
                String requestTime, String completionTime) {
        this.rideId = rideId;
        this.rider = rider;
        this.driver = driver;
        this.baseFare = baseFare;
        this.surgeMultiplier = surgeMultiplier;
        this.finalFare = finalFare;
        this.status = status;
        this.requestTime = requestTime;
        this.completionTime = completionTime;
    }

    public String getRideId() {
        return rideId;
    }

    public Rider getRider() {
        return rider;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public double getBaseFare() {
        return baseFare;
    }

    public double getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public double getFinalFare() {
        return finalFare;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(RideStatus status) {
        this.status = status;
    }

    public String getRequestTime() {
        return requestTime;
    }

    public String getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(String completionTime) {
        this.completionTime = completionTime;
    }

    @Override
    public String toString() {
        return String.format("Ride[ID=%s, Rider=%s, Driver=%s, Fare=Rs.%.2f (Surge=%.2fx), Status=%s]",
                rideId, rider.getName(), (driver != null ? driver.getName() : "None"),
                finalFare, surgeMultiplier, status);
    }
}
