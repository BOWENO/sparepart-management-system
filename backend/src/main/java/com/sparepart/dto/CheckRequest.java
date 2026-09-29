package com.sparepart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckRequest {
    @NotNull(message = "备品ID不能为空")
    private Integer partId;
    @NotNull(message = "实盘数不能为空")
    @Min(value = 0, message = "实盘数不能为负数")
    private Integer actualQty;
}