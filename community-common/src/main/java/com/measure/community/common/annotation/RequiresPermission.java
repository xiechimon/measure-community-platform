package com.measure.community.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 功能级鉴权(§6):标注在 controller/service 方法上,要求当前用户具备指定权限点。
 * 由 {@code RequiresPermissionAspect} 读取 {@code UserContextHolder} 权限集校验,
 * 缺失抛 {@code BizException(FORBIDDEN)}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** 需要的权限点,如 {@code population:export} */
    String[] value();

    /** 多个权限点的逻辑关系:AND=全部具备(默认),OR=具备其一 */
    Logical logical() default Logical.AND;

    enum Logical {
        AND, OR
    }
}
