package com.cabdispatch.service;

import com.cabdispatch.exception.NoDriverAvailableException;
import com.cabdispatch.model.Driver;
import com.cabdispatch.model.DriverStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe shared pool managing all active cab drivers.
 * Uses synchronized methods to ensure mutual exclusion during driver
 * discovery, status mutation, and assignment — preventing double-booking.
 */
public class DriverPool {
    private final Map<String, Driver> drivers = new HashMap<>();

    /**
     * Registers a new driver into the pool.
     */
    public synchronized void addDriver(Driver driver) {
        drivers.put(driver.getDriverId(), driver);
    }

    /**
     * Atomically checks for an available driver, marks them BUSY, and assigns them.
     * The synchronized block guarantees that two racing rider threads will never
     * be assigned the same driver.
     *
     * @param riderName Name of the rider requesting a cab.
     * @return The assigned Driver instance.
     * @throws NoDriverAvailableException If all drivers in the pool are BUSY.
     */
    public synchronized Driver assignDriver(String riderName) throws NoDriverAvailableException {
        for (Driver driver : drivers.values()) {
            if (driver.getStatus() == DriverStatus.AVAILABLE) {
                driver.setStatus(DriverStatus.BUSY);
                return driver;
            }
        }
        throw new NoDriverAvailableException("All drivers are currently BUSY. Unable to dispatch cab for: " + riderName);
    }

    /**
     * Atomically releases a driver back into the pool, resetting their status to AVAILABLE.
     *
     * @param driverId Unique ID of the driver to release.
     */
    public synchronized void releaseDriver(String driverId) {
        Driver driver = drivers.get(driverId);
        if (driver != null) {
            driver.setStatus(DriverStatus.AVAILABLE);
        }
    }

    /**
     * Returns the count of drivers currently AVAILABLE.
     */
    public synchronized int getAvailableDriverCount() {
        int count = 0;
        for (Driver driver : drivers.values()) {
            if (driver.getStatus() == DriverStatus.AVAILABLE) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the total count of registered drivers in the pool.
     */
    public synchronized int getTotalDriverCount() {
        return drivers.size();
    }

    /**
     * Returns a snapshot copy of all drivers currently in the pool.
     */
    public synchronized List<Driver> getAllDrivers() {
        return new ArrayList<>(drivers.values());
    }
}
