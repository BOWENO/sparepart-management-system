package com.sparepart.dto;

import com.sparepart.entity.StockFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StockFlowView extends StockFlow {
    private String partNo;
    private String partName;
    private String partSpec;
    private String unit;
    private String deviceCode;
    private String deviceName;
    private String lineCode;
    private String lineName;
    private String operatorName;
    private String recipientName;
    public String getFlowTypeName() { return com.sparepart.service.StockFlowReadService.typeName(getFlowType()); }
}
