# Project Statement: Cab Dispatch & Surge Pricing Simulator

**Course Code**: CSE2006 — Programming in Java  
**Project Category**: Build Your Own Project (BYOP) Submission  
**Academic Year**: 2026  

---

## 1. Problem Statement

In modern on-demand transportation systems (such as Uber, Lyft, and Ola), ride requests fluctuate unpredictably, generating sudden concurrency spikes while available driver supply remains constrained and geographically fixed.

Naive software implementations in concurrent dispatch environments fail due to two fundamental architectural vulnerabilities:
1. **Critical Section Race Conditions (Double-Booking)**: When multiple riders fire dispatch requests at the same instant, unsynchronized threads concurrently query the pool, observe the same driver as `AVAILABLE`, and simultaneously assign that driver to multiple riders.
2. **Inelastic Static Pricing**: When demand vastly exceeds capacity, failing to dynamically scale prices results in instantaneous fleet exhaustion, unmanaged request queues, and economic inefficiency.

Consequently, there is a clear necessity for a robust, multi-threaded dispatch simulation engine that guarantees:
- **Mutual Exclusion**: Mathematically preventing double-booking through atomic monitor locks.
- **Dynamic Surge Pricing**: Regulating demand in real-time according to supply-to-demand ratios.
- **Defensive Error Handling**: Catching fleet exhaustion gracefully via custom checked exceptions without application failure.
- **Reliable Data Persistence & Reporting**: Preserving transactional history in an embedded database and outputting file audit logs.

---

## 2. Scope of the Project

The **Cab Dispatch & Surge Pricing Simulator** is designed as a focused, production-quality, cross-platform Java simulation engine adhering to strict modularity without unnecessary third-party frameworks.

### In-Scope:
* **Concurrency Management**: Worker threads (`RiderRequestThread`) simulating concurrent passenger lifecycles and contending for shared drivers.
* **Synchronization & Mutual Exclusion**: Synchronized check-and-assign critical section in `DriverPool` to eliminate race conditions.
* **Dynamic Surge Pricing Engine**: Algorithmic multiplier calculation $\min(\max(\frac{\text{Demand}}{\text{Supply}}, 1.0), 3.0)$ based on active demand queues.
* **Custom Exception Architecture**: Domain-specific exceptions (`NoDriverAvailableException`, `InvalidRequestException`) with recovery logic.
* **Relational Database Persistence**: Embedded SQLite integration using plain JDBC (`RideDAO`) to store driver metadata and transactional ride histories.
* **File I/O Stream Reporting**: Structured text report generation using `BufferedWriter` and `FileWriter`.
* **Zero-Dependency Automated Testing**: Built-in test harness (`TestRunner`) testing concurrency invariants, pricing bounds, and persistence.
* **Self-Contained Executable**: Single-command execution via `CabDispatch.jar` or native build scripts (`build.bat`, `build.sh`).

### Out-of-Scope (Deliberately Avoided to Maintain Clean Focus):
* Heavy external frameworks (Spring Boot, Hibernate, JPA) that obscure core Java concurrency mechanics.
* Distributed network sockets or cloud-based message queues.
* Complex GPS routing or road network graph traversal (approximated accurately with Euclidean trip distances).

---

## 3. Technical Architecture & Component Interaction

The application follows a clean, decoupled 5-tier architecture:

```
+-------------------------------------------------------------------------+
|                              Main / CLI                                 |
|          (Seeds Fleet, Spawns Threads, Awaits Join, Exports Log)        |
+-------------------+---------------------------------+-------------------+
                    |                                 |
                    v                                 v
+------------------------------------+    +-------------------------------+
|      RiderRequestThread (xN)       |    |      TestRunner (Harness)     |
| (Simulates Contention & Ride Time) |    |  (Unit & Concurrency Tests)   |
+-------------------+----------------+    +-------------------------------+
                    |
      +-------------+-------------+
      |                           |
      v                           v
+-------------------------+  +--------------------------------------------+
|   SurgePricingService   |  |           DriverPool (Synchronized)        |
| (Dynamic Ratio Engine)  |  | (HashMap Fleet, Atomic Assign & Release)   |
+-------------------------+  +--------------------+-----------------------+
                                                  |
                                                  v
                                     +----------------------------+
                                     |          RideDAO           |
                                     |    (SQLite JDBC Engine)    |
                                     +--------------+-------------+
                                                    |
                                                    v
                                     +----------------------------+
                                     |      cab_dispatch.db       |
                                     |     (drivers & rides)      |
                                     +----------------------------+
```

### Component Breakdown:
1. **`com.cabdispatch.model`**: Defines pure domain entities (`Driver`, `Rider`, `Ride`) and type-safe enums (`DriverStatus`, `RideStatus`).
2. **`com.cabdispatch.service`**:
   - `DriverPool`: Holds the in-memory `HashMap<String, Driver>` fleet, exposing `synchronized` methods for driver allocation and release.
   - `SurgePricingService`: Computes dynamic multipliers based on current contention.
3. **`com.cabdispatch.exception`**: Houses domain-specific exceptions (`NoDriverAvailableException`, `InvalidRequestException`).
4. **`com.cabdispatch.dao`**: `RideDAO` encapsulates SQLite JDBC CRUD operations and handles schema generation automatically.
5. **`com.cabdispatch.thread`**: `RiderRequestThread` orchestrates the rider lifecycle from dispatch request to arrival and driver release.
6. **`com.cabdispatch.test`**: Standalone `TestRunner` providing 6 automated verification suites without JUnit dependencies.

---

## 4. Key Algorithmic Workflows & Concurrency Guarantees

### Algorithmic Workflow 1: Synchronized Driver Assignment
```
[Rider Thread Starts]
         |
         v
[Increment activeRequestCounter]
         |
         v
[Calculate Surge Multiplier: max(1.0, min(3.0, Active / Available))]
         |
         v
[Enter DriverPool.assignDriver() -> ACQUIRE MONITOR LOCK]
         |
    +----+--------------------------------+
    | Is any Driver Status == AVAILABLE?  |
    +----+--------------------------------+
         |                                |
       (YES)                             (NO)
         |                                |
         v                                v
[Set Driver Status = BUSY]        [RELEASE MONITOR LOCK]
         |                                |
[RELEASE MONITOR LOCK]                    v
         |                    [Throw NoDriverAvailableException]
         v                                |
[Return Assigned Driver]                  v
         |                    [Log Failure & Save Ride with CANCELLED]
         v
[Thread.sleep(Trip Duration)]
         |
         v
[FINALLY Block: releaseDriver() -> Reset Status to AVAILABLE]
         |
[Decrement activeRequestCounter]
```

### Mathematical Proof of Mutual Exclusion
Let $S$ represent the state of Driver $D_i$ where $S \in \{\text{AVAILABLE}, \text{BUSY}\}$.  
Let $T_A$ and $T_B$ represent two racing rider threads requesting an assignment simultaneously at time $t$.  
In Java, marking `assignDriver()` with `synchronized` binds the method to the intrinsic lock of the `DriverPool` instance ($L_{\text{pool}}$).  
By JVM specification:
$$\text{Holder}(L_{\text{pool}}, t) \le 1 \quad \forall t$$
Since only one thread can hold $L_{\text{pool}}$, Thread $T_A$ enters the critical section while $T_B$ transitions to `BLOCKED`. $T_A$ evaluates $S(D_i) = \text{AVAILABLE}$, mutates $S(D_i) \leftarrow \text{BUSY}$, and exits, releasing $L_{\text{pool}}$. When $T_B$ subsequently acquires $L_{\text{pool}}$, $S(D_i) = \text{BUSY}$. Therefore, $D_i$ cannot be assigned to $T_B$. Double-booking is mathematically impossible.

---

## 5. Database Schema & Data Models

The persistence layer connects to `jdbc:sqlite:cab_dispatch.db` and manages two tables:

### Table 1: `drivers`
| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `driver_id` | `VARCHAR(50)` | `PRIMARY KEY` | Unique identifier (e.g., `D1`, `D2`) |
| `name` | `VARCHAR(100)` | `NOT NULL` | Full legal name of the driver |
| `phone` | `VARCHAR(20)` | `NOT NULL` | Contact telephone number |
| `status` | `VARCHAR(20)` | `NOT NULL` | Operational state: `AVAILABLE` or `BUSY` |

### Table 2: `rides`
| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `ride_id` | `VARCHAR(50)` | `PRIMARY KEY` | Unique transaction ID (e.g., `RIDE-R1-4208`) |
| `rider_id` | `VARCHAR(50)` | `NOT NULL` | ID of the passenger |
| `rider_name` | `VARCHAR(100)` | `NOT NULL` | Full name of the passenger |
| `driver_id` | `VARCHAR(50)` | `NULLABLE` | Assigned driver ID (NULL if no driver available) |
| `pickup_location`| `VARCHAR(100)` | `NOT NULL` | Starting route address |
| `drop_location` | `VARCHAR(100)` | `NOT NULL` | Destination route address |
| `distance_km` | `REAL` | `NOT NULL` | Route distance in kilometers |
| `base_fare` | `REAL` | `NOT NULL` | Standard unadjusted fare (₹) |
| `surge_multiplier`| `REAL` | `NOT NULL` | Dynamic multiplier applied ($1.00\times - 3.00\times$) |
| `final_fare` | `REAL` | `NOT NULL` | Total fare paid (₹0.00 if cancelled) |
| `status` | `VARCHAR(30)` | `NOT NULL` | `COMPLETED` or `CANCELLED_NO_DRIVER` |
| `request_time` | `TEXT` | `NOT NULL` | Timestamp of request initiation |
| `completion_time`| `TEXT` | `NULLABLE` | Timestamp of drop-off completion |

---

## 6. Non-Functional Specifications

* **Concurrency Safety**: 100% thread-safe; verified by automated barrier tests firing 10 simultaneous threads without collision.
* **Deterministic Resource Deallocation**: Uses Java `try-finally` semantics to guarantee that every assigned driver is released, eliminating resource leaks under abnormal termination.
* **Zero External Server Overhead**: Uses embedded SQLite; requires no separate database daemon installation or network port bindings.
* **Portability**: Verified to compile and execute uniformly across Windows, Linux, and macOS platforms.

---

## 7. Technical Defense & Viva Voce Q&A

### Q1: Why use `synchronized` instead of `ConcurrentHashMap`?
> *"`ConcurrentHashMap` provides thread-safe operations for single read or put actions, but it does NOT provide atomicity across a multi-step Check-Then-Act sequence (checking if status is AVAILABLE, selecting the driver, and setting status to BUSY). Using `synchronized` on `DriverPool` ensures that the entire discovery and assignment sequence executes as one indivisible critical section."*

### Q2: What prevents driver starvation or deadlocks?
> *"Deadlocks require circular wait conditions across multiple locks. Our design uses a single monitor lock on `DriverPool`, entirely eliminating lock ordering cycles. Furthermore, driver release is enclosed inside a `finally` block in `RiderRequestThread`, ensuring that even if a thread is interrupted or throws an exception, the driver is guaranteed to return to the pool."*

### Q3: How is dynamic pricing calculated and capped?
> *"The multiplier is calculated as `Active Requests / Available Drivers`. If demand is less than or equal to supply, it defaults to 1.0x (base rate). If supply hits zero or demand spikes heavily, the ratio is clamped at a maximum ceiling of 3.0x using `Math.min(ratio, 3.0)` to protect riders from predatory price inflation."*
