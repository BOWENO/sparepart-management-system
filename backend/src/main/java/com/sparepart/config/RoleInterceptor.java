package com.sparepart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparepart.common.Result;
import com.sparepart.common.UserContext;
import com.sparepart.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色权限拦截器（接口级 @RequireRole 注解化）。
 * 规则：
 *  - 已登录（AuthInterceptor 保证）；写操作(POST/PUT/DELETE/PATCH)：
 *    - 方法标注 @RequireRole 且空值 → 登录即可（如改密、标记已读）
 *    - 方法标注 @RequireRole 且指定角色 → 校验当前角色
 *    - 方法未标注 → 默认要求 ROLE_ADMIN
 *  - 读操作(GET) 一律放行（供主管查看）。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        RequireRole ann = null;
        if (handler instanceof HandlerMethod) {
            ann = ((HandlerMethod) handler).getMethodAnnotation(RequireRole.class);
        }
        // 显式角色要求对读写请求均生效，避免敏感查询因 GET 默认放行而泄露。
        if (ann != null && ann.value().length > 0) {
            User user = UserContext.get();
            if (user != null && Arrays.asList(ann.value()).contains(user.getRole())) return true;
            write403(response);
            return false;
        }

        String method = request.getMethod();
        boolean isWrite = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
        if (!isWrite) {
            return true; // 读操作放行
        }

        if (ann != null && ann.value().length == 0) {
            return true; // 已登录即可
        }

        // 需要 admin，或未标注注解（默认 admin）
        boolean needAdmin = true;
        if (needAdmin && !UserContext.isAdmin()) {
            write403(response);
            return false;
        }
        return true;
    }

    private void write403(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("无权限执行该操作")));
    }
}
