package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("stock_flow")
public class StockFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Integer flowType;
    private Integer partId;
    private Integer deviceId;
    private Integer quantity;
    private String operator;
    private String remark;
    private Integer isException;
    private String exceptionRemark;
    private BigDecimal unitPrice;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate businessDate;
    private Long reversedFlowId;
    private Long replaceRecordId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
