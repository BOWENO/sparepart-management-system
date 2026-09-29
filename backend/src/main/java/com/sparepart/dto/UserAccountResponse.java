package com.sparepart.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor
public class UserAccountResponse {
    private Integer id; private String username; private String realName; private String role;
    private Integer status; private Integer mustChangePwd;
}
