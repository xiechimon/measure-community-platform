package com.measure.community.common.config;

import com.measure.community.common.constant.CommonConstant;
import com.measure.community.common.utils.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import com.alibaba.fastjson2.JSON;

/**
 * @Description: Feign 调用拦截器，透传用户信息和 Internal-Secret
 */
@Slf4j
@Configuration
public class FeignConfig implements RequestInterceptor {

    private final String internalSecret;

    public FeignConfig(@Value("${security.internal.secret}") String internalSecret) {
        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("必须配置 security.internal.secret");
        }
        this.internalSecret = internalSecret;
    }

    @Override
    public void apply(RequestTemplate template) {
        // 1. 设置内部调用密钥，防止绕过网关
        template.removeHeader(CommonConstant.X_INTERNAL_AUTH);
        template.header(CommonConstant.X_INTERNAL_AUTH, internalSecret);

        // 2. 传递用户信息 (如果有)
        Map<String, String> userInfo = UserContextHolder.get();
        if (userInfo != null && !userInfo.isEmpty()) {
             try {
                String json = JSON.toJSONString(userInfo);
                String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                template.header(CommonConstant.X_USERINFO, base64);
            } catch (Exception e) {
                log.error("Feign用户信息编码失败", e);
            }
        }

        // 3. 【2026-03-14新增】传递 TraceId
        // 从当前线程的 MDC 中取出 traceId 塞入 Feign 的请求头中传递给下游
        String traceId = MDC.get(CommonConstant.TRACE_ID_HEADER);
        if (traceId != null) {
            template.header(CommonConstant.TRACE_ID_HEADER, traceId);
        }
    }
}
