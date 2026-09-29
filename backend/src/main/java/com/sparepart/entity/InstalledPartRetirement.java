package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("installed_part_retirements")
public class InstalledPartRetirement {
    @TableId(type = IdType.AUTO) private Long id;
    private Long replaceRecordId;
    private Long installedPartId;
    private Integer quantity;
}
