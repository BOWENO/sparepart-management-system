package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("installed_parts")
public class InstalledPart {
    @TableId(type = IdType.AUTO) private Long id;
    private Long replaceRecordId;
    private Integer partId;
    private Integer deviceId;
    private Integer technicianId;
    private Integer quantity;
    private Integer inUseQuantity;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime removedAt;
}
