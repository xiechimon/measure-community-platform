package com.measure.community.common.utils;


import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * UserContextHolder
 *
 * @author 海言
 * @date 2025/9/5
 * @time 15:57
 * @Description 请求上下文:承载网关下发的用户身份与权限(角色/权限点),供审计与功能级鉴权使用。
 */
public class UserContextHolder {

    private static final ThreadLocal<Map<String, String>> context = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> roles = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> permissions = new ThreadLocal<>();

    // 设置用户信息
    public static void set(Map<String, String> userInfo) {
        context.set(userInfo);
    }

    // 获取用户信息
    public static Map<String, String> get() {
        return context.get();
    }

    public static String getUserId() {
        Map<String, String> userInfo = context.get();
        return userInfo != null ? userInfo.get("id") : null;
    }

    public static String getName() {
        Map<String, String> userInfo = context.get();
        return userInfo != null ? userInfo.get("name") : null;
    }

    public static Long getOrgId() { return parseLong("orgId"); }

    public static Long getGridId() { return parseLong("gridId"); }

    /** 数据范围码，缺失时按最窄 SELF。 */
    public static String getDataScope() {
        Map<String, String> u = context.get();
        String s = u != null ? u.get("dataScope") : null;
        return (s == null || s.isBlank()) ? "SELF" : s;
    }

    private static Long parseLong(String key) {
        Map<String, String> u = context.get();
        String v = u != null ? u.get(key) : null;
        if (v == null || v.isBlank()) return null;
        try { return Long.valueOf(v); } catch (NumberFormatException e) { return null; }
    }

    // ---- 角色 / 权限(RBAC,§6)----
    public static void setRoles(Set<String> r) {
        roles.set(r);
    }

    public static Set<String> getRoles() {
        Set<String> r = roles.get();
        return r != null ? r : Collections.emptySet();
    }

    public static void setPermissions(Set<String> p) {
        permissions.set(p);
    }

    public static Set<String> getPermissions() {
        Set<String> p = permissions.get();
        return p != null ? p : Collections.emptySet();
    }

    public static boolean hasPermission(String code) {
        return getPermissions().contains(code);
    }

    // 清理(过滤器 finally 必须调用,防止 ThreadLocal 泄漏)
    public static void clear() {
        context.remove();
        roles.remove();
        permissions.remove();
    }

}
