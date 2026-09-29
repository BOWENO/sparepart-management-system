package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.dto.LoginRequest;
import com.sparepart.dto.LoginResponse;
import com.sparepart.dto.ChangePasswordRequest;
import com.sparepart.entity.User;
import com.sparepart.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public LoginResponse login(LoginRequest req, HttpSession session, HttpServletRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BizException("账号已锁定，请在" + user.getLockedUntil() + "后重试");
        }
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            if (user != null) {
                int failed = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
                user.setFailedAttempts(failed);
                if (failed >= 3) {
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(3));
                    user.setFailedAttempts(0);
                }
                userMapper.updateById(user);
            }
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setSessionVersion((user.getSessionVersion() == null ? 0 : user.getSessionVersion()) + 1);
        userMapper.updateById(user);
        // 登录成功后更换 Session ID；递增会话版本使同账号旧会话立即失效。
        request.changeSessionId();
        session.setAttribute(AuthInterceptorKey.SESSION_USER_KEY, user);
        boolean needChange = Integer.valueOf(1).equals(user.getMustChangePwd());
        return new LoginResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRole(), needChange);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public LoginResponse me(HttpSession session) {
        User user = (User) session.getAttribute(AuthInterceptorKey.SESSION_USER_KEY);
        if (user == null) {
            throw new BizException("未登录");
        }
        User current = userMapper.selectById(user.getId());
        if (current == null) throw new BizException("账号不存在");
        boolean needChange = Integer.valueOf(1).equals(current.getMustChangePwd());
        return new LoginResponse(current.getId(), current.getUsername(), current.getRealName(), current.getRole(), needChange);
    }

    /** 修改密码：校验原密码，更新为新密码并清除强制改密标记 */
    public void changePassword(ChangePasswordRequest req, HttpSession session) {
        User sessionUser = (User) session.getAttribute(AuthInterceptorKey.SESSION_USER_KEY);
        if (sessionUser == null) {
            throw new BizException("未登录");
        }
        User db = userMapper.selectById(sessionUser.getId());
        if (db == null) {
            throw new BizException("账号不存在");
        }
        if (!encoder.matches(req.getOldPassword(), db.getPasswordHash())) {
            throw new BizException("原密码错误");
        }
        if (req.getNewPassword().equals(req.getOldPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        if (!req.getNewPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")) {
            throw new BizException("新密码至少6位，且必须同时包含字母和数字");
        }
        db.setPasswordHash(encoder.encode(req.getNewPassword()));
        db.setMustChangePwd(0);
        db.setSessionVersion((db.getSessionVersion() == null ? 0 : db.getSessionVersion()) + 1);
        userMapper.updateById(db);
        sessionUser.setMustChangePwd(0);
        sessionUser.setSessionVersion(db.getSessionVersion());
        session.setAttribute(AuthInterceptorKey.SESSION_USER_KEY, sessionUser);
    }

    /** 与 AuthInterceptor 共享的 Session key */
    public static final class AuthInterceptorKey {
        public static final String SESSION_USER_KEY = "LOGIN_USER";
    }
}
