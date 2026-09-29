package com.sparepart.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限注解。
 * - 空值(默认)：已登录即可访问（写操作放行给登录用户，如改密、标记已读）。
 * - 指定角色：如 {@code @RequireRole({"ROLE_ADMIN"})}，仅该角色可访问。
 * 未标注此注解的写操作默认要求 ROLE_ADMIN（仓管员）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value() default {};
}