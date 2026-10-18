package com.cafeteria.service;

import com.cafeteria.dto.BillLine;
import com.cafeteria.dto.BillResponse;
import com.cafeteria.dto.OrderItemRequest;
import com.cafeteria.dto.OrderRequest;
import com.cafeteria.dto.OrderResponse;
import com.cafeteria.model.MenuItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;

    public OrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MenuItem> loadMenu() {
        String sql = "SELECT item_id, item_name, category, price FROM menu_items WHERE is_available = TRUE ORDER BY item_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MenuItem(
                rs.getInt("item_id"),
                rs.getString("item_name"),
                rs.getString("category"),
                rs.getBigDecimal("price")
        ));
    }

    public BillResponse calculateBill(OrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("An empty order cannot be calculated");
        }

        // Aggregate quantities for duplicate item selections
        Map<Integer, Integer> aggregatedItems = new LinkedHashMap<>();
        for (OrderItemRequest item : request.getItems()) {
            if (item.getItemId() == null) {
                throw new IllegalArgumentException("Item ID is required");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive whole number greater than 0");
            }
            aggregatedItems.merge(item.getItemId(), item.getQuantity(), Integer::sum);
        }

        List<BillLine> lines = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        String itemSql = "SELECT item_name, price, is_available FROM menu_items WHERE item_id = ?";

        for (Map.Entry<Integer, Integer> entry : aggregatedItems.entrySet()) {
            Integer itemId = entry.getKey();
            Integer quantity = entry.getValue();

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(itemSql, itemId);

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Menu item with ID " + itemId + " does not exist");
            }

            Map<String, Object> row = rows.get(0);
            String itemName = (String) row.get("item_name");

            Object priceObj = row.get("price");
            BigDecimal unitPrice;
            if (priceObj instanceof BigDecimal) {
                unitPrice = (BigDecimal) priceObj;
            } else if (priceObj != null) {
                unitPrice = new BigDecimal(priceObj.toString());
            } else {
                throw new IllegalArgumentException("Price for item " + itemName + " is invalid");
            }

            Object availObj = row.get("is_available");
            boolean isAvailable = false;
            if (availObj instanceof Boolean) {
                isAvailable = (Boolean) availObj;
            } else if (availObj instanceof Number) {
                isAvailable = ((Number) availObj).intValue() == 1;
            }

            if (!isAvailable) {
                throw new IllegalArgumentException("Menu item '" + itemName + "' is currently unavailable");
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            BillLine line = new BillLine(itemId, itemName, quantity, unitPrice.setScale(2, RoundingMode.HALF_UP), lineTotal);

            lines.add(line);
            grandTotal = grandTotal.add(lineTotal);
        }

        return new BillResponse(lines, grandTotal.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public OrderResponse saveOrder(OrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("An empty order cannot be saved");
        }

        // 1. Recalculate bill from database prices and validate all items
        BillResponse bill = calculateBill(request);

        // 2. Insert into orders table and capture generated order_id using KeyHolder
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String orderInsertSql = "INSERT INTO orders (total_amount) VALUES (?)";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(orderInsertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setBigDecimal(1, bill.getGrandTotal());
            return ps;
        }, keyHolder);

        Number generatedKey = null;
        try {
            generatedKey = keyHolder.getKey();
        } catch (Exception ignored) {
        }
        if (generatedKey == null && keyHolder.getKeyList() != null && !keyHolder.getKeyList().isEmpty()) {
            Map<String, Object> map = keyHolder.getKeyList().get(0);
            if (!map.isEmpty()) {
                Object val = map.values().iterator().next();
                if (val instanceof Number) {
                    generatedKey = (Number) val;
                }
            }
        }
        if (generatedKey == null) {
            throw new IllegalStateException("Failed to retrieve generated order ID from database");
        }
        int orderId = generatedKey.intValue();

        // Retrieve created timestamp from database record
        Timestamp dbTimestamp = jdbcTemplate.queryForObject(
                "SELECT order_date FROM orders WHERE order_id = ?",
                Timestamp.class,
                orderId
        );
        LocalDateTime orderDate = (dbTimestamp != null) ? dbTimestamp.toLocalDateTime() : LocalDateTime.now();

        // 3. Batch insert all order items into order_items table
        String itemInsertSql = "INSERT INTO order_items (order_id, item_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";
        List<Object[]> batchParams = new ArrayList<>();
        for (BillLine line : bill.getLines()) {
            batchParams.add(new Object[] {
                    orderId,
                    line.getItemId(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getLineTotal()
            });
        }

        jdbcTemplate.batchUpdate(itemInsertSql, batchParams);

        return new OrderResponse(orderId, orderDate, bill.getLines(), bill.getGrandTotal());
    }

    public List<com.cafeteria.dto.OrderSummary> getOrderHistory() {
        String sql = "SELECT order_id, order_date, total_amount FROM orders ORDER BY order_date DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<com.cafeteria.dto.OrderSummary> history = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Integer orderId = ((Number) row.get("order_id")).intValue();
            Object dateObj = row.get("order_date");
            LocalDateTime orderDate = null;
            if (dateObj instanceof Timestamp) {
                orderDate = ((Timestamp) dateObj).toLocalDateTime();
            } else if (dateObj instanceof LocalDateTime) {
                orderDate = (LocalDateTime) dateObj;
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            Object totalObj = row.get("total_amount");
            if (totalObj instanceof BigDecimal) {
                totalAmount = (BigDecimal) totalObj;
            } else if (totalObj != null) {
                totalAmount = new BigDecimal(totalObj.toString());
            }

            history.add(new com.cafeteria.dto.OrderSummary(
                    orderId,
                    orderDate,
                    totalAmount.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return history;
    }

    public OrderResponse getOrderDetails(int orderId) {
        String headerSql = "SELECT order_id, order_date, total_amount FROM orders WHERE order_id = ?";
        List<Map<String, Object>> headerRows = jdbcTemplate.queryForList(headerSql, orderId);

        if (headerRows.isEmpty()) {
            throw new com.cafeteria.exception.ResourceNotFoundException("Order with ID " + orderId + " not found");
        }

        Map<String, Object> header = headerRows.get(0);
        Integer id = ((Number) header.get("order_id")).intValue();
        Object dateObj = header.get("order_date");
        LocalDateTime orderDate = null;
        if (dateObj instanceof Timestamp) {
            orderDate = ((Timestamp) dateObj).toLocalDateTime();
        } else if (dateObj instanceof LocalDateTime) {
            orderDate = (LocalDateTime) dateObj;
        } else {
            orderDate = LocalDateTime.now();
        }

        Object totalObj = header.get("total_amount");
        BigDecimal grandTotal = BigDecimal.ZERO;
        if (totalObj instanceof BigDecimal) {
            grandTotal = (BigDecimal) totalObj;
        } else if (totalObj != null) {
            grandTotal = new BigDecimal(totalObj.toString());
        }
        grandTotal = grandTotal.setScale(2, RoundingMode.HALF_UP);

        String linesSql = "SELECT oi.item_id, m.item_name, oi.quantity, oi.unit_price, oi.line_total " +
                "FROM order_items oi " +
                "JOIN menu_items m ON oi.item_id = m.item_id " +
                "WHERE oi.order_id = ? " +
                "ORDER BY oi.order_item_id";

        List<Map<String, Object>> lineRows = jdbcTemplate.queryForList(linesSql, orderId);
        List<BillLine> lines = new ArrayList<>();

        for (Map<String, Object> row : lineRows) {
            Integer itemId = ((Number) row.get("item_id")).intValue();
            String itemName = (String) row.get("item_name");
            Integer quantity = ((Number) row.get("quantity")).intValue();

            BigDecimal unitPrice = (row.get("unit_price") instanceof BigDecimal)
                    ? (BigDecimal) row.get("unit_price")
                    : new BigDecimal(row.get("unit_price").toString());

            BigDecimal lineTotal = (row.get("line_total") instanceof BigDecimal)
                    ? (BigDecimal) row.get("line_total")
                    : new BigDecimal(row.get("line_total").toString());

            lines.add(new BillLine(
                    itemId,
                    itemName,
                    quantity,
                    unitPrice.setScale(2, RoundingMode.HALF_UP),
                    lineTotal.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return new OrderResponse(id, orderDate, lines, grandTotal);
    }
}
