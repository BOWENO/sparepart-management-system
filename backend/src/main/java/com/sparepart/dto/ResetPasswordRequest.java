package com.sparepart.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "临时密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,}$", message = "临时密码至少6位，且必须同时包含字母和数字")
    private String temporaryPassword;
}
