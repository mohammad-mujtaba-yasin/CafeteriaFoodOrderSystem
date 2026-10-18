/**
 * Cafeteria Food Order & Billing System - Frontend Logic
 * Vanilla JavaScript (No frameworks)
 */

document.addEventListener("DOMContentLoaded", () => {
    // -------------------------------------------------------------------------
    // 1. Live Date & Time Clock
    // -------------------------------------------------------------------------
    const clockElement = document.getElementById("liveClock");

    function updateClock() {
        const now = new Date();
        const options = {
            weekday: "short",
            year: "numeric",
            month: "short",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: true
        };
        if (clockElement) {
            clockElement.textContent = now.toLocaleString("en-IN", options);
        }
    }

    updateClock();
    setInterval(updateClock, 1000);

    // -------------------------------------------------------------------------
    // 2. Status Message Helper
    // -------------------------------------------------------------------------
    const statusBanner = document.getElementById("statusMessage");

    function showStatus(text, type = "info") {
        if (!statusBanner) return;
        statusBanner.textContent = text;
        statusBanner.className = "status-banner";
        if (type === "success") {
            statusBanner.classList.add("success-status");
        } else if (type === "warn" || type === "error") {
            statusBanner.classList.add("warn-status");
        } else {
            statusBanner.classList.add("info-status");
        }
    }

    // Helper for HTML escaping
    function escapeHtml(text) {
        if (!text) return "";
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    // Helper for category badge styling
    function getCategoryClass(category) {
        if (!category) return "tag-default";
        const cat = category.toLowerCase();
        if (cat.includes("snack")) return "tag-snacks";
        if (cat.includes("south")) return "tag-south";
        if (cat.includes("bev") || cat.includes("tea") || cat.includes("coffee")) return "tag-beverages";
        if (cat.includes("fast") || cat.includes("burger")) return "tag-fastfood";
        return "tag-default";
    }

    // -------------------------------------------------------------------------
    // 3. Current Order State Management
    // -------------------------------------------------------------------------
    // Array of { itemId: number, itemName: string, unitPrice: number, quantity: number }
    let currentOrder = [];

    const orderTableBody = document.getElementById("orderTableBody");
    const orderCountBadge = document.getElementById("orderCountBadge");
    const invoiceArea = document.getElementById("invoiceArea");

    function renderCurrentOrder() {
        if (!orderTableBody) return;
        orderTableBody.innerHTML = "";

        if (currentOrder.length === 0) {
            orderTableBody.innerHTML = `
                <tr id="emptyOrderRow">
                    <td colspan="5" class="text-center" style="padding: 1.5rem; color: var(--text-muted);">
                        No items in order. Select items from menu above and click "Add to Order".
                    </td>
                </tr>
            `;
            if (orderCountBadge) orderCountBadge.textContent = "0 Items";
            return;
        }

        let totalQty = 0;
        currentOrder.forEach((item, index) => {
            totalQty += item.quantity;
            const lineTotal = (item.unitPrice * item.quantity).toFixed(2);
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td class="text-center">
                    <input type="checkbox" class="order-item-select" data-index="${index}">
                </td>
                <td class="font-medium">${escapeHtml(item.itemName)}</td>
                <td class="text-center">${item.quantity}</td>
                <td class="price-cell">₹${item.unitPrice.toFixed(2)}</td>
                <td class="price-cell">₹${lineTotal}</td>
            `;
            orderTableBody.appendChild(tr);
        });

        if (orderCountBadge) {
            orderCountBadge.textContent = `${totalQty} Item(s)`;
        }
    }

    // Initialize current order table on load
    renderCurrentOrder();

    // -------------------------------------------------------------------------
    // 4. Database Menu Loading
    // -------------------------------------------------------------------------
    const menuTableBody = document.getElementById("menuTableBody");
    const menuCountBadge = document.getElementById("menuCountBadge");

    async function loadMenu() {
        try {
            const response = await fetch("/api/menu");
            if (!response.ok) {
                throw new Error(`Server returned HTTP ${response.status}`);
            }
            const items = await response.json();

            if (!menuTableBody) return;
            menuTableBody.innerHTML = "";

            if (!items || items.length === 0) {
                menuTableBody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center" style="padding: 1.5rem; color: var(--text-secondary);">
                            No available menu items found in the database.
                        </td>
                    </tr>
                `;
                if (menuCountBadge) menuCountBadge.textContent = "0 Items Available";
                showStatus("No available items in database.", "warn");
                return;
            }

            items.forEach(item => {
                const tr = document.createElement("tr");
                const priceFormatted = Number(item.price).toFixed(2);
                const tagClass = getCategoryClass(item.category);

                tr.innerHTML = `
                    <td class="text-center">
                        <input type="checkbox" class="item-select"
                               data-id="${item.itemId}"
                               data-item="${escapeHtml(item.itemName)}"
                               data-price="${priceFormatted}">
                    </td>
                    <td class="font-medium">${escapeHtml(item.itemName)}</td>
                    <td><span class="category-tag ${tagClass}">${escapeHtml(item.category || "General")}</span></td>
                    <td class="price-cell">₹${priceFormatted}</td>
                    <td>
                        <input type="number" class="qty-input" min="1" step="1" value="1">
                    </td>
                `;
                menuTableBody.appendChild(tr);
            });

            if (menuCountBadge) {
                menuCountBadge.textContent = `${items.length} Items Available`;
            }
            showStatus(`Successfully loaded ${items.length} menu items from database.`, "success");

        } catch (error) {
            console.error("Failed to load menu:", error);
            if (menuTableBody) {
                menuTableBody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center" style="padding: 1.5rem; color: var(--color-danger); font-weight: 500;">
                            &#9888; Failed to load menu from server. Please verify database connection and refresh.
                        </td>
                    </tr>
                `;
            }
            if (menuCountBadge) {
                menuCountBadge.textContent = "Error";
            }
            showStatus("Error loading menu from database. Check MySQL connection.", "warn");
        }
    }

    loadMenu();

    // Helper: Collects items either from currentOrder or directly from menu checkboxes
    function collectOrderItems() {
        if (currentOrder.length > 0) {
            return currentOrder;
        }

        // If currentOrder is empty, check if items are checked in the menu table
        const selectedCheckboxes = document.querySelectorAll(".item-select:checked");
        const gathered = [];

        selectedCheckboxes.forEach(cb => {
            const tr = cb.closest("tr");
            const itemId = parseInt(cb.getAttribute("data-id"), 10);
            const itemName = cb.getAttribute("data-item");
            const unitPrice = parseFloat(cb.getAttribute("data-price"));
            const qtyInput = tr.querySelector(".qty-input");
            const rawQty = qtyInput ? qtyInput.value.trim() : "1";

            if (!rawQty || !/^\d+$/.test(rawQty)) {
                return;
            }
            const quantity = parseInt(rawQty, 10);
            if (quantity <= 0) return;

            gathered.push({ itemId, itemName, unitPrice, quantity });
        });

        return gathered;
    }

    // -------------------------------------------------------------------------
    // 5. Order Management Actions: Add, Remove, Clear
    // -------------------------------------------------------------------------
    const btnAddOrder = document.getElementById("btnAddOrder");
    const btnRemoveItem = document.getElementById("btnRemoveItem");
    const btnClear = document.getElementById("btnClear");
    const btnCalculate = document.getElementById("btnCalculate");

    // Add selected items to current order with strict quantity validation
    if (btnAddOrder) {
        btnAddOrder.addEventListener("click", () => {
            const selectedCheckboxes = document.querySelectorAll(".item-select:checked");
            if (selectedCheckboxes.length === 0) {
                showStatus("Please select at least one item from the menu to add.", "warn");
                return;
            }

            let addedCount = 0;
            let hasValidationError = false;

            selectedCheckboxes.forEach(cb => {
                if (hasValidationError) return;

                const tr = cb.closest("tr");
                const itemId = parseInt(cb.getAttribute("data-id"), 10);
                const itemName = cb.getAttribute("data-item");
                const unitPrice = parseFloat(cb.getAttribute("data-price"));
                const qtyInput = tr.querySelector(".qty-input");
                const rawQty = qtyInput ? qtyInput.value.trim() : "";

                // Strict quantity validation rules:
                // Non-empty, whole positive integer >= 1, no decimals or negative numbers
                if (!rawQty) {
                    showStatus(`Quantity cannot be blank for "${itemName}".`, "warn");
                    hasValidationError = true;
                    return;
                }
                if (!/^\d+$/.test(rawQty)) {
                    showStatus(`Quantity must be a positive whole number for "${itemName}". Decimals and non-numeric values are not allowed.`, "warn");
                    hasValidationError = true;
                    return;
                }

                const quantity = parseInt(rawQty, 10);
                if (quantity <= 0) {
                    showStatus(`Quantity must be greater than 0 for "${itemName}".`, "warn");
                    hasValidationError = true;
                    return;
                }

                // Check if already in order; if so, update quantity
                const existing = currentOrder.find(item => item.itemId === itemId);
                if (existing) {
                    existing.quantity += quantity;
                } else {
                    currentOrder.push({
                        itemId: itemId,
                        itemName: itemName,
                        unitPrice: unitPrice,
                        quantity: quantity
                    });
                }

                // Reset selection and quantity in menu table
                cb.checked = false;
                if (qtyInput) qtyInput.value = "1";
                addedCount++;
            });

            if (!hasValidationError && addedCount > 0) {
                renderCurrentOrder();
                showStatus(`Added item(s) to order. Click "Calculate Bill" or "Save Order".`, "success");
            }
        });
    }

    // Remove checked items from current order
    if (btnRemoveItem) {
        btnRemoveItem.addEventListener("click", () => {
            const checkedOrderItems = document.querySelectorAll(".order-item-select:checked");
            if (checkedOrderItems.length === 0) {
                showStatus("Please select at least one item from the Current Order table to remove.", "warn");
                return;
            }

            // Collect indices in descending order to splice cleanly
            const indicesToRemove = Array.from(checkedOrderItems)
                .map(cb => parseInt(cb.getAttribute("data-index"), 10))
                .sort((a, b) => b - a);

            indicesToRemove.forEach(index => {
                currentOrder.splice(index, 1);
            });

            renderCurrentOrder();
            showStatus(`Removed ${indicesToRemove.length} item(s) from current order.`, "info");
        });
    }

    // Clear entire current order and invoice
    if (btnClear) {
        btnClear.addEventListener("click", () => {
            currentOrder = [];
            renderCurrentOrder();

            // Uncheck menu items and reset quantities
            document.querySelectorAll(".item-select").forEach(cb => cb.checked = false);
            document.querySelectorAll(".qty-input").forEach(input => input.value = "1");

            // Reset invoice area
            if (invoiceArea) {
                invoiceArea.textContent = `================================================
          CAFETERIA FOOD ORDER & BILLING
================================================
Invoice cleared.
Add items to the order and click "Calculate Bill".
================================================`;
            }
            showStatus("Current order and menu selections cleared.", "info");
        });
    }

    // -------------------------------------------------------------------------
    // 6. Calculate Bill (Calls POST /api/orders/calculate)
    // -------------------------------------------------------------------------
    if (btnCalculate) {
        btnCalculate.addEventListener("click", async () => {
            const itemsToCalculate = collectOrderItems();

            if (!itemsToCalculate || itemsToCalculate.length === 0) {
                showStatus("An empty order cannot be calculated. Please select items from the menu first.", "warn");
                return;
            }

            // Sync currentOrder if items were collected from menu table
            if (currentOrder.length === 0 && itemsToCalculate.length > 0) {
                currentOrder = itemsToCalculate;
                renderCurrentOrder();
            }

            const payload = {
                items: currentOrder.map(item => ({
                    itemId: item.itemId,
                    quantity: item.quantity
                }))
            };

            try {
                showStatus("Calculating bill from server...", "info");
                const response = await fetch("/api/orders/calculate", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(payload)
                });

                const data = await response.json();

                if (!response.ok) {
                    const errorMsg = data && data.error ? data.error : "Failed to calculate bill.";
                    showStatus(`Calculation error: ${errorMsg}`, "warn");
                    if (invoiceArea) {
                        invoiceArea.textContent = `================================================
          CALCULATION ERROR (HTTP ${response.status})
================================================
${errorMsg}
================================================`;
                    }
                    return;
                }

                // Render itemised invoice from server response
                let lines = [];
                const now = new Date().toLocaleString("en-IN");

                lines.push("================================================");
                lines.push("          CAFETERIA FOOD ORDER & BILLING");
                lines.push("================================================");
                lines.push(`Status   : Calculated Draft`);
                lines.push(`Date/Time: ${now}`);
                lines.push("------------------------------------------------");
                lines.push("Item                Qty    Unit (₹)    Total (₹)");
                lines.push("------------------------------------------------");

                data.lines.forEach(line => {
                    const name = line.itemName.padEnd(18, " ").substring(0, 18);
                    const qty = String(line.quantity).padStart(4, " ");
                    const unitPrice = Number(line.unitPrice).toFixed(2).padStart(11, " ");
                    const lineTotal = Number(line.lineTotal).toFixed(2).padStart(12, " ");
                    lines.push(`${name} ${qty} ${unitPrice} ${lineTotal}`);
                });

                const grandTotal = Number(data.grandTotal).toFixed(2);
                lines.push("------------------------------------------------");
                lines.push(`Grand Total:                             ₹${grandTotal}`);
                lines.push("================================================");
                lines.push("       Thank you for dining with us!");
                lines.push("================================================");

                if (invoiceArea) {
                    invoiceArea.textContent = lines.join("\n");
                }

                showStatus(`Bill calculated successfully! Grand Total: ₹${grandTotal}`, "success");

            } catch (err) {
                console.error("Calculation request failed:", err);
                showStatus("Network or server connection error while calculating bill.", "warn");
            }
        });
    }

    // Helper to format itemised invoice text for pre blocks
    function formatInvoice(orderId, orderDate, lines, grandTotal, statusTitle = "Saved in DB") {
        let output = [];
        const dateStr = orderDate ? String(orderDate).replace("T", " ") : new Date().toLocaleString("en-IN");

        output.push("================================================");
        output.push("          CAFETERIA FOOD ORDER & BILLING");
        output.push("================================================");
        output.push(`Order No : #${orderId}`);
        output.push(`Status   : ${statusTitle}`);
        output.push(`Date/Time: ${dateStr}`);
        output.push("------------------------------------------------");
        output.push("Item                Qty    Unit (₹)    Total (₹)");
        output.push("------------------------------------------------");

        lines.forEach(line => {
            const name = (line.itemName || "").padEnd(18, " ").substring(0, 18);
            const qty = String(line.quantity).padStart(4, " ");
            const unitPrice = Number(line.unitPrice).toFixed(2).padStart(11, " ");
            const lineTotal = Number(line.lineTotal).toFixed(2).padStart(12, " ");
            output.push(`${name} ${qty} ${unitPrice} ${lineTotal}`);
        });

        const totalStr = Number(grandTotal).toFixed(2);
        output.push("------------------------------------------------");
        output.push(`Grand Total:                             ₹${totalStr}`);
        output.push("================================================");
        output.push("       Thank you for dining with us!");
        output.push("================================================");

        return output.join("\n");
    }

    // -------------------------------------------------------------------------
    // 7. Navigation: Toggle Order View and Order History View
    // -------------------------------------------------------------------------
    const orderView = document.getElementById("orderView");
    const historyView = document.getElementById("historyView");
    const btnViewHistory = document.getElementById("btnViewHistory");
    const btnBackToOrder = document.getElementById("btnBackToOrder");

    if (btnViewHistory) {
        btnViewHistory.addEventListener("click", () => {
            orderView.classList.add("hidden");
            historyView.classList.remove("hidden");
            loadOrderHistory();
        });
    }

    if (btnBackToOrder) {
        btnBackToOrder.addEventListener("click", () => {
            historyView.classList.add("hidden");
            orderView.classList.remove("hidden");
            showStatus("Back to Order screen.", "info");
        });
    }

    // -------------------------------------------------------------------------
    // 8. Bottom Panel Buttons (Save Order & Exit)
    // -------------------------------------------------------------------------
    const btnSaveOrder = document.getElementById("btnSaveOrder");
    const btnExit = document.getElementById("btnExit");

    if (btnSaveOrder) {
        btnSaveOrder.addEventListener("click", async () => {
            const itemsToSave = collectOrderItems();

            if (!itemsToSave || itemsToSave.length === 0) {
                showStatus("Cannot save an empty order. Please select items from the menu first.", "warn");
                return;
            }

            // Sync currentOrder if items were collected from menu table
            if (currentOrder.length === 0 && itemsToSave.length > 0) {
                currentOrder = itemsToSave;
                renderCurrentOrder();
            }

            // Disable button ONLY during network call to prevent duplicate submissions
            btnSaveOrder.disabled = true;
            showStatus("Saving order to database...", "info");

            const payload = {
                items: currentOrder.map(item => ({
                    itemId: item.itemId,
                    quantity: item.quantity
                }))
            };

            try {
                const response = await fetch("/api/orders", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(payload)
                });

                const data = await response.json();

                if (!response.ok) {
                    const errorMsg = data && data.error ? data.error : "Failed to save order.";
                    showStatus(`Order save failed: ${errorMsg}`, "warn");
                    if (invoiceArea) {
                        invoiceArea.textContent = `================================================
          ORDER SAVE FAILED (HTTP ${response.status})
================================================
Error: ${errorMsg}
================================================`;
                    }
                    return;
                }

                // Render official itemised invoice upon successful save
                const invoiceText = formatInvoice(data.orderId, data.orderDate, data.lines, data.grandTotal, "Saved (Completed)");
                if (invoiceArea) {
                    invoiceArea.textContent = invoiceText;
                }

                const grandTotal = Number(data.grandTotal).toFixed(2);
                showStatus(`Order #${data.orderId} saved successfully to database! Grand Total: ₹${grandTotal}`, "success");

                // Clear current order after successful save
                currentOrder = [];
                renderCurrentOrder();

                // Clear any checked items in menu table
                document.querySelectorAll(".item-select").forEach(cb => cb.checked = false);
                document.querySelectorAll(".qty-input").forEach(input => input.value = "1");

            } catch (err) {
                console.error("Save order request failed:", err);
                showStatus("Network or server connection error while saving order.", "warn");
            } finally {
                // Always re-enable button after request completes
                btnSaveOrder.disabled = false;
            }
        });
    }

    if (btnExit) {
        btnExit.addEventListener("click", () => {
            if (confirm("Are you sure you want to exit the application?")) {
                showStatus("Session closed. You can close this browser tab.", "warn");
            }
        });
    }

    // -------------------------------------------------------------------------
    // 9. Order History Loading & Details Viewing
    // -------------------------------------------------------------------------
    const historyTableBody = document.getElementById("historyTableBody");
    const btnRefreshHistory = document.getElementById("btnRefreshHistory");
    const orderDetailsModal = document.getElementById("orderDetailsModal");
    const modalInvoiceArea = document.getElementById("modalInvoiceArea");
    const modalTitle = document.getElementById("modalTitle");
    const btnModalClose = document.getElementById("btnModalClose");
    const btnModalCloseSecondary = document.getElementById("btnModalCloseSecondary");
    const btnModalLoadToMain = document.getElementById("btnModalLoadToMain");

    let activeSelectedOrder = null;

    async function loadOrderHistory() {
        if (!historyTableBody) return;
        historyTableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center" style="padding: 1.5rem; color: var(--text-secondary);">
                    Loading order history from database...
                </td>
            </tr>
        `;

        try {
            const response = await fetch("/api/orders");
            if (!response.ok) {
                throw new Error(`Server returned HTTP ${response.status}`);
            }

            const orders = await response.json();
            historyTableBody.innerHTML = "";

            if (!orders || orders.length === 0) {
                historyTableBody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center" style="padding: 2rem; color: var(--text-secondary);">
                            No orders found in history. Place and save your first order to see it here!
                        </td>
                    </tr>
                `;
                showStatus("Order history is currently empty.", "info");
                return;
            }

            orders.forEach(order => {
                const tr = document.createElement("tr");
                tr.className = "history-row";
                tr.setAttribute("data-order-id", order.orderId);
                tr.setAttribute("title", "Double-click to view itemised invoice");

                const formattedDate = order.orderDate ? String(order.orderDate).replace("T", " ") : "N/A";
                const formattedTotal = Number(order.totalAmount).toFixed(2);

                tr.innerHTML = `
                    <td class="font-medium">#${order.orderId}</td>
                    <td>${formattedDate}</td>
                    <td class="price-cell">₹${formattedTotal}</td>
                    <td><span class="status-tag status-completed">Completed</span></td>
                    <td class="text-center">
                        <button type="button" class="btn btn-sm btn-outline btn-view-details" data-order-id="${order.orderId}">View Details</button>
                    </td>
                `;

                // Double click row handler: open details
                tr.addEventListener("dblclick", () => {
                    viewOrderDetails(order.orderId);
                });

                // View Details button handler
                const btnDetails = tr.querySelector(".btn-view-details");
                if (btnDetails) {
                    btnDetails.addEventListener("click", (e) => {
                        e.stopPropagation();
                        viewOrderDetails(order.orderId);
                    });
                }

                historyTableBody.appendChild(tr);
            });

            showStatus(`Loaded ${orders.length} order(s) from history.`, "success");

        } catch (err) {
            console.error("Failed to load order history:", err);
            historyTableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center" style="padding: 1.5rem; color: var(--color-danger); font-weight: 500;">
                        &#9888; Failed to load order history from server. Check database connection.
                    </td>
                </tr>
            `;
            showStatus("Failed to load order history from server.", "warn");
        }
    }

    async function viewOrderDetails(orderId) {
        showStatus(`Fetching details for Order #${orderId}...`, "info");

        try {
            const response = await fetch(`/api/orders/${orderId}`);
            const data = await response.json();

            if (!response.ok) {
                const msg = data && data.error ? data.error : "Failed to load order details.";
                showStatus(`Error: ${msg}`, "warn");
                return;
            }

            activeSelectedOrder = data;

            const invoiceText = formatInvoice(
                data.orderId,
                data.orderDate,
                data.lines,
                data.grandTotal,
                "Saved in DB"
            );

            // Populate both modal and main invoice area
            if (modalInvoiceArea) {
                modalInvoiceArea.textContent = invoiceText;
            }
            if (modalTitle) {
                modalTitle.textContent = `Order Details: #${data.orderId}`;
            }

            // Also load directly into main invoice area
            if (invoiceArea) {
                invoiceArea.textContent = invoiceText;
            }

            // Open modal
            if (orderDetailsModal) {
                orderDetailsModal.classList.remove("hidden");
            }

            showStatus(`Viewing details for Order #${orderId}.`, "success");

        } catch (err) {
            console.error("Failed to load order details:", err);
            showStatus(`Network or server error loading details for Order #${orderId}.`, "warn");
        }
    }

    function closeModal() {
        if (orderDetailsModal) {
            orderDetailsModal.classList.add("hidden");
        }
    }

    if (btnModalClose) btnModalClose.addEventListener("click", closeModal);
    if (btnModalCloseSecondary) btnModalCloseSecondary.addEventListener("click", closeModal);
    if (orderDetailsModal) {
        orderDetailsModal.addEventListener("click", (e) => {
            if (e.target === orderDetailsModal) closeModal();
        });
    }

    if (btnModalLoadToMain) {
        btnModalLoadToMain.addEventListener("click", () => {
            closeModal();
            // Switch back to Order View where invoice panel is displayed
            historyView.classList.add("hidden");
            orderView.classList.remove("hidden");
            showStatus(`Loaded Order #${activeSelectedOrder ? activeSelectedOrder.orderId : ''} into Invoice Panel.`, "info");
        });
    }

    if (btnRefreshHistory) {
        btnRefreshHistory.addEventListener("click", () => {
            loadOrderHistory();
        });
    }
});
