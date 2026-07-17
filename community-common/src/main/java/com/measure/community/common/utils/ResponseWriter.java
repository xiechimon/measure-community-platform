package com.measure.community.common.utils;

import com.alibaba.fastjson2.JSON;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.model.RetObj;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 在 Servlet 过滤器等 DispatcherServlet 之前的位置,统一写出 RetObj 错误响应。
 */
public final class ResponseWriter {

    private ResponseWriter() {
    }

    public static void writeError(HttpServletResponse resp, SystemStatus status) throws IOException {
        resp.setStatus(status.getCode());
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JSON.toJSONString(RetObj.error(status)));
    }
}
