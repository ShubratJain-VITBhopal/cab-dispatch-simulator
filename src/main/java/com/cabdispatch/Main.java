package com.cabdispatch;

import com.cabdispatch.dao.RideDAO;
import com.cabdispatch.model.Driver;
import com.cabdispatch.model.DriverStatus;
import com.cabdispatch.model.Ride;
import com.cabdispatch.model.RideStatus;
import com.cabdispatch.model.Rider;
import com.cabdispatch.service.DriverPool;
import com.cabdispatch.service.SurgePricingService;
import com.cabdispatch.thread.RiderRequestThread;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main application driver:
 * 1. Seeds database and driver pool
 * 2. Spawns concurrent rider threads forcing resource contention
 * 3. Awaits completion and proves mutual exclusion (no double-booking)
 * 4. Displays and exports summary report via Java I/O streams
 */
public class Main {
    private static final String REPORT_FILE = "simulation_report.txt";

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("          CAB DISPATCH & SURGE PRICING SIMULATOR (CSE2006)");
        System.out.println("===============================================================================\n");

        // 1. Initialize Persistence Layer & Shared Services
        RideDAO rideDAO = new RideDAO();
        rideDAO.initializeDatabase();
        System.out.println("[INIT] SQLite Database initialized (tables: drivers, rides).");

        DriverPool driverPool = new DriverPool();
        SurgePricingService surgePricingService = new SurgePricingService();
        AtomicInteger activeRequestCounter = new AtomicInteger(0);

        // 2. Seed Driver Pool (Limited supply: 3 drivers)
        Driver[] initialDrivers = {
                new Driver("D1", "Alice Smith", "+91-9876543210", DriverStatus.AVAILABLE),
                new Driver("D2", "Bob Johnson", "+91-9876543211", DriverStatus.AVAILABLE),
                new Driver("D3", "Charlie Brown", "+91-9876543212", DriverStatus.AVAILABLE)
        };

        System.out.println("\n[SEEDING] Registering " + initialDrivers.length + " drivers into shared DriverPool & Database:");
        for (Driver d : initialDrivers) {
            driverPool.addDriver(d);
            rideDAO.saveDriver(d);
            System.out.println("  -> Registered: " + d);
        }

        // 3. Prepare Simulated Riders (High demand: 7 riders competing for 3 cabs)
        List<Rider> testRiders = new ArrayList<>();
        testRiders.add(new Rider("R1", "Aarav Sharma", "MG Road", "Indiranagar", 4.5));
        testRiders.add(new Rider("R2", "Diya Patel", "Koramangala", "HSR Layout", 3.2));
        testRiders.add(new Rider("R3", "Rohan Mehta", "Whitefield", "Marathahalli", 7.0));
        testRiders.add(new Rider("R4", "Sneha Rao", "Electronic City", "Silk Board", 8.4));
        testRiders.add(new Rider("R5", "Vikram Singh", "Hebbal", "Yelahanka", 6.1));
        testRiders.add(new Rider("R6", "Ananya Verma", "Jayanagar", "JP Nagar", 2.8));
        testRiders.add(new Rider("R7", "Kabir Nair", "BTM Layout", "Bannerghatta", 5.0));

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("LAUNCHING " + testRiders.size() + " CONCURRENT RIDER THREADS (Supply: 3 drivers, Demand: 7 requests)");
        System.out.println("-------------------------------------------------------------------------------\n");

        List<RiderRequestThread> threads = new ArrayList<>();
        long rideDurationMs = 1500; // 1.5 seconds simulated trip

        // Launch all threads in rapid succession to force race conditions
        for (Rider rider : testRiders) {
            RiderRequestThread thread = new RiderRequestThread(
                    rider,
                    driverPool,
                    surgePricingService,
                    rideDAO,
                    activeRequestCounter,
                    rideDurationMs
            );
            threads.add(thread);
            thread.start();
        }

        // 4. Wait for all rider threads to complete
        for (RiderRequestThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println("Thread join interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println("ALL THREADS COMPLETED. GENERATING SIMULATION REPORT");
        System.out.println("-------------------------------------------------------------------------------\n");

        // 5. Aggregate Results & Statistics
        int totalRequests = threads.size();
        int successfulDispatches = 0;
        int failedRequests = 0;
        double totalRevenue = 0.0;
        double surgeSum = 0.0;

        List<Ride> completedRidesList = new ArrayList<>();

        for (RiderRequestThread t : threads) {
            Ride ride = t.getCompletedRide();
            if (ride != null) {
                completedRidesList.add(ride);
                if (ride.getStatus() == RideStatus.COMPLETED) {
                    successfulDispatches++;
                    totalRevenue += ride.getFinalFare();
                    surgeSum += ride.getSurgeMultiplier();
                } else if (ride.getStatus() == RideStatus.CANCELLED_NO_DRIVER) {
                    failedRequests++;
                    surgeSum += ride.getSurgeMultiplier();
                }
            }
        }

        double avgSurge = totalRequests > 0 ? (surgeSum / totalRequests) : 1.0;

        // 6. Print Summary to Console
        System.out.println("========== SIMULATION SUMMARY REPORT ==========");
        System.out.println("Total Rider Requests   : " + totalRequests);
        System.out.println("Successful Dispatches  : " + successfulDispatches);
        System.out.println("Failed (No Driver)     : " + failedRequests);
        System.out.printf("Average Surge Applied  : %.2fx%n", avgSurge);
        System.out.printf("Total Revenue Generated: Rs.%.2f%n", totalRevenue);
        System.out.println("===============================================\n");

        // 7. Verify Persistence via JDBC Query
        System.out.println("[DATABASE AUDIT] Fetching ride records persisted in SQLite database:");
        List<Ride> persistedRides = rideDAO.getAllRides();
        for (Ride r : persistedRides) {
            System.out.println("  " + r);
        }

        // 8. Export Report to File via Java I/O Streams
        exportReportToFile(REPORT_FILE, totalRequests, successfulDispatches,
                failedRequests, avgSurge, totalRevenue, persistedRides);
        System.out.println("\n[FILE I/O] Summary report successfully exported to: " + REPORT_FILE);
    }

    /**
     * Writes the simulation summary and ride breakdown to a text file using FileWriter and BufferedWriter.
     */
    private static void exportReportToFile(String filename,
                                           int totalRequests,
                                           int successfulDispatches,
                                           int failedRequests,
                                           double avgSurge,
                                           double totalRevenue,
                                           List<Ride> rides) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("============================================================\n");
            writer.write("      CAB DISPATCH & SURGE PRICING SIMULATOR REPORT        \n");
            writer.write("============================================================\n\n");
            writer.write(String.format("Total Requests Submitted : %d%n", totalRequests));
            writer.write(String.format("Successful Dispatches    : %d%n", successfulDispatches));
            writer.write(String.format("Failed Requests (No Cab) : %d%n", failedRequests));
            writer.write(String.format("Average Surge Multiplier : %.2fx%n", avgSurge));
            writer.write(String.format("Total Revenue (INR)      : Rs.%.2f%n%n", totalRevenue));

            writer.write("------------------------------------------------------------\n");
            writer.write("                     RIDE AUDIT LOG                         \n");
            writer.write("------------------------------------------------------------\n");
            for (Ride r : rides) {
                String driverStr = (r.getDriver() != null) ? r.getDriver().getDriverId() : "NONE";
                writer.write(String.format("Ride ID: %-16s | Rider: %-14s | Driver: %-5s | Fare: Rs.%-7.2f | Surge: %-4.2fx | Status: %s%n",
                        r.getRideId(),
                        r.getRider().getName(),
                        driverStr,
                        r.getFinalFare(),
                        r.getSurgeMultiplier(),
                        r.getStatus()));
            }
            writer.write("\n======================= END OF REPORT ======================\n");
        } catch (IOException e) {
            System.err.println("[FILE I/O ERROR] Failed to write report file: " + e.getMessage());
        }
    }
}
