package com.sparepart.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/** 在使用备品台账视图，固定前后端字段名，避免数据库对 Map 别名大小写处理不一致。 */
@Data
public class InstalledPartView {
    private Long id;
    private Long replaceRecordId;
    private String flowNo;
    private Integer partId;
    private String partNo;
    private String partName;
    private String partSpec;
    private Integer quantity;
    private Integer inUseQuantity;
    private Integer deviceId;
    private String deviceCode;
    private String deviceName;
    private Integer technicianId;
    private String employeeNo;
    private String technicianName;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime issuedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime removedAt;
}
