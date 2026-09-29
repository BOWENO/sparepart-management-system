package com.sparepart.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/** 按设备汇总的在使用备品主表。 */
@Data
public class InstalledDeviceSummary {
    private Integer deviceId;
    private String deviceCode;
    private String deviceName;
    private Integer partTypeCount;
    private Integer totalInUseQuantity;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime latestIssuedAt;
}
