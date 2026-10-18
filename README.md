# Cafeteria Food Order & Billing System (Web Version)

A **website** built with **Java 17, Spring Boot 3, Spring Web, Spring JDBC, MySQL 8, Maven Wrapper, and a plain HTML/CSS/JavaScript frontend**. A cashier selects food items, enters quantities, calculates the bill, views an itemised invoice, saves the order to MySQL, and browses order history.

This file is the single source of truth for the project. Build it **step by step, one day at a time** (see the Execution Plan). Do not jump ahead.

---

## 1. Objectives

- Display cafeteria menu items with category and price.
- Allow selecting items and entering quantities.
- Calculate line totals and the grand total automatically.
- Show an itemised invoice (order number, date/time, items, quantities, unit prices, line totals, grand total).
- Save the order header and all order lines to MySQL using a JDBC transaction.
- Show previous orders in an order-history table, with an option to view an order's details.
- Validate input and show clear success/error messages.

## 2. Technology Stack

| Component | Purpose |
|---|---|
| Java 17+ | Application logic, OOP |
| Spring Boot 3 | Framework, embedded web server, dependency injection |
| Spring Web | RESTful controllers, static file serving |
| Spring JDBC (`JdbcTemplate`) | Connect Java to MySQL, run SQL & transactions |
| Bean Validation | Request and input validation |
| MySQL 8.x | Stores menu items, orders, order lines |
| Maven Wrapper (`mvnw` / `mvnw.cmd`) | Reproducible builds without pre-installed Maven |
| HTML / CSS / vanilla JavaScript | Browser user interface |

## 3. Functional Modules

1. **Menu Management** – read `menu_items` and show name, category, price (only `is_available = TRUE`).
2. **Order Entry** – select items and enter a positive whole-number quantity.
3. **Bill Calculation** – `lineTotal = unitPrice × quantity`; `grandTotal = Σ lineTotal`.
4. **Invoice Display** – formatted itemised invoice in the page.
5. **Database Saving** – insert into `orders` and `order_items` inside one transaction.
6. **Order History** – table of past orders; double-click/select to view details.

## 4. Database Design

File: `sql/cafeteria_db.sql`

```sql
CREATE DATABASE IF NOT EXISTS cafeteria_db;
USE cafeteria_db;

CREATE TABLE menu_items (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    item_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    price DECIMAL(10,2) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE
);

CREATE TABLE orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10,2) NOT NULL
);

CREATE TABLE order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    item_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (item_id) REFERENCES menu_items(item_id)
);

INSERT INTO menu_items (item_name, category, price) VALUES
('Veg Sandwich', 'Snacks', 60.00),
('Masala Dosa', 'South Indian', 90.00),
('Tea', 'Beverages', 15.00),
('Cold Coffee', 'Beverages', 70.00),
('Veg Burger', 'Fast Food', 80.00);
```

History query:

```sql
SELECT order_id, order_date, total_amount
FROM orders
ORDER BY order_date DESC;
```

## 5. Web Interface Layout

| Area | Contents |
|---|---|
| Top panel | Application title and live date/time |
| Menu panel | Table: item name, category, price, quantity input, select checkbox |
| Action row | Add to Order, Remove Item, Clear, Calculate Bill |
| Invoice panel | Monospace text area with itemised invoice and final amount |
| Bottom panel | Save Order, View Order History, Exit |
| History section | Hidden table of past orders, toggled by View Order History, with a Back button |

## 6. Bill Calculation Rules

- Use `BigDecimal` for all money in Java. Never use `float` or `double`.
- `lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity))`
- `grandTotal` = sum of all line totals.
- Prices are **always read from the database on the server**. Never trust prices sent by the browser.
- Currency symbol: ₹. Format to 2 decimal places.

Example: 2 × Veg Sandwich (₹60) = ₹120; 1 × Tea (₹15) = ₹15; **grand total = ₹135.00**.

## 7. JDBC Rules

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/cafeteria_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

- Read DB credentials from `application.properties` (listed in `.gitignore`) or environment variables (`DB_USERNAME`, `DB_PASSWORD`). **Never hard-code or commit real passwords.** Commit `application.properties.example` instead.
- Use `JdbcTemplate` / parameterized queries for every query that includes user input.
- Saving an order must be a transaction:
  1. Insert into `orders` (obtain generated `order_id`)
  2. Insert all `order_items` rows in batch
  3. Execute inside `@Transactional` boundary; rollback on exception.

## 8. REST API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Health check and database connectivity verification |
| GET | `/api/menu` | List available menu items |
| POST | `/api/orders/calculate` | Validate items and return line totals and grand total |
| POST | `/api/orders` | Save the order in a transaction; return order id, date and invoice data |
| GET | `/api/orders` | Order history (newest first) |
| GET | `/api/orders/{id}` | Details of one order (404 if not found) |

Errors are returned as JSON: `{"error": "message"}` with HTTP 400 (invalid input), 404 (not found) or 500 (server/database error).

## 9. Project Structure

```
CafeteriaFoodOrderSystem/
├── README.md
├── DEMO_NOTES.md
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── sql/
│   └── cafeteria_db.sql
├── screenshots/
│   └── README.md
└── src/
    ├── main/
    │   ├── java/com/cafeteria/
    │   │   ├── CafeteriaSystemApplication.java
    │   │   ├── controller/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── HealthController.java
    │   │   │   ├── MenuController.java
    │   │   │   └── OrderController.java
    │   │   ├── dto/
    │   │   │   ├── BillLine.java
    │   │   │   ├── BillResponse.java
    │   │   │   ├── OrderItemRequest.java
    │   │   │   ├── OrderRequest.java
    │   │   │   ├── OrderResponse.java
    │   │   │   └── OrderSummary.java
    │   │   ├── exception/
    │   │   │   └── ResourceNotFoundException.java
    │   │   ├── model/
    │   │   │   └── MenuItem.java
    │   │   └── service/
    │   │       └── OrderService.java
    │   └── resources/
    │       ├── application.properties
    │       ├── application.properties.example
    │       └── static/
    │           ├── app.js
    │           ├── index.html
    │           └── style.css
    └── test/
        └── java/com/cafeteria/
            ├── CafeteriaSystemApplicationTests.java
            └── service/
                └── OrderServiceTest.java
```

## 10. Seven-Day Execution Plan

All 7 implementation steps have been completed:

| Step | Tasks | Status | Verified Result |
|---|---|---|---|
| 1 | Create Spring Boot 3 Maven project, MySQL script, application properties example, health endpoint. | **COMPLETED** | Build succeeds, `/api/health` returns status:ok and database:connected |
| 2 | Static file serving skeleton (HTML/CSS/JS). | **COMPLETED** | Working page layout at `http://localhost:8080` |
| 3 | `GET /api/menu`; load menu data from MySQL into the frontend table. | **COMPLETED** | Dynamic database-backed menu with category tags |
| 4 | Item selection, quantity validation, bill calculation with `BigDecimal`. | **COMPLETED** | Correct totals (₹135.00 example), strict positive whole-number validation |
| 5 | Invoice display; save order and order items via JDBC transaction. | **COMPLETED** | Transactional save with atomic batch insert & rollback proven by unit tests |
| 6 | Order history table; load past orders; view order details. | **COMPLETED** | Order history table, row double-click and modal details with complete line items |
| 7 | Edge case tests, polish, screenshots, demo notes, git push guide. | **COMPLETED** | 100% testing checklist passed, DEMO_NOTES.md, screenshots guide, ready for submission |

## 11. Validation Rules

- Quantity must be a positive whole number. Zero, negative, blank, decimal, or non-numeric input is rejected with a clear message (in the browser **and** on the server).
- An empty order cannot be calculated or saved.
- Unavailable menu items (`is_available = FALSE`) are not shown and are rejected by the server if submitted.
- Show a success message with the order number after saving; show an error message on failure.
- The Save button must not create duplicate orders on repeated clicks (debounced & disabled during network request).

## 12. Testing Checklist

- [x] **Menu items load correctly from MySQL** (Verified via `GET /api/menu`).
- [x] **Quantity of zero, negative, blank, decimal or text is rejected** (Verified client-side regex + server HTTP 400).
- [x] **Bill total matches selected items and quantities** (Verified 2 × ₹60.00 + 1 × ₹15.00 = ₹135.00).
- [x] **Empty orders cannot be calculated or saved** (Verified HTTP 400 rejection).
- [x] **Saved orders appear in history after restarting the app** (Verified persistence across JVM restarts).
- [x] **A database failure leaves no partially saved order** (Verified `@Transactional` rollback in unit tests).
- [x] **Large quantities and decimal prices are calculated correctly** (Verified 10,000 units and arbitrary fractional prices).
- [x] **Items with `is_available = FALSE` do not appear** (Verified query filter and server rejection).
- [x] **Double-clicking Save does not create duplicate orders** (Verified button disabling during in-flight fetch).
- [x] **The page is readable on desktop and mobile widths** (Verified responsive `@media (max-width: 900px)`).
- [x] **If MySQL is stopped, the user sees a friendly error and the server does not crash** (Verified global error handlers).

## 13. Demonstration Flow

1. **Open the website**: Navigate to `http://localhost:8080` and demonstrate the menu items loaded dynamically from MySQL with category tags and prices.
2. **Demonstrate validation**: Try adding quantity `0`, `-5`, or `abc` to show immediate rejection.
3. **Select items & calculate bill**: Select 2 × Veg Sandwich and 1 × Tea. Click **Add to Order**, then click **Calculate Bill** to show the draft invoice preview matching ₹135.00.
4. **Save the order**: Click **Save Order**. Point out the button debounce preventing double-clicks. Note the green success banner and generated Order Number.
5. **View Order History**: Click **View Order History** to display the persistent order table with the new order listed at the top.
6. **Inspect details**: Double-click the row or click **View Details** to open the itemised breakdown in the modal.
7. **Database verification**: Query `orders` and `order_items` in MySQL to show the database records.

## 14. How to Run

1. **Prerequisites**:
   - Java 17+ installed (`java -version`).
   - MySQL 8.x installed and running.
   - Maven wrapper (`mvnw` / `mvnw.cmd`) is included in the project.

2. **Initialize Database**:
   - Execute the SQL script in MySQL to create `cafeteria_db`, the 3 tables, and sample data:
     ```powershell
     mysql -u root -p < sql/cafeteria_db.sql
     ```

3. **Configure Database Credentials**:
   - Copy `src/main/resources/application.properties.example` to `src/main/resources/application.properties` (or set environment variables `DB_USERNAME` and `DB_PASSWORD`):
     ```properties
     spring.datasource.url=jdbc:mysql://localhost:3306/cafeteria_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
     spring.datasource.username=root
     spring.datasource.password=your_mysql_password
     ```
   - Note: `application.properties` is git-ignored so credentials are never committed.

4. **Run Automated Tests**:
   ```powershell
   .\mvnw.cmd test
   ```

5. **Run the Application**:
   - On Windows:
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   - On Linux/macOS:
     ```bash
     ./mvnw spring-boot:run
     ```

6. **Access Application**:
   - Web GUI: `http://localhost:8080`
   - Health check: `http://localhost:8080/api/health`

## 15. Future Enhancements (out of scope for v1)

Cashier login, discounts and tax configuration, receipt printing, stock management, daily sales reports, PDF invoice export. **Version 1 is focused on menu selection, billing, saving orders and order history.**
