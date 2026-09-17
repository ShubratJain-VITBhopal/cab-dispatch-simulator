package com.cabdispatch.test;

import com.cabdispatch.dao.RideDAO;
import com.cabdispatch.exception.InvalidRequestException;
import com.cabdispatch.exception.NoDriverAvailableException;
import com.cabdispatch.model.Driver;
import com.cabdispatch.model.DriverStatus;
import com.cabdispatch.model.Ride;
import com.cabdispatch.model.RideStatus;
import com.cabdispatch.model.Rider;
import com.cabdispatch.service.DriverPool;
import com.cabdispatch.service.SurgePricingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Standalone, zero-dependency automated test runner for Cab Dispatch Simulator.
 * Evaluates concurrency safety, exception handling, dynamic pricing math, and JDBC persistence.
 */
public class TestRunner {
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   AUTOMATED TEST SUITE: Cab Dispatch & Surge Pricing Simulator   ");
        System.out.println("==================================================================\n");

        runTest("Surge Pricing Calculation & Clamping", TestRunner::testSurgePricing);
        runTest("Input Validation (InvalidRequestException)", TestRunner::testInputValidation);
        runTest("Fleet Exhaustion (NoDriverAvailableException)", TestRunner::testFleetExhaustion);
        runTest("Driver Pool Release & State Recovery", TestRunner::testDriverRelease);
        runTest("Concurrency Safety & Zero Double-Booking", TestRunner::testConcurrencySafety);
        runTest("SQLite Database JDBC CRUD Operations", TestRunner::testDatabasePersistence);

        System.out.println("\n------------------------------------------------------------------");
        System.out.printf("TEST SUMMARY: Total: %d | Passed: %d | Failed: %d%n", totalTests, passedTests, failedTests);
        System.out.println("------------------------------------------------------------------");

        if (failedTests > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, TestCase testCase) {
        totalTests++;
        System.out.printf("[RUNNING] Test %d: %s...", totalTests, testName);
        try {
            testCase.execute();
            passedTests++;
            System.out.println(" [PASSED]");
        } catch (Throwable t) {
            failedTests++;
            System.out.println(" [FAILED]");
            System.err.println("  -> Error: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void testSurgePricing() throws Exception {
        SurgePricingService service = new SurgePricingService();

        // 1. Equal supply and demand -> 1.0x
        double s1 = service.calculateSurgeMultiplier(3, 3);
        assertTrue(s1 == 1.0, "Expected 1.0x when demand equals supply, got: " + s1);

        // 2. Demand less than supply -> 1.0x
        double s2 = service.calculateSurgeMultiplier(2, 4);
        assertTrue(s2 == 1.0, "Expected 1.0x when demand < supply, got: " + s2);

        // 3. Demand > supply -> proportional ratio
        double s3 = service.calculateSurgeMultiplier(4, 2);
        assertTrue(s3 == 2.0, "Expected 2.0x for 4 requests / 2 drivers, got: " + s3);

        // 4. Extreme demand -> capped at 3.0x
        double s4 = service.calculateSurgeMultiplier(20, 2);
        assertTrue(s4 == 3.0, "Expected 3.0x max cap, got: " + s4);

        // 5. Zero drivers -> capped at 3.0x
        double s5 = service.calculateSurgeMultiplier(5, 0);
        assertTrue(s5 == 3.0, "Expected 3.0x when available drivers is 0, got: " + s5);
    }

    private static void testInputValidation() throws Exception {
        RideDAO dao = new RideDAO("jdbc:sqlite:test_dispatch.db");
        dao.initializeDatabase();

        // Invalid negative distance
        Rider invalidRider = new Rider("R_INV", "Test Rider", "A", "B", -5.0);
        Ride invalidRide = new Ride("RIDE_INV", invalidRider, null, 50.0, 1.0, 50.0,
                RideStatus.REQUESTED, "2026-09-17 12:00:00", null);

        boolean exceptionThrown = false;
        try {
            dao.saveRide(invalidRide);
        } catch (InvalidRequestException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown, "Expected InvalidRequestException for negative distance.");
    }

    private static void testFleetExhaustion() throws Exception {
        DriverPool pool = new DriverPool();
        pool.addDriver(new Driver("T_D1", "Driver One", "000", DriverStatus.AVAILABLE));

        // First assignment must succeed
        Driver d1 = pool.assignDriver("Rider 1");
        assertTrue(d1.getDriverId().equals("T_D1"), "Assigned driver ID mismatch.");
        assertTrue(d1.getStatus() == DriverStatus.BUSY, "Assigned driver must be BUSY.");

        // Second assignment must throw NoDriverAvailableException
        boolean exceptionThrown = false;
        try {
            pool.assignDriver("Rider 2");
        } catch (NoDriverAvailableException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown, "Expected NoDriverAvailableException when pool is empty.");
    }

    private static void testDriverRelease() throws Exception {
        DriverPool pool = new DriverPool();
        pool.addDriver(new Driver("T_D1", "Driver One", "000", DriverStatus.AVAILABLE));

        Driver d1 = pool.assignDriver("Rider 1");
        assertTrue(pool.getAvailableDriverCount() == 0, "Available count should be 0.");

        pool.releaseDriver("T_D1");
        assertTrue(pool.getAvailableDriverCount() == 1, "Available count should be 1 after release.");
        assertTrue(d1.getStatus() == DriverStatus.AVAILABLE, "Driver status should revert to AVAILABLE.");
    }

    private static void testConcurrencySafety() throws Exception {
        DriverPool pool = new DriverPool();
        int driverCount = 3;
        for (int i = 1; i <= driverCount; i++) {
            pool.addDriver(new Driver("C_D" + i, "Driver " + i, "111", DriverStatus.AVAILABLE));
        }

        int threadCount = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<String> assignedDriverIds = Collections.synchronizedList(new ArrayList<>());
        List<String> rejectedRiders = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= threadCount; i++) {
            final String riderName = "ConcurrentRider_" + i;
            new Thread(() -> {
                try {
                    startGate.await(); // Synchronize all threads to fire simultaneously
                    Driver assigned = pool.assignDriver(riderName);
                    assignedDriverIds.add(assigned.getDriverId());
                } catch (NoDriverAvailableException e) {
                    rejectedRiders.add(riderName);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            }).start();
        }

        // Release the start gate so all 10 threads race at once
        startGate.countDown();
        endGate.await();

        // Exactly 3 assignments must have succeeded
        assertTrue(assignedDriverIds.size() == 3, "Expected exactly 3 assignments, got: " + assignedDriverIds.size());
        // Exactly 7 requests must have been rejected
        assertTrue(rejectedRiders.size() == 7, "Expected exactly 7 rejections, got: " + rejectedRiders.size());

        // Prove mutual exclusion: Every assigned driver ID must be unique (no double-booking)
        Set<String> uniqueAssignedIds = new HashSet<>(assignedDriverIds);
        assertTrue(uniqueAssignedIds.size() == 3,
                "Double-booking detected! Unique assigned drivers: " + uniqueAssignedIds.size() + ", expected: 3");
    }

    private static void testDatabasePersistence() throws Exception {
        RideDAO dao = new RideDAO("jdbc:sqlite:test_dispatch.db");
        dao.initializeDatabase();

        Driver driver = new Driver("DB_D1", "Test DB Driver", "+91-9999999999", DriverStatus.AVAILABLE);
        dao.saveDriver(driver);

        Rider rider = new Rider("DB_R1", "Test DB Rider", "Source", "Destination", 5.0);
        String testRideId = "RIDE_DB_" + System.currentTimeMillis();
        Ride ride = new Ride(testRideId, rider, driver, 125.0, 1.5, 187.5,
                RideStatus.COMPLETED, "2026-09-17 12:30:00", "2026-09-17 12:45:00");
        dao.saveRide(ride);

        List<Ride> history = dao.getAllRides();
        assertTrue(!history.isEmpty(), "Ride history should not be empty.");

        boolean found = false;
        for (Ride r : history) {
            if (testRideId.equals(r.getRideId())) {
                found = true;
                assertTrue(r.getFinalFare() == 187.5, "Fare mismatch in DB record.");
                assertTrue(r.getStatus() == RideStatus.COMPLETED, "Status mismatch in DB record.");
                break;
            }
        }
        assertTrue(found, "Saved ride was not found in SQLite query result.");
    }

    private static void assertTrue(boolean condition, String failureMessage) {
        if (!condition) {
            throw new AssertionError(failureMessage);
        }
    }

    @FunctionalInterface
    interface TestCase {
        void execute() throws Exception;
    }
}
