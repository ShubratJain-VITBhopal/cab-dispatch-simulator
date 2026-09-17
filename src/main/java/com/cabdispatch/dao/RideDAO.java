package com.cabdispatch.dao;

import com.cabdispatch.exception.InvalidRequestException;
import com.cabdispatch.model.Driver;
import com.cabdispatch.model.DriverStatus;
import com.cabdispatch.model.Ride;
import com.cabdispatch.model.RideStatus;
import com.cabdispatch.model.Rider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object handling SQLite JDBC persistence for drivers and rides.
 * Performs basic input validation prior to database mutations.
 */
public class RideDAO {
    static {
        try {
            Class<?> clazz = Class.forName("org.sqlite.JDBC");
            java.sql.Driver driver = (java.sql.Driver) clazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(driver);
        } catch (Exception e) {
            System.err.println("[DB ERROR] Failed to register SQLite JDBC driver: " + e.getMessage());
        }
    }

    private final String dbUrl;

    public RideDAO() {
        this.dbUrl = "jdbc:sqlite:cab_dispatch.db";
    }

    public RideDAO(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    /**
     * Establishes a connection to the SQLite database.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Initializes database tables if they do not already exist.
     */
    public void initializeDatabase() {
        String createDriversTable = "CREATE TABLE IF NOT EXISTS drivers (" +
                "driver_id VARCHAR(50) PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "phone VARCHAR(20) NOT NULL, " +
                "status VARCHAR(20) NOT NULL" +
                ");";

        String createRidesTable = "CREATE TABLE IF NOT EXISTS rides (" +
                "ride_id VARCHAR(50) PRIMARY KEY, " +
                "rider_id VARCHAR(50) NOT NULL, " +
                "rider_name VARCHAR(100) NOT NULL, " +
                "driver_id VARCHAR(50), " +
                "pickup_location VARCHAR(100) NOT NULL, " +
                "drop_location VARCHAR(100) NOT NULL, " +
                "distance_km REAL NOT NULL, " +
                "base_fare REAL NOT NULL, " +
                "surge_multiplier REAL NOT NULL, " +
                "final_fare REAL NOT NULL, " +
                "status VARCHAR(30) NOT NULL, " +
                "request_time TEXT NOT NULL, " +
                "completion_time TEXT" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createDriversTable);
            stmt.execute(createRidesTable);
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to initialize database tables: " + e.getMessage());
        }
    }

    /**
     * Inserts or updates a driver record in the database.
     */
    public void saveDriver(Driver driver) {
        if (driver == null || driver.getDriverId() == null || driver.getName() == null) {
            System.err.println("[DB WARNING] Cannot save null or incomplete driver record.");
            return;
        }

        String sql = "INSERT INTO drivers (driver_id, name, phone, status) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(driver_id) DO UPDATE SET status = excluded.status;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, driver.getDriverId());
            pstmt.setString(2, driver.getName());
            pstmt.setString(3, driver.getPhone());
            pstmt.setString(4, driver.getStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to save driver " + driver.getDriverId() + ": " + e.getMessage());
        }
    }

    /**
     * Updates the status of an existing driver in the database.
     */
    public void updateDriverStatus(String driverId, DriverStatus status) {
        if (driverId == null || status == null) {
            return;
        }

        String sql = "UPDATE drivers SET status = ? WHERE driver_id = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, driverId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to update driver status: " + e.getMessage());
        }
    }

    /**
     * Inserts a ride record into the database with basic validation.
     *
     * @param ride The Ride object to persist.
     * @throws InvalidRequestException If validation fails (e.g., negative fare or distance).
     */
    public void saveRide(Ride ride) throws InvalidRequestException {
        if (ride == null) {
            throw new InvalidRequestException("Ride cannot be null.");
        }
        if (ride.getRider() == null || ride.getRider().getName() == null) {
            throw new InvalidRequestException("Rider information cannot be null.");
        }
        if (ride.getRider().getDistanceKm() <= 0) {
            throw new InvalidRequestException("Ride distance must be strictly positive (> 0 km).");
        }
        if (ride.getFinalFare() < 0) {
            throw new InvalidRequestException("Ride fare cannot be negative.");
        }

        String sql = "INSERT INTO rides (ride_id, rider_id, rider_name, driver_id, " +
                "pickup_location, drop_location, distance_km, base_fare, surge_multiplier, " +
                "final_fare, status, request_time, completion_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ride.getRideId());
            pstmt.setString(2, ride.getRider().getRiderId());
            pstmt.setString(3, ride.getRider().getName());
            pstmt.setString(4, (ride.getDriver() != null) ? ride.getDriver().getDriverId() : null);
            pstmt.setString(5, ride.getRider().getPickupLocation());
            pstmt.setString(6, ride.getRider().getDropLocation());
            pstmt.setDouble(7, ride.getRider().getDistanceKm());
            pstmt.setDouble(8, ride.getBaseFare());
            pstmt.setDouble(9, ride.getSurgeMultiplier());
            pstmt.setDouble(10, ride.getFinalFare());
            pstmt.setString(11, ride.getStatus().name());
            pstmt.setString(12, ride.getRequestTime());
            pstmt.setString(13, ride.getCompletionTime());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to save ride " + ride.getRideId() + ": " + e.getMessage());
        }
    }

    /**
     * Retrieves all recorded rides from the database.
     */
    public List<Ride> getAllRides() {
        List<Ride> rides = new ArrayList<>();
        String sql = "SELECT * FROM rides ORDER BY request_time ASC;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String rideId = rs.getString("ride_id");
                String riderId = rs.getString("rider_id");
                String riderName = rs.getString("rider_name");
                String driverId = rs.getString("driver_id");
                String pickup = rs.getString("pickup_location");
                String drop = rs.getString("drop_location");
                double distance = rs.getDouble("distance_km");
                double baseFare = rs.getDouble("base_fare");
                double surge = rs.getDouble("surge_multiplier");
                double finalFare = rs.getDouble("final_fare");
                RideStatus status = RideStatus.valueOf(rs.getString("status"));
                String reqTime = rs.getString("request_time");
                String compTime = rs.getString("completion_time");

                Rider rider = new Rider(riderId, riderName, pickup, drop, distance);
                Driver driver = null;
                if (driverId != null) {
                    driver = new Driver(driverId, "Driver " + driverId, "N/A", DriverStatus.AVAILABLE);
                }

                Ride ride = new Ride(rideId, rider, driver, baseFare, surge, finalFare, status, reqTime, compTime);
                rides.add(ride);
            }
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to fetch ride history: " + e.getMessage());
        }

        return rides;
    }
}
