package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 线程池上下文装饰器:把主线程的 MDC(traceId) 与 UserContextHolder(用户信息)
 * 传入线程池子线程,执行后清理,避免异步任务丢失审计人/traceId 及线程复用污染。
 */
public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Map<String, String> user = UserContextHolder.get();
        return () -> {
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                if (user != null) {
                    UserContextHolder.set(user);
                }
                runnable.run();
            } finally {
                MDC.clear();
                UserContextHolder.clear();
            }
        };
    }
}
