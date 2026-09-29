package com.sparepart.aspect;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparepart.common.UserContext;
import com.sparepart.entity.OperationLog;
import com.sparepart.entity.User;
import com.sparepart.service.OperationLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 操作日志切面：拦截 @OpLog 方法，记录操作者、模块、动作、IP 及入参 JSON。
 */
@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogWriter logWriter;
    private final ObjectMapper objectMapper = createMaskingMapper();

    /** 敏感字段名（序列化统一脱敏，不落入日志） */
    private static final String[] SENSITIVE_FIELDS = {"password", "oldPassword", "newPassword", "passwordHash"};

    private static ObjectMapper createMaskingMapper() {
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        SimpleFilterProvider fp = new SimpleFilterProvider().addFilter("sensitive",
                SimpleBeanPropertyFilter.serializeAllExcept(SENSITIVE_FIELDS));
        fp.setFailOnUnknownId(false);
        m.setFilterProvider(fp);
        m.addMixIn(Object.class, SensitiveMixin.class);
        return m;
    }

    /** 应用于所有类型的 @JsonFilter 载体 */
    @JsonFilter("sensitive")
    private abstract static class SensitiveMixin {
    }

    public OperationLogAspect(OperationLogWriter logWriter) {
        this.logWriter = logWriter;
    }

    @Around("@annotation(op)")
    public Object around(ProceedingJoinPoint pjp, OpLog op) throws Throwable {
        try {
            String before = safeToJson(pjp.getArgs());
            Object result = pjp.proceed();
            saveLog(op, op.action(), before, safeToJson(result));
            return result;
        } catch (Throwable t) {
            // 失败操作也要记录日志（after_json 需为合法 JSON），然后继续抛出
            String err = t.getMessage() == null ? t.toString() : t.getMessage();
            saveLog(op, op.action() + "-失败", null, safeToJson(Map.of("error", err)));
            throw t;
        }
    }

    /** 安全序列化：失败返回 null，绝不影响主流程 */
    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveLog(OpLog op, String action, String beforeJson, String afterJson) {
        try {
            OperationLog log = new OperationLog();
            User user = UserContext.get();
            log.setOperator(user == null ? "system" : user.getUsername());
            log.setModule(op.module());
            log.setAction(action);
            log.setBeforeJson(beforeJson);
            log.setAfterJson(afterJson);
            log.setIp(getIp());
            logWriter.write(log);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
        }
    }

    private String getIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
