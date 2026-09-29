package com.sparepart.dto;
import lombok.Data;
@Data public class StockFlowExcelDTO {
    private String flowNo;
    private Integer flowType;
    private Integer partId;
    private Integer deviceId;
    private Integer quantity;
    private String operator;
    private String businessDate;
    private String remark;
    private String createdAt;
}
