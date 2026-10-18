# Project Screenshots Guide

This folder is designated for screenshots demonstrating the working **Cafeteria Food Order & Billing System**. Capture and save the following screenshots into this directory (`screenshots/`) before final project submission.

---

## Required Screenshots Checklist

| File Name | Description | Key Elements to Capture |
|---|---|---|
| `01_menu_display.png` | **Menu Loaded from Database** | Browser at `http://localhost:8080` showing the top bar, live date/time clock, and the Menu table populated with all 5 items from MySQL with category badges and ₹ prices. |
| `02_bill_calculation.png` | **Bill Calculation & Draft Receipt** | Items added to the *Current Order* table (e.g. 2 × Veg Sandwich + 1 × Tea). The *Itemised Invoice* panel showing the calculated preview with grand total ₹135.00. |
| `03_saved_order_invoice.png` | **Saved Order & Official Invoice** | Green status banner: *"Order #... saved successfully to database! Grand Total: ₹135.00"*, with the official invoice rendered in the monospace receipt panel displaying the DB-generated Order No and timestamp. |
| `04_order_history_table.png` | **Order History View** | Click **View Order History** to display the history table showing past orders listed in descending date order with Order IDs, timestamps, amounts, and *Completed* tags. |
| `05_order_details_modal.png` | **Order Details Inspection** | Modal dialog opened by clicking **View Details** or double-clicking a history row, displaying the full itemised breakdown for that specific past order. |
| `06_mysql_database_tables.png` | **MySQL Database Verification** | Terminal or MySQL Workbench screenshot showing the queries and results from: <br> `SELECT * FROM orders;` and `SELECT * FROM order_items;` |
| `07_input_validation.png` | **Edge Case & Validation Feedback** | Warning banner displayed when submitting zero/negative/blank quantity or attempting to calculate/save an empty order. |

---

## How to Capture on Windows

1. **Full Window / Active Window**:
   - Press `Alt + PrintScreen` to copy the active browser window, then paste (`Ctrl + V`) into Paint or an image editor and save as PNG.
2. **Snipping Tool**:
   - Press `Windows + Shift + S`, select the region, and save directly into `screenshots/<filename>.png`.
3. **Responsive / Mobile View (Optional Bonus)**:
   - In Chrome / Edge, press `F12` -> toggle Device Toolbar (`Ctrl + Shift + M`) -> select iPhone / Pixel -> take screenshot (`Ctrl + Shift + P` -> "Capture screenshot") and save as `08_mobile_responsive.png`.
