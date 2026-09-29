package com.sparepart.common;

import com.sparepart.entity.User;

/**
 * 基于 ThreadLocal 的 Session 用户上下文
 */
public class UserContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static boolean isAdmin() {
        User u = HOLDER.get();
        return u != null && "ROLE_ADMIN".equals(u.getRole());
    }

    public static void clear() {
        HOLDER.remove();
    }
}