package com.cabdispatch.model;

/**
 * Entity representing a cab driver in the dispatch system.
 */
public class Driver {
    private String driverId;
    private String name;
    private String phone;
    private DriverStatus status;

    public Driver(String driverId, String name, String phone, DriverStatus status) {
        this.driverId = driverId;
        this.name = name;
        this.phone = phone;
        this.status = status;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("Driver[ID=%s, Name=%s, Status=%s]", driverId, name, status);
    }
}
