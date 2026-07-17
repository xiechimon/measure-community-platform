package com.measure.community.common.constant;

/**
 * @Description: 公共常量类
 * @ClassName: CommonConatant
 * @Author: xiongfeng
 * @Date: 2026/3/15 17:27
 * @Version: 1.0
 */
public interface CommonConstant {

	public static final String X_INTERNAL_AUTH = "X-Internal-Auth";
	public static final String X_USERINFO = "X-UserInfo";
	public static final String TRACE_ID_HEADER = "traceId";

	/** 无登录用户时的审计人占位 */
	public static final String AUDIT_SYSTEM_USER = "system";
}
