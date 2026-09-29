package com.sparepart.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private Integer id;
    private String username;
    private String realName;
    private String role;
    /** true 表示首次登录需强制修改密码 */
    private Boolean needChangePassword;
}