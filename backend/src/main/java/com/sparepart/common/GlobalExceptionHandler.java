package com.sparepart.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparepart.entity.OperationLog;
import com.sparepart.entity.User;
import com.sparepart.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：所有报错均记录到 operation_logs（应用可查）+ 系统日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GlobalExceptionHandler(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @ExceptionHandler(BizException.class)
    public Result<Map<String, Object>> handleBiz(BizException e, HttpServletRequest request) {
        log.warn("业务异常: {}", e.getMessage());
        writeErrorLog("业务异常", e.getMessage(), request);
        Map<String, Object> data = null;
        if (e.getFailedRowIndex() >= 0) {
            data = new HashMap<>();
            data.put("failedRowIndex", e.getFailedRowIndex());
            data.put("failedRowError", e.getMessage());
        }
        return Result.fail(-1, e.getMessage(), data);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        writeErrorLog("参数校验失败", msg, request);
        return Result.fail(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String msg = e.getConstraintViolations().stream()
                .findFirst().map(v -> v.getMessage()).orElse("参数校验失败");
        writeErrorLog("参数校验失败", msg, request);
        return Result.fail(msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        String msg = "缺少参数：" + e.getParameterName();
        writeErrorLog("参数校验失败", msg, request);
        return Result.fail(msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String msg = "参数格式错误：" + e.getName();
        writeErrorLog("参数校验失败", msg, request);
        return Result.fail(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        String msg = "请求内容格式错误";
        writeErrorLog("参数校验失败", msg, request);
        return Result.fail(msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} @ {} {}", e.getMessage(), request.getMethod(), request.getRequestURI(), e);
        writeErrorLog("系统异常", e.toString(), request);
        return Result.fail("系统异常，请稍后重试");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail("资源不存在");
    }

    /** 记录报错到操作日志表（after_json 需为合法 JSON） */
    private void writeErrorLog(String action, String msg, HttpServletRequest request) {
        try {
            OperationLog logEntry = new OperationLog();
            User user = UserContext.get();
            logEntry.setOperator(user == null ? "system" : user.getUsername());
            logEntry.setModule("系统");
            logEntry.setAction(action);
            Map<String, Object> detail = new HashMap<>();
            detail.put("error", msg);
            if (request != null) {
                detail.put("uri", request.getMethod() + " " + request.getRequestURI());
            }
            logEntry.setAfterJson(objectMapper.writeValueAsString(detail));
            logEntry.setIp(request == null ? null : request.getRemoteAddr());
            operationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.error("记录失败日志异常: {}", ex.getMessage());
        }
    }
}
