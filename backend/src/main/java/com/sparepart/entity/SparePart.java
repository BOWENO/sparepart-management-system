package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("spare_parts")
public class SparePart {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String partNo;
    private String name;
    private String spec;
    private String unit;
    private String locationCode;
    private Integer minStock;
    private Integer goodStock;
    private Integer scrapStock;
    private Long ratedLifeCount;
    private BigDecimal price;
    private Integer status;
    /** 0正常 1已删除；删除仅从当前清单隐藏，保留历史关联和操作日志。 */
    @TableLogic(value = "0", delval = "1")
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Integer deleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
