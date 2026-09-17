# Cab Dispatch & Surge Pricing Simulator

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/)
[![Course](https://img.shields.io/badge/Course-CSE2006%20Programming%20in%20Java-orange.svg)]()
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Database](https://img.shields.io/badge/Database-SQLite%20(JDBC)-lightgrey.svg)](https://www.sqlite.org/)
[![License](https://img.shields.io/badge/License-Academic%20Use-green.svg)]()

A concurrency-safe, multi-threaded ride-hailing dispatch simulation engine built in pure Java for the **CSE2006 (Programming in Java)** course project. The simulator models real-time competition among concurrent riders for a constrained driver fleet, mathematically guarantees **zero double-booking** through synchronized monitor locking, applies dynamic **surge pricing**, manages exceptions gracefully, and provides automated test verification and persistent JDBC audit logging.

---

## 🚀 Features

* **Thread-Safe Driver Allocation**: Guarantees zero double-booking across simultaneous, racing rider requests using atomic mutual exclusion locks (`synchronized`) on the shared `DriverPool`.
* **Dynamic Surge Pricing Algorithm**: Computes real-time price multipliers ($Demand / Supply$, clamped between $1.0\times$ and $3.0\times$) to adjust trip fares during peak concurrency bursts.
* **Graceful Fleet Exhaustion Handling**: Detects driver shortages and raises a custom `NoDriverAvailableException`, recording cancellations gracefully without application crashes.
* **Automated Defensive Resource Recovery**: Wraps driver release operations inside a `finally` block in `RiderRequestThread`, ensuring drivers are returned to the `AVAILABLE` pool even upon interruption or error.
* **Embedded SQLite JDBC Persistence**: Automatically initializes and maintains relational tables (`drivers`, `rides`) in `cab_dispatch.db` via `RideDAO` with parameterized SQL statements.
* **File I/O Stream Export**: Generates and formats a complete post-simulation analytics report (`simulation_report.txt`) using standard Java character streams (`BufferedWriter`, `FileWriter`).
* **Standalone Automated Test Suite**: Zero-dependency test runner (`TestRunner`) asserting concurrency mutual exclusion, pricing boundaries, input validation, and database CRUD.
* **100% Standalone Executable**: Packaged into an executable fat JAR (`CabDispatch.jar`) executable across Windows, Linux, and macOS without external dependencies.

---

## 📋 Prerequisites & Technologies

* **Java Development Kit (JDK)**: Version 17 or higher (`javac` and `java` added to system PATH).
* **Core Libraries & Tools**:
  * `java.lang.Thread` & `java.util.concurrent.atomic.AtomicInteger`: Concurrency and atomic telemetry.
  * `java.sql.*`: SQLite JDBC connection management and prepared statements.
  * `java.io.*`: File writing and character stream buffering.
  * `sqlite-jdbc` (v3.45.1.0) & `slf4j`: Embedded database engine and logging facade.

---

## 📁 Project Architecture

```
Cab-Dispatch-Surge-Pricing-Simulator/
├── CabDispatch.jar                              # Standalone executable JAR
├── build.bat                                    # Windows automated build, test & packaging script
├── build.sh                                     # Linux/macOS build script
├── run.bat & run.ps1                            # Direct compile & execution scripts
├── pom.xml                                      # Maven build configuration
├── statement.md                                 # Formal academic project statement
├── README.md                                    # Project documentation and viva guide
├── simulation_report.txt                        # Exported simulation audit report
├── cab_dispatch.db                              # SQLite embedded database file
├── lib/                                         # Embedded dependency libraries
│   ├── sqlite-jdbc.jar
│   ├── slf4j-api.jar
│   └── slf4j-nop.jar
└── src/
    └── main/
        ├── resources/
        │   └── schema.sql                       # Database DDL schema
        └── java/
            └── com/
                └── cabdispatch/
                    ├── Main.java                # Simulation coordinator & entry point
                    ├── model/
                    │   ├── Driver.java          # Driver entity (ID, name, phone, status)
                    │   ├── Rider.java           # Rider entity (ID, name, route, distance)
                    │   ├── Ride.java            # Ride transaction entity (fares, surge, status)
                    │   ├── DriverStatus.java    # Enum: AVAILABLE, BUSY
                    │   └── RideStatus.java      # Enum: REQUESTED, CONFIRMED, COMPLETED, CANCELLED_NO_DRIVER
                    ├── dao/
                    │   └── RideDAO.java         # SQLite JDBC persistence (CRUD & audit)
                    ├── service/
                    │   ├── DriverPool.java      # Synchronized driver fleet manager
                    │   └── SurgePricingService.java # Dynamic pricing ratio calculation
                    ├── exception/
                    │   ├── NoDriverAvailableException.java # Thrown on fleet exhaustion
                    │   └── InvalidRequestException.java   # Thrown on invalid ride parameters
                    ├── thread/
                    │   └── RiderRequestThread.java # Worker thread modeling rider lifecycle
                    └── test/
                        └── TestRunner.java      # Standalone 6-point automated test runner
```

---

## 🧠 Concurrency & Synchronization Model (Viva Preparation)

### 1. The Race Condition Problem
In an unsynchronized system, when two riders ($R_1$ and $R_2$) submit requests concurrently:
1. Thread $R_1$ queries the pool and identifies Driver $D_1$ with `status == AVAILABLE`.
2. Before $R_1$ can mutate the driver's status, the OS thread scheduler performs a context switch to Thread $R_2$.
3. Thread $R_2$ inspects the pool, observes that $D_1$ is **still** marked `AVAILABLE`, assigns $D_1$ to itself, and sets status to `BUSY`.
4. The scheduler switches back to Thread $R_1$, which continues its previous execution and also assigns $D_1$ to itself.
5. **Result**: Both riders are dispatched the same vehicle—a physical double-booking disaster.

### 2. The Synchronized Critical Section in `DriverPool`
```java
public synchronized Driver assignDriver(String riderName) throws NoDriverAvailableException {
    for (Driver driver : drivers.values()) {
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            driver.setStatus(DriverStatus.BUSY);
            return driver;
        }
    }
    throw new NoDriverAvailableException("All drivers are currently BUSY. Unable to dispatch cab for: " + riderName);
}
```

* **Monitor Lock**: The `synchronized` keyword locks the monitor of the shared `DriverPool` instance.
* **Mutual Exclusion**: Only a single thread may enter the method at any given nanosecond. All competing threads are forced into the `BLOCKED` state.
* **Atomicity**: The check (`if AVAILABLE`) and update (`setStatus(BUSY)`) occur as an atomic, indivisible operation. When the lock is released, the driver is already `BUSY`, preventing double-assignment.
* **Leak-Proof Recovery**: Driver return is guaranteed in `RiderRequestThread.run()` using a `finally` block:
```java
finally {
    if (assignedDriver != null) {
        driverPool.releaseDriver(assignedDriver.getDriverId());
        rideDAO.updateDriverStatus(assignedDriver.getDriverId(), DriverStatus.AVAILABLE);
    }
    activeRequestCounter.decrementAndGet();
}
```

---

## 📈 Dynamic Surge Pricing Formula

$$\text{Surge Multiplier} = \min\left(\max\left(\frac{\text{Active Requests}}{\text{Available Drivers}}, 1.0\right), 3.0\right)$$

* **Base Rate**: ₹50.00 initial flag drop + ₹15.00 per kilometer.
* **Standard Pricing**: When available drivers $\ge$ active requests, multiplier remains **1.00x**.
* **Surge Escalation**: When active demand exceeds idle capacity, prices scale linearly up to a safety ceiling of **3.00x**.
* **Zero Supply Protection**: If available drivers equal zero, multiplier defaults to **3.00x**.

---

## 🗄️ Database Schema (`cab_dispatch.db`)

```sql
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
```

---

## 🧪 Automated Validation Test Suite

Run the built-in automated test suite to verify system integrity:

```bash
java -cp "bin;lib/*" com.cabdispatch.test.TestRunner
```

### Verified Test Cases
| # | Test Name | Assertion / Validation | Status |
| :-: | :--- | :--- | :-: |
| 1 | `Surge Pricing Calculation & Clamping` | Asserts ratio math, baseline 1.0x, 3.0x ceiling, and zero-driver fallback | **PASSED** |
| 2 | `Input Validation` | Asserts `InvalidRequestException` thrown on negative trip distance | **PASSED** |
| 3 | `Fleet Exhaustion` | Asserts `NoDriverAvailableException` thrown when all drivers are busy | **PASSED** |
| 4 | `Driver Pool State Recovery` | Asserts driver transitions from `BUSY` back to `AVAILABLE` on release | **PASSED** |
| 5 | `Concurrency Safety & Mutual Exclusion` | 10 concurrent racing threads against 3 drivers; asserts **0 double-bookings** | **PASSED** |
| 6 | `SQLite Database Persistence` | Full CRUD roundtrip: table init, driver save, ride insert, history query | **PASSED** |

---

## 🛠️ How to Build and Run

### 1. One-Click Build & Test (Recommended)
**Windows**:
```cmd
build.bat
```
**Linux / macOS**:
```bash
chmod +x build.sh
./build.sh
```
*This compiles all files, executes the test suite, and generates `CabDispatch.jar`.*

### 2. Run Standalone Executable JAR
```bash
java -jar CabDispatch.jar
```

### 3. Run Directly via Script
```cmd
run.bat
```
or via PowerShell:
```powershell
.\run.ps1
```

### 4. Build with Maven
```bash
mvn clean compile
mvn exec:java
```

---

## 📊 Sample Simulation Output

```text
===============================================================================
          CAB DISPATCH & SURGE PRICING SIMULATOR (CSE2006)
===============================================================================

[INIT] SQLite Database initialized (tables: drivers, rides).

[SEEDING] Registering 3 drivers into shared DriverPool & Database:
  -> Registered: Driver[ID=D1, Name=Alice Smith, Status=AVAILABLE]
  -> Registered: Driver[ID=D2, Name=Bob Johnson, Status=AVAILABLE]
  -> Registered: Driver[ID=D3, Name=Charlie Brown, Status=AVAILABLE]

-------------------------------------------------------------------------------
LAUNCHING 7 CONCURRENT RIDER THREADS (Supply: 3 drivers, Demand: 7 requests)
-------------------------------------------------------------------------------

[Thread-R2] [REQUEST FIRED] Diya Patel requested a ride (Koramangala -> HSR Layout, 3.2 km). [Active Queue: 4]
[Thread-R6] [REQUEST FIRED] Ananya Verma requested a ride (Jayanagar -> JP Nagar, 2.8 km). [Active Queue: 3]
[Thread-R7] [REQUEST FIRED] Kabir Nair requested a ride (BTM Layout -> Bannerghatta, 5.0 km). [Active Queue: 5]
  ==> [DISPATCH SUCCESS] Thread-R2 assigned to Diya Patel | Driver: Alice Smith (D1) | Base: Rs.98.00 | Surge: 1.33x | Final: Rs.130.34
  ==> [DISPATCH SUCCESS] Thread-R6 assigned to Ananya Verma | Driver: Bob Johnson (D2) | Base: Rs.92.00 | Surge: 1.50x | Final: Rs.138.00
  ==> [DISPATCH SUCCESS] Thread-R7 assigned to Kabir Nair | Driver: Charlie Brown (D3) | Base: Rs.125.00 | Surge: 3.00x | Final: Rs.375.00
  <!> [DISPATCH REJECTED] Thread-R4: All drivers are currently BUSY. Unable to dispatch cab for: Sneha Rao
  <!> [DISPATCH REJECTED] Thread-R3: All drivers are currently BUSY. Unable to dispatch cab for: Rohan Mehta
  <!> [DISPATCH REJECTED] Thread-R5: All drivers are currently BUSY. Unable to dispatch cab for: Vikram Singh
  <!> [DISPATCH REJECTED] Thread-R1: All drivers are currently BUSY. Unable to dispatch cab for: Aarav Sharma
  <== [RIDE COMPLETED] Diya Patel dropped at HSR Layout. Driver Alice Smith (D1) is being released.
  <== [RIDE COMPLETED] Ananya Verma dropped at JP Nagar. Driver Bob Johnson (D2) is being released.
  <== [RIDE COMPLETED] Kabir Nair dropped at Bannerghatta. Driver Charlie Brown (D3) is being released.

-------------------------------------------------------------------------------
ALL THREADS COMPLETED. GENERATING SIMULATION REPORT
-------------------------------------------------------------------------------

========== SIMULATION SUMMARY REPORT ==========
Total Rider Requests   : 7
Successful Dispatches  : 3
Failed (No Driver)     : 4
Average Surge Applied  : 2.55x
Total Revenue Generated: Rs.643.34
===============================================

[DATABASE AUDIT] Fetching ride records persisted in SQLite database:
  Ride[ID=RIDE-R2-9355, Rider=Diya Patel, Driver=Driver D1, Fare=Rs.130.34 (Surge=1.33x), Status=COMPLETED]
  Ride[ID=RIDE-R6-9355, Rider=Ananya Verma, Driver=Driver D2, Fare=Rs.138.00 (Surge=1.50x), Status=COMPLETED]
  Ride[ID=RIDE-R7-9355, Rider=Kabir Nair, Driver=Driver D3, Fare=Rs.375.00 (Surge=3.00x), Status=COMPLETED]
  Ride[ID=RIDE-R4-9355, Rider=Sneha Rao, Driver=None, Fare=Rs.0.00 (Surge=3.00x), Status=CANCELLED_NO_DRIVER]
  Ride[ID=RIDE-R5-9355, Rider=Vikram Singh, Driver=None, Fare=Rs.0.00 (Surge=3.00x), Status=CANCELLED_NO_DRIVER]
  Ride[ID=RIDE-R3-9355, Rider=Rohan Mehta, Driver=None, Fare=Rs.0.00 (Surge=3.00x), Status=CANCELLED_NO_DRIVER]
  Ride[ID=RIDE-R1-9355, Rider=Aarav Sharma, Driver=None, Fare=Rs.0.00 (Surge=3.00x), Status=CANCELLED_NO_DRIVER]

[FILE I/O] Summary report successfully exported to: simulation_report.txt
```
