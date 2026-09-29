package com.sparepart.controller;
import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.config.RequireRole;
import com.sparepart.dto.ResetPasswordRequest;
import com.sparepart.dto.UserAccountResponse;
import com.sparepart.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/user-accounts")
public class UserAccountController {
    private final UserAccountService service;
    public UserAccountController(UserAccountService service) { this.service = service; }
    @GetMapping @RequireRole({"ROLE_VIEWER"})
    public Result<List<UserAccountResponse>> list() { return Result.success(service.list()); }
    @PostMapping("/{id}/reset-password") @RequireRole({"ROLE_VIEWER"})
    @OpLog(module = "账号管理", action = "重置密码")
    public Result<Void> resetPassword(@PathVariable Integer id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.getTemporaryPassword());
        return Result.success("密码已重置，用户下次登录必须修改密码");
    }
}
