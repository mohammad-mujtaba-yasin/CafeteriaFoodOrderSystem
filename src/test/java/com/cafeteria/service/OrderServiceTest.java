package com.cafeteria.service;

import com.cafeteria.dto.BillResponse;
import com.cafeteria.dto.OrderItemRequest;
import com.cafeteria.dto.OrderRequest;
import com.cafeteria.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private FakeJdbcTemplate fakeJdbcTemplate;
    private OrderService orderService;

    static class FakeJdbcTemplate extends JdbcTemplate {
        private final Map<Integer, Map<String, Object>> itemsDb = new HashMap<>();
        public List<Object[]> lastBatchArgs = new ArrayList<>();
        public boolean failBatchUpdate = false;
        private final int nextOrderId = 101;
        public final List<Map<String, Object>> ordersDb = new ArrayList<>();
        public final Map<Integer, List<Map<String, Object>>> orderItemsDb = new HashMap<>();

        void addItem(int id, String name, BigDecimal price, boolean isAvailable) {
            Map<String, Object> row = new HashMap<>();
            row.put("item_name", name);
            row.put("price", price);
            row.put("is_available", isAvailable);
            itemsDb.put(id, row);
        }

        void addOrder(int id, Timestamp orderDate, BigDecimal totalAmount) {
            Map<String, Object> row = new HashMap<>();
            row.put("order_id", id);
            row.put("order_date", orderDate);
            row.put("total_amount", totalAmount);
            ordersDb.add(row);
        }

        void addOrderItem(int orderId, int itemId, String itemName, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {
            Map<String, Object> row = new HashMap<>();
            row.put("order_id", orderId);
            row.put("item_id", itemId);
            row.put("item_name", itemName);
            row.put("quantity", qty);
            row.put("unit_price", unitPrice);
            row.put("line_total", lineTotal);
            orderItemsDb.computeIfAbsent(orderId, k -> new ArrayList<>()).add(row);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            if (sql.contains("FROM orders")) {
                return new ArrayList<>(ordersDb);
            }
            return Collections.emptyList();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("SELECT order_id, order_date, total_amount FROM orders WHERE order_id = ?")) {
                if (args != null && args.length > 0 && args[0] instanceof Integer) {
                    Integer id = (Integer) args[0];
                    for (Map<String, Object> o : ordersDb) {
                        if (id.equals(o.get("order_id"))) {
                            return List.of(o);
                        }
                    }
                }
                return Collections.emptyList();
            }

            if (sql.contains("FROM order_items")) {
                if (args != null && args.length > 0 && args[0] instanceof Integer) {
                    Integer orderId = (Integer) args[0];
                    return orderItemsDb.getOrDefault(orderId, Collections.emptyList());
                }
                return Collections.emptyList();
            }

            if (args != null && args.length > 0 && args[0] instanceof Integer) {
                Integer id = (Integer) args[0];
                Map<String, Object> row = itemsDb.get(id);
                if (row != null) {
                    return List.of(row);
                }
            }
            return Collections.emptyList();
        }

        @Override
        public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
            generatedKeyHolder.getKeyList().add(Collections.singletonMap("order_id", nextOrderId));
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("SELECT order_date")) {
                return (T) Timestamp.valueOf("2026-10-18 19:30:00");
            }
            return super.queryForObject(sql, requiredType, args);
        }

        @Override
        public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
            if (failBatchUpdate) {
                throw new DataIntegrityViolationException("Simulated foreign key failure in order_items batch insert");
            }
            this.lastBatchArgs = new ArrayList<>(batchArgs);
            int[] result = new int[batchArgs.size()];
            java.util.Arrays.fill(result, 1);
            return result;
        }
    }

    @BeforeEach
    void setUp() {
        fakeJdbcTemplate = new FakeJdbcTemplate();
        orderService = new OrderService(fakeJdbcTemplate);
    }

    @Test
    @DisplayName("Verify example from README: 2 x Veg Sandwich (60) + 1 x Tea (15) = 135.00")
    void testCalculateBill_StandardExample_VegSandwichAndTea() {
        // Arrange
        fakeJdbcTemplate.addItem(1, "Veg Sandwich", new BigDecimal("60.00"), true);
        fakeJdbcTemplate.addItem(3, "Tea", new BigDecimal("15.00"), true);

        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(1, 2),
                new OrderItemRequest(3, 1)
        ));

        // Act
        BillResponse response = orderService.calculateBill(request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getLines().size());

        assertEquals("Veg Sandwich", response.getLines().get(0).getItemName());
        assertEquals(2, response.getLines().get(0).getQuantity());
        assertEquals(new BigDecimal("60.00"), response.getLines().get(0).getUnitPrice());
        assertEquals(new BigDecimal("120.00"), response.getLines().get(0).getLineTotal());

        assertEquals("Tea", response.getLines().get(1).getItemName());
        assertEquals(1, response.getLines().get(1).getQuantity());
        assertEquals(new BigDecimal("15.00"), response.getLines().get(1).getUnitPrice());
        assertEquals(new BigDecimal("15.00"), response.getLines().get(1).getLineTotal());

        assertEquals(new BigDecimal("135.00"), response.getGrandTotal());
    }

    @Test
    @DisplayName("Verify calculation with non-trivial decimal prices")
    void testCalculateBill_DecimalPrices() {
        // Arrange: 3 x 90.55 = 271.65, 2 x 15.45 = 30.90 => total = 302.55
        fakeJdbcTemplate.addItem(2, "Masala Dosa Special", new BigDecimal("90.55"), true);
        fakeJdbcTemplate.addItem(4, "Special Coffee", new BigDecimal("15.45"), true);

        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(2, 3),
                new OrderItemRequest(4, 2)
        ));

        // Act
        BillResponse response = orderService.calculateBill(request);

        // Assert
        assertEquals(new BigDecimal("271.65"), response.getLines().get(0).getLineTotal());
        assertEquals(new BigDecimal("30.90"), response.getLines().get(1).getLineTotal());
        assertEquals(new BigDecimal("302.55"), response.getGrandTotal());
    }

    @Test
    @DisplayName("Verify calculation with large quantities")
    void testCalculateBill_LargeQuantities() {
        // Arrange: 10,000 x 60.00 = 600,000.00
        fakeJdbcTemplate.addItem(1, "Veg Sandwich", new BigDecimal("60.00"), true);

        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(1, 10000)
        ));

        // Act
        BillResponse response = orderService.calculateBill(request);

        // Assert
        assertEquals(new BigDecimal("600000.00"), response.getGrandTotal());
    }

    @Test
    @DisplayName("Empty order must throw IllegalArgumentException")
    void testCalculateBill_EmptyOrder_ThrowsException() {
        OrderRequest emptyRequest = new OrderRequest(Collections.emptyList());
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.calculateBill(emptyRequest));
        assertTrue(exception.getMessage().contains("empty order"));
    }

    @Test
    @DisplayName("Zero or negative quantity must throw IllegalArgumentException")
    void testCalculateBill_ZeroOrNegativeQuantity_ThrowsException() {
        OrderRequest requestZero = new OrderRequest(List.of(new OrderItemRequest(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> orderService.calculateBill(requestZero));

        OrderRequest requestNeg = new OrderRequest(List.of(new OrderItemRequest(1, -5)));
        assertThrows(IllegalArgumentException.class, () -> orderService.calculateBill(requestNeg));
    }

    @Test
    @DisplayName("Unavailable menu item must throw IllegalArgumentException")
    void testCalculateBill_UnavailableItem_ThrowsException() {
        fakeJdbcTemplate.addItem(3, "Tea", new BigDecimal("15.00"), false);

        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(3, 1)));
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.calculateBill(request));
        assertTrue(exception.getMessage().contains("currently unavailable"));
    }

    @Test
    @DisplayName("Non-existent item ID must throw IllegalArgumentException")
    void testCalculateBill_NonExistentItem_ThrowsException() {
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(999, 1)));
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.calculateBill(request));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Save order successfully with transaction and batch insert")
    void testSaveOrder_Success() {
        fakeJdbcTemplate.addItem(1, "Veg Sandwich", new BigDecimal("60.00"), true);
        fakeJdbcTemplate.addItem(3, "Tea", new BigDecimal("15.00"), true);

        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(1, 2),
                new OrderItemRequest(3, 1)
        ));

        OrderResponse response = orderService.saveOrder(request);

        assertNotNull(response);
        assertEquals(101, response.getOrderId());
        assertEquals(new BigDecimal("135.00"), response.getGrandTotal());
        assertEquals(2, response.getLines().size());

        // Verify batch arguments passed to order_items
        assertEquals(2, fakeJdbcTemplate.lastBatchArgs.size());
        Object[] line1Args = fakeJdbcTemplate.lastBatchArgs.get(0);
        assertEquals(101, line1Args[0]); // order_id
        assertEquals(1, line1Args[1]);   // item_id
        assertEquals(2, line1Args[2]);   // quantity
        assertEquals(new BigDecimal("60.00"), line1Args[3]); // unit_price
        assertEquals(new BigDecimal("120.00"), line1Args[4]); // line_total
    }

    @Test
    @DisplayName("Simulate batch insert failure triggering rollback")
    void testSaveOrder_BatchInsertFailure_ThrowsException() {
        fakeJdbcTemplate.addItem(1, "Veg Sandwich", new BigDecimal("60.00"), true);
        fakeJdbcTemplate.failBatchUpdate = true;

        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(1, 2)));

        assertThrows(DataIntegrityViolationException.class, () -> orderService.saveOrder(request));
    }

    @Test
    @DisplayName("Load order history returns orders in descending order")
    void testGetOrderHistory_ReturnsList() {
        fakeJdbcTemplate.addOrder(102, Timestamp.valueOf("2026-10-18 20:00:00"), new BigDecimal("250.00"));
        fakeJdbcTemplate.addOrder(101, Timestamp.valueOf("2026-10-18 19:30:00"), new BigDecimal("135.00"));

        List<com.cafeteria.dto.OrderSummary> history = orderService.getOrderHistory();

        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals(102, history.get(0).getOrderId());
        assertEquals(new BigDecimal("250.00"), history.get(0).getTotalAmount());
        assertEquals(101, history.get(1).getOrderId());
        assertEquals(new BigDecimal("135.00"), history.get(1).getTotalAmount());
    }

    @Test
    @DisplayName("Get order details returns complete header and line items")
    void testGetOrderDetails_Success() {
        fakeJdbcTemplate.addOrder(101, Timestamp.valueOf("2026-10-18 19:30:00"), new BigDecimal("135.00"));
        fakeJdbcTemplate.addOrderItem(101, 1, "Veg Sandwich", 2, new BigDecimal("60.00"), new BigDecimal("120.00"));
        fakeJdbcTemplate.addOrderItem(101, 3, "Tea", 1, new BigDecimal("15.00"), new BigDecimal("15.00"));

        OrderResponse details = orderService.getOrderDetails(101);

        assertNotNull(details);
        assertEquals(101, details.getOrderId());
        assertEquals(new BigDecimal("135.00"), details.getGrandTotal());
        assertEquals(2, details.getLines().size());
        assertEquals("Veg Sandwich", details.getLines().get(0).getItemName());
        assertEquals(2, details.getLines().get(0).getQuantity());
        assertEquals(new BigDecimal("120.00"), details.getLines().get(0).getLineTotal());
    }

    @Test
    @DisplayName("Get order details with non-existent ID throws ResourceNotFoundException")
    void testGetOrderDetails_NotFound_ThrowsResourceNotFoundException() {
        assertThrows(com.cafeteria.exception.ResourceNotFoundException.class, () ->
                orderService.getOrderDetails(9999));
    }
}
