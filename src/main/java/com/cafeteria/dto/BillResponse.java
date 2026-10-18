package com.cafeteria.dto;

import java.math.BigDecimal;
import java.util.List;

public class BillResponse {
    private List<BillLine> lines;
    private BigDecimal grandTotal;

    public BillResponse() {
    }

    public BillResponse(List<BillLine> lines, BigDecimal grandTotal) {
        this.lines = lines;
        this.grandTotal = grandTotal;
    }

    public List<BillLine> getLines() {
        return lines;
    }

    public void setLines(List<BillLine> lines) {
        this.lines = lines;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }
}
