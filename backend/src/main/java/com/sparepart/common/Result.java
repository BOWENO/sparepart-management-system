package com.sparepart.common;

import lombok.Data;

/**
 * 统一响应体 {code, msg, data}
 * code: 0=成功, -1=业务失败
 */
@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(0, msg, data);
    }

    public static <T> Result<T> success(String msg) {
        return new Result<>(0, msg, null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(-1, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
}