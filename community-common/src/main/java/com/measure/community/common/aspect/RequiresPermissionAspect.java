package com.measure.community.common.aspect;

import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.utils.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 功能级鉴权切面:拦截 {@link RequiresPermission} 标注的方法,
 * 校验 {@link UserContextHolder} 中的权限集,不满足抛 {@code BizException(FORBIDDEN)}。
 */
@Slf4j
@Aspect
@Component
public class RequiresPermissionAspect {

    @Before("@annotation(rp)")
    public void check(RequiresPermission rp) {
        Set<String> owned = UserContextHolder.getPermissions();
        String[] need = rp.value();
        boolean ok = rp.logical() == RequiresPermission.Logical.OR
                ? Arrays.stream(need).anyMatch(owned::contains)
                : owned.containsAll(Arrays.asList(need));
        if (!ok) {
            log.warn("鉴权失败: 用户={}, 需要权限={}({}), 实际={}",
                    UserContextHolder.getUserId(), Arrays.toString(need), rp.logical(), owned);
            throw new BizException(SystemStatus.FORBIDDEN, "缺少权限: " + String.join(",", need));
        }
    }
}
