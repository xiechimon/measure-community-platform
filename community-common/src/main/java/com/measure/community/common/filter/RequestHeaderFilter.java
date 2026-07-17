package com.measure.community.common.filter;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.measure.community.common.constant.CommonConstant;
import com.measure.community.common.utils.UserContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * RequestHeaderFilter
 *
 * @author 海言
 * @date 2025/9/5
 * @time 15:29
 * @Description 请求头过滤器
 */
@Component
@Slf4j
public class RequestHeaderFilter implements Filter {

    private final String internalSecret;

    public RequestHeaderFilter(@Value("${security.internal.secret}") String internalSecret) {
        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("必须配置 security.internal.secret");
        }
        this.internalSecret = internalSecret;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        // 1. 提取基础信息
        String uri = req.getRequestURI();
        String method = req.getMethod();
        // 获取 URL 后的参数 (?id=1)
        String queryString = req.getQueryString();
        // 1. 【2026-03-14新增】处理 TraceId
        String traceId = req.getHeader(CommonConstant.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            // 将网关传来的 ID 存入 MDC
            MDC.put(CommonConstant.TRACE_ID_HEADER, traceId);
        }
        
        // 2. 网关防刷校验
        String header = req.getHeader(CommonConstant.X_INTERNAL_AUTH);
        if (!internalSecret.equals(header)) {
            log.warn("非法访问接口，禁止绕过网关访问: {}", uri);
            com.measure.community.common.utils.ResponseWriter.writeError(
                    (jakarta.servlet.http.HttpServletResponse) response,
                    com.measure.community.common.enums.SystemStatus.FORBIDDEN);
            return;
        }
        
        // 3. 处理用户信息并存入 MDC
        String userBase64 = req.getHeader("X-UserInfo");
        String userId = null;
        if(StringUtils.hasText(userBase64)){
            //用户信息转码
            String userJson = new String(Base64.getDecoder().decode(userBase64), StandardCharsets.UTF_8);
            com.alibaba.fastjson.JSONObject obj = JSON.parseObject(userJson);
            // 标量字段进 Map(id/name/account/phone 等),数组字段(roles/permissions)另存
            Map<String, String> map = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                Object v = e.getValue();
                if (v != null && !(v instanceof java.util.Collection)) {
                    map.put(e.getKey(), String.valueOf(v));
                }
            }
            //将用户信息设置到自定义context中
            UserContextHolder.set(map);
            UserContextHolder.setRoles(toStringSet(obj.getJSONArray("roles")));
            UserContextHolder.setPermissions(toStringSet(obj.getJSONArray("permissions")));

            userId = map.get("id");
            if (StringUtils.hasText(userId)) {
                MDC.put("userId", userId);
            }
        }
        
        // 4. 打印入口日志（此时 MDC 已经有了 traceId 和 userId）
        log.info("[请求信息] Method: {}, URI: {}, Params: {}", method, uri,
                StringUtils.hasText(queryString) ? queryString : "EMPTY");

        try {
            chain.doFilter(request, response);
        } finally {
            // 防止内存泄漏，必须清除 ThreadLocal 和 MDC
            UserContextHolder.clear();
            MDC.remove(CommonConstant.TRACE_ID_HEADER);
            if (StringUtils.hasText(userId)) {
                MDC.remove("userId");
            }
        }
    }

    private static java.util.Set<String> toStringSet(com.alibaba.fastjson.JSONArray arr) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (arr != null) {
            for (Object o : arr) {
                if (o != null) {
                    set.add(String.valueOf(o));
                }
            }
        }
        return set;
    }
}
