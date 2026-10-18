# Demo Notes & Viva Preparation Guide

**Project**: Cafeteria Food Order & Billing System (Web Version)  
**Stack**: Java 17, Spring Boot 3.4.1, Spring Web, Spring JDBC (`JdbcTemplate`), MySQL 8, Maven Wrapper (`mvnw`), Vanilla HTML5/CSS3/JavaScript.

---

## 1. 3–5 Minute Demonstration Talking Script

### **0:00 – 0:45 | Introduction & Architecture**
> *"Good morning/afternoon. Today I am demonstrating the Cafeteria Food Order and Billing System. We designed this as a robust, full-stack web application built on Java 17 and Spring Boot 3 with Spring JDBC, backed by MySQL 8, and driven by a responsive vanilla JavaScript frontend without any heavy external frameworks.*  
> *The system follows a strict layered architecture: Controller layer for REST endpoints, Service layer for business logic and transactions, DTO layer for type-safe data transfer, and JdbcTemplate for secure database interactions."*

---

### **0:45 – 1:30 | Database-Backed Menu & Input Validation**
> *"When the page loads at `localhost:8080`, it makes an asynchronous `GET /api/menu` call. You can see our 5 menu items loaded directly from the MySQL database with real-time status and category tags. Items marked `is_available = FALSE` in MySQL are automatically excluded.*  
> *Notice our validation: if I try to enter zero, a negative number, a decimal, or text in the quantity input and click 'Add to Order', the system immediately rejects it with a clear warning message. The server also independently enforces positive whole-number validation."*

---

### **1:30 – 2:30 | Order Selection & Secure Bill Calculation**
> *"Now I will select 2 Veg Sandwiches and 1 Tea. When I click 'Add to Order', they appear in our Current Order table with quantities, unit prices, and line totals.*  
> *When I click 'Calculate Bill', the browser sends a POST request with item IDs and quantities to `/api/orders/calculate`. Notice that the frontend never sends item prices. The server looks up the authoritative price for each item directly from MySQL, calculates each line total and grand total using Java's `BigDecimal` with half-up rounding, and returns an itemised preview: 2 × ₹60.00 = ₹120.00, 1 × ₹15.00 = ₹15.00, Grand Total = ₹135.00."*

---

### **2:30 – 3:30 | Transactional Order Saving & Rollback**
> *"Next, I will click 'Save Order'. Notice the Save button is disabled immediately during the in-flight request to prevent duplicate orders from rapid accidental double-clicks.*  
> *On the server, `OrderService.saveOrder()` executes inside a `@Transactional` boundary: it re-validates prices from the database, inserts the order header into `orders`, captures the auto-generated primary key via Spring's `KeyHolder`, and performs a batch insert into `order_items`. If any line fails or foreign key is violated, the entire transaction rolls back completely—no orphan records can ever occur.*  
> *Upon success, the UI displays an official monospace receipt with the database-generated Order Number and timestamp, and clears the cart."*

---

### **3:30 – 4:30 | Order History & Order Details**
> *"Now I click 'View Order History'. The UI makes a `GET /api/orders` call and renders all past orders sorted newest first. The order we just placed (#4) is right at the top.*  
> *If I double-click any order row or click 'View Details', it calls `GET /api/orders/{id}` which joins `order_items` with `menu_items`. A modal popup opens showing the exact itemised invoice breakdown for that past transaction. I can also click 'Load to Main Invoice Panel' to transfer it back to the main receipt view.*  
> *Finally, if I stop and restart the server, all these orders remain completely intact because they are persistently stored in MySQL."*

---

## 2. Likely Viva Questions & Model Answers

### **Q1: Why did you use `BigDecimal` instead of `float` or `double` for money?**
> **Answer**: `float` and `double` are binary floating-point representations based on IEEE 754. They represent numbers as powers of 2, which cannot represent base-10 fractional decimals like `0.1` or `0.05` exactly, leading to rounding drift (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial and billing systems, this causes balance discrepancies and audit failures. `BigDecimal` provides arbitrary-precision signed decimal arithmetic and configurable rounding modes (`RoundingMode.HALF_UP`), ensuring 100% exact currency math.

---

### **Q2: Why is saving the order wrapped in `@Transactional`? What problem does it solve?**
> **Answer**: Saving an order requires two distinct database operations:
> 1. Inserting the order header into `orders` (generating the `order_id`).
> 2. Inserting multiple line items into `order_items` with that `order_id`.
> Without a transaction, if the server crashes or a database constraint fails during line item insertion, the order header would remain in `orders` without items (an orphaned, inconsistent record). With `@Transactional`, Spring manages the transaction lifecycle; if an unchecked exception occurs, the transaction automatically rolls back, preserving database atomicity and consistency (ACID).

---

### **Q3: Why did you use `PreparedStatement` and parameterized queries with `JdbcTemplate`?**
> **Answer**: Parameterized queries using `?` placeholders separate SQL code from user-supplied data:
> 1. **Security**: Completely prevents SQL Injection attacks because parameters are escaped and transmitted to the database engine separately from the SQL statement.
> 2. **Performance**: The database engine can compile and cache the query execution plan and reuse it across executions.
> 3. **Type Safety**: Automatically converts Java data types (such as `BigDecimal`, `Timestamp`, and `Integer`) to the corresponding SQL types.

---

### **Q4: Why are prices calculated on the server instead of trusting prices sent from the browser?**
> **Answer**: A fundamental web security rule is **"Never trust the client"**. A user can modify client-side JavaScript, alter network payloads in DevTools, or send forged curl/Postman requests (e.g., sending `price: 1.00` instead of `60.00`). If the server accepted client prices, anyone could purchase items at arbitrary prices. By requiring only `itemId` and reading authoritative prices from MySQL, the system is tamper-proof and resilient to concurrent price adjustments.

---

### **Q5: How does the application capture the generated `order_id` after inserting an order?**
> **Answer**: We use Spring's `GeneratedKeyHolder` combined with a `PreparedStatementCreator` configured with `Statement.RETURN_GENERATED_KEYS`:
> ```java
> KeyHolder keyHolder = new GeneratedKeyHolder();
> jdbcTemplate.update(connection -> {
>     PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
>     ps.setBigDecimal(1, totalAmount);
>     return ps;
> }, keyHolder);
> int orderId = keyHolder.getKey().intValue();
> ```
> This retrieves the auto-incremented primary key directly from the JDBC driver without needing a second `SELECT MAX(order_id)` query, preventing race conditions under concurrent load.

---

### **Q6: How did you prevent duplicate order submissions from accidental double-clicking?**
> **Answer**: Through client-side debouncing: the `Save Order` button is disabled (`btnSaveOrder.disabled = true;`) immediately when clicked, prior to the `fetch()` call. It is styled with `pointer-events: none;` and only re-enabled inside a `finally` block once the network request resolves. Additionally, if the order is empty, the button is disabled to prevent invalid requests.

---

### **Q7: What is the purpose of the Maven Wrapper (`mvnw` / `mvnw.cmd`)?**
> **Answer**: The Maven Wrapper guarantees reproducible builds by automatically downloading and using the exact tested Maven version (3.10.0) without requiring evaluators or developers to pre-install Maven on their system. They only need Java 17+ installed.

---

### **Q8: How does the application handle errors and 404s?**
> **Answer**: We implemented a centralized `@RestControllerAdvice` class (`GlobalExceptionHandler`). Any unhandled business exceptions (`ResourceNotFoundException`, `IllegalArgumentException`, `MethodArgumentNotValidException`, or `MethodArgumentTypeMismatchException`) are intercepted and converted into standardized JSON: `{"error": "message"}` with appropriate HTTP status codes (400, 404, or 500).

---

## 3. Step-by-Step Guide to Push Code to GitHub / Git

Follow these exact steps from your project terminal to push the project to GitHub.

### **Step 1: Check Git Status & Staging**
In PowerShell inside `D:\CafeteriaFoodOrderSystem`:
```powershell
# Check current git status
git status
```
Verify that `src/main/resources/application.properties` and `target/` are **NOT** listed. Only clean project files and `application.properties.example` should appear.

---

### **Step 2: Stage All Project Files**
```powershell
git add .
```

---

### **Step 3: Create the Initial Commit**
```powershell
git commit -m "Initial commit: Cafeteria Food Order & Billing System (Java 17, Spring Boot 3, MySQL 8)"
```

---

### **Step 4: Rename Branch to `main`**
```powershell
git branch -M main
```

---

### **Step 5: Create a Repository on GitHub**
1. Open [https://github.com/new](https://github.com/new) in your web browser.
2. Enter **Repository name**: `CafeteriaFoodOrderSystem`.
3. Set visibility to **Public** (or **Private** as desired).
4. **DO NOT** check "Add a README file", ".gitignore template", or "license" (we already have them in our project).
5. Click **Create repository**.
6. Copy the repository URL (e.g., `https://github.com/<your-username>/CafeteriaFoodOrderSystem.git`).

---

### **Step 6: Add Remote and Push**
Replace `<your-github-url>` with your copied repository URL:
```powershell
# Link your local repository to GitHub
git remote add origin https://github.com/<your-username>/CafeteriaFoodOrderSystem.git

# Push code to main branch
git push -u origin main
```

> **Note on Authentication**:
> When prompted for your password, GitHub requires a **Personal Access Token (Classic)** instead of your account password:
> 1. Go to GitHub -> Settings -> Developer settings -> Personal access tokens -> Tokens (classic).
> 2. Click **Generate new token (classic)** -> give note `cafeteria-upload`, check `repo` scope -> click **Generate token**.
> 3. Copy the token and paste it as your password in the terminal prompt.
