package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("request_dedup")
public class RequestDedup {
    @TableId
    private String requestId;
    private String flowNo;
    private String operator;
    private String payloadHash;
    private Integer itemCount;
}
