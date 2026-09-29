package com.sparepart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    /** 仅写入，序列化输出时忽略，防止明文进入操作日志 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 仅写入，序列化输出时忽略，防止明文进入操作日志 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度至少 6 位")
    private String newPassword;
}