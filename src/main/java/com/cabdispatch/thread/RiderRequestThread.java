package com.cabdispatch.thread;

import com.cabdispatch.dao.RideDAO;
import com.cabdispatch.exception.InvalidRequestException;
import com.cabdispatch.exception.NoDriverAvailableException;
import com.cabdispatch.model.*;
import com.cabdispatch.service.DriverPool;
import com.cabdispatch.service.SurgePricingService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker thread simulating a concurrent rider requesting, riding, and releasing a cab.
 */
public class RiderRequestThread extends Thread {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double BASE_FLAG_RATE = 50.0;
    private static final double RATE_PER_KM = 15.0;

    private final Rider rider;
    private final DriverPool driverPool;
    private final SurgePricingService surgePricingService;
    private final RideDAO rideDAO;
    private final AtomicInteger activeRequestCounter;
    private final long simulatedRideDurationMs;

    private Ride completedRide;

    public RiderRequestThread(Rider rider,
                              DriverPool driverPool,
                              SurgePricingService surgePricingService,
                              RideDAO rideDAO,
                              AtomicInteger activeRequestCounter,
                              long simulatedRideDurationMs) {
        super("Thread-" + rider.getRiderId());
        this.rider = rider;
        this.driverPool = driverPool;
        this.surgePricingService = surgePricingService;
        this.rideDAO = rideDAO;
        this.activeRequestCounter = activeRequestCounter;
        this.simulatedRideDurationMs = simulatedRideDurationMs;
    }

    public Ride getCompletedRide() {
        return completedRide;
    }

    @Override
    public void run() {
        String rideId = "RIDE-" + rider.getRiderId() + "-" + System.currentTimeMillis() % 10000;
        String requestTime = LocalDateTime.now().format(TIME_FORMATTER);

        // Track real-time concurrent demand
        int currentActiveRequests = activeRequestCounter.incrementAndGet();

        System.out.printf("[%s] [REQUEST FIRED] %s requested a ride (%s -> %s, %.1f km). [Active Queue: %d]%n",
                getName(), rider.getName(), rider.getPickupLocation(), rider.getDropLocation(),
                rider.getDistanceKm(), currentActiveRequests);

        Driver assignedDriver = null;
        double baseFare = BASE_FLAG_RATE + (rider.getDistanceKm() * RATE_PER_KM);
        double surgeMultiplier = 1.0;
        double finalFare = baseFare;

        try {
            // Validate input before processing
            if (rider.getDistanceKm() <= 0) {
                throw new InvalidRequestException("Invalid distance " + rider.getDistanceKm() + " km for rider " + rider.getName());
            }

            // Calculate dynamic surge multiplier based on active demand vs available supply
            int availableSupply = driverPool.getAvailableDriverCount();
            surgeMultiplier = surgePricingService.calculateSurgeMultiplier(currentActiveRequests, availableSupply);
            finalFare = Math.round((baseFare * surgeMultiplier) * 100.0) / 100.0;

            // Core atomic dispatch: synchronized check-and-assign
            assignedDriver = driverPool.assignDriver(rider.getName());
            rideDAO.updateDriverStatus(assignedDriver.getDriverId(), DriverStatus.BUSY);

            System.out.printf("  ==> [DISPATCH SUCCESS] %s assigned to %s | Driver: %s (%s) | Base: Rs.%.2f | Surge: %.2fx | Final: Rs.%.2f%n",
                    getName(), rider.getName(), assignedDriver.getName(), assignedDriver.getDriverId(),
                    baseFare, surgeMultiplier, finalFare);

            // Simulate the trip duration
            Thread.sleep(simulatedRideDurationMs);

            String completionTime = LocalDateTime.now().format(TIME_FORMATTER);
            completedRide = new Ride(rideId, rider, assignedDriver, baseFare, surgeMultiplier,
                    finalFare, RideStatus.COMPLETED, requestTime, completionTime);

            // Persist completed ride to DB
            rideDAO.saveRide(completedRide);

            System.out.printf("  <== [RIDE COMPLETED] %s dropped at %s. Driver %s (%s) is being released.%n",
                    rider.getName(), rider.getDropLocation(), assignedDriver.getName(), assignedDriver.getDriverId());

        } catch (NoDriverAvailableException e) {
            // Handled gracefully without crash
            System.err.printf("  <!> [DISPATCH REJECTED] %s: %s%n", getName(), e.getMessage());
            completedRide = new Ride(rideId, rider, null, baseFare, surgeMultiplier,
                    0.0, RideStatus.CANCELLED_NO_DRIVER, requestTime, null);
            try {
                rideDAO.saveRide(completedRide);
            } catch (InvalidRequestException ex) {
                System.err.println("[DB ERROR] Failed to record cancelled ride: " + ex.getMessage());
            }

        } catch (InvalidRequestException e) {
            System.err.printf("  <!> [INVALID REQUEST] %s: %s%n", getName(), e.getMessage());

        } catch (InterruptedException e) {
            System.err.printf("  <!> [INTERRUPTED] %s trip was interrupted: %s%n", getName(), e.getMessage());
            Thread.currentThread().interrupt();

        } finally {
            // Guarantee driver release and status reset back to AVAILABLE
            if (assignedDriver != null) {
                driverPool.releaseDriver(assignedDriver.getDriverId());
                rideDAO.updateDriverStatus(assignedDriver.getDriverId(), DriverStatus.AVAILABLE);
            }
            activeRequestCounter.decrementAndGet();
        }
    }
}
