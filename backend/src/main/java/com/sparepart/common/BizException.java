package com.sparepart.common;

/**
 * 业务异常，携带可选的行号信息（用于批量换新行级错误提示）
 */
public class BizException extends RuntimeException {

    /** 批量接口中出错的行号（0-based），非批量操作时为 -1 */
    private final int failedRowIndex;

    public BizException(String message) {
        super(message);
        this.failedRowIndex = -1;
    }

    public BizException(String message, int failedRowIndex) {
        super(message);
        this.failedRowIndex = failedRowIndex;
    }

    public int getFailedRowIndex() {
        return failedRowIndex;
    }
}