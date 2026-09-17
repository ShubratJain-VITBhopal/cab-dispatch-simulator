-- Database Schema for Cab Dispatch & Surge Pricing Simulator
-- Engine: SQLite (JDBC compatible)

CREATE TABLE IF NOT EXISTS drivers (
    driver_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS rides (
    ride_id VARCHAR(50) PRIMARY KEY,
    rider_id VARCHAR(50) NOT NULL,
    rider_name VARCHAR(100) NOT NULL,
    driver_id VARCHAR(50),
    pickup_location VARCHAR(100) NOT NULL,
    drop_location VARCHAR(100) NOT NULL,
    distance_km REAL NOT NULL,
    base_fare REAL NOT NULL,
    surge_multiplier REAL NOT NULL,
    final_fare REAL NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_time TEXT NOT NULL,
    completion_time TEXT
);
