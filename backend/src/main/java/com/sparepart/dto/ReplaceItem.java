package com.sparepart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReplaceItem {
    @NotNull(message = "新备品不能为空")
    private Integer newPartId;
    /** 旧备品ID，特殊出库（无旧件）时为 null */
    private Integer oldPartId;
    @NotNull(message = "关联设备不能为空")
    private Integer deviceId;
    @NotNull(message = "领用人不能为空")
    private Integer technicianId;
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
    /** 退回旧品数量；正常换新默认等于新品数量。 */
    @Min(value = 1, message = "旧品数量必须大于0")
    private Integer oldQuantity;
    /** 新旧型号不同或数量不同时必填。 */
    private String differenceReason;
    private Boolean isException = false;
    private String exceptionRemark;
    /** FAILURE=故障报废，LIFE_LIMIT=寿命到期强换，OTHER=其他。 */
    private String replacementReason;
    @Min(value = 0, message = "起始计数不能小于0")
    private Long counterStart;
    @Min(value = 0, message = "结束计数不能小于0")
    private Long counterEnd;
    @Min(value = 0, message = "实际使用次数不能小于0")
    /** 本行每件旧件的实际使用次数，不是整批累计次数。 */
    private Long actualUsageCount;
    private String scrapReason;
}
