package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("replace_records")
public class ReplaceRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Integer newPartId;
    private Integer oldPartId;
    private Integer deviceId;
    private Integer technicianId;
    private Integer quantity;
    private Integer oldQuantity;
    private String differenceReason;
    private String requestId;
    private String replacementReason;
    private Long counterStart;
    private Long counterEnd;
    private Long actualUsageCount;
    /** ACTIVE=有效，CANCELLED=整单撤销。 */
    private String status;
    private String cancelReason;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
