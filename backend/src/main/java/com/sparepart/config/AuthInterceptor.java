package com.sparepart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparepart.common.Result;
import com.sparepart.common.UserContext;
import com.sparepart.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import com.sparepart.mapper.UserMapper;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录鉴权拦截器：校验 Session 中是否存在已登录用户，并注入 UserContext。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_KEY = "LOGIN_USER";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserMapper userMapper;

    public AuthInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute(SESSION_USER_KEY);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail("未登录或会话已过期")));
            return false;
        }
        User current = userMapper.selectById(user.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())
                || !java.util.Objects.equals(current.getSessionVersion(), user.getSessionVersion())) {
            session.invalidate();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail("账号已在其他设备登录或会话已失效")));
            return false;
        }
        String uri = request.getRequestURI();
        boolean authFlow = uri.equals("/api/v1/auth/me")
                || uri.equals("/api/v1/auth/logout")
                || uri.equals("/api/v1/auth/change-password");
        if (Integer.valueOf(1).equals(current.getMustChangePwd()) && !authFlow) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail("请先修改初始密码")));
            return false;
        }
        UserContext.set(current);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
