package com.sparepart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量换新请求。操作人由服务端从登录会话取（UserContext），不接受客户端传入，防伪造。
 */
@Data
public class ReplaceBatchRequest {
    @jakarta.validation.constraints.NotBlank(message = "请求编号不能为空")
    private String requestId;
    @NotEmpty(message = "换新明细不能为空")
    private List<@Valid ReplaceItem> items;
}
