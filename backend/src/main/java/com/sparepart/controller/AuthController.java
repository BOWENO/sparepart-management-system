package com.sparepart.controller;

import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.config.RequireRole;
import com.sparepart.dto.ChangePasswordRequest;
import com.sparepart.dto.LoginRequest;
import com.sparepart.dto.LoginResponse;
import com.sparepart.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpSession session,
                                       HttpServletRequest request) {
        return Result.success("登录成功", authService.login(req, session, request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        authService.logout(session);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<LoginResponse> me(HttpSession session) {
        return Result.success(authService.me(session));
    }

    /** 修改密码（所有已登录用户可用） */
    @PostMapping("/change-password")
    @RequireRole
    @OpLog(module = "系统", action = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpSession session) {
        authService.changePassword(req, session);
        return Result.success("密码修改成功");
    }
}
