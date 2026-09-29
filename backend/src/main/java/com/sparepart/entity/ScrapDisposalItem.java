package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 报废品处置单与报废档案之间的数量分配明细。 */
@Data
@TableName("scrap_disposal_items")
public class ScrapDisposalItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private Long scrapRecordId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
