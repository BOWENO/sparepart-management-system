package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scrap_records")
public class ScrapRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scrapNo;
    private String flowNo;
    private Long replaceRecordId;
    private Integer partId;
    private Integer deviceId;
    private Integer quantity;
    private Integer remainingQuantity;
    private Long actualUsageCount;
    private Long ratedLifeCount;
    private String replacementReason;
    private String scrapReason;
    private String operator;
    private String status;
    private String disposalBatch;
    private LocalDateTime receivedAt;
    private LocalDateTime clearedAt;
}
