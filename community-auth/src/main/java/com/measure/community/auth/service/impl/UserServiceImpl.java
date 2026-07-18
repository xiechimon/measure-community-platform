package com.measure.community.auth.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.auth.mapper.SysUserMapper;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.model.RetObj;
import com.measure.community.auth.model.entity.SysUser;
import com.measure.community.auth.model.req.LoginInfoReq;
import com.measure.community.auth.model.vo.LoginUser;
import com.measure.community.auth.service.UserService;
import com.measure.community.auth.utils.JwtTokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @Description: 登录鉴权:BCrypt 校验密码,加载角色/权限,签发 JWT 并缓存会话(§6)。
 * @ClassName: UserServiceImpl
 */
@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Autowired
	private RedisTemplate redisTemplate;

	@Override
	public RetObj login(LoginInfoReq req) {
		// 1. 按账号查用户
		SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getUsername, req.getAccount()));
		// 2. 校验:存在 + BCrypt 密码匹配(账号/密码错误统一提示,不泄漏哪个错)
		if (Objects.isNull(user) || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
			throw new BizException(SystemStatus.UNAUTHORIZED, "账号或密码错误");
		}
		if (user.getStatus() != null && user.getStatus() == 0) {
			throw new BizException(SystemStatus.FORBIDDEN, "账号已停用");
		}

		// 3~4. 组装登录用户(角色/权限/org/grid/dataScope) + 签发 JWT
		LoginUser loginUser = buildLoginUser(user);
		String token = JwtTokenUtils.createToken(user.getId());
		loginUser.setToken(token);

		// 5. 缓存会话(TTL 与 token 过期一致)
		long ttl = JwtTokenUtils.getExpireSeconds();
		redisTemplate.opsForValue().set("alibaba-token:" + token, JSON.toJSONString(loginUser), ttl, TimeUnit.SECONDS);
		redisTemplate.opsForValue().set("alibaba_user_login_token:" + user.getId(), token, ttl, TimeUnit.SECONDS);
		return RetObj.success(loginUser);
	}

	/** 从 SysUser + mapper 组装 LoginUser(角色码/权限点码/org/grid/最宽 dataScope);不含 token/Redis。 */
	LoginUser buildLoginUser(SysUser u) {
		LoginUser lu = new LoginUser();
		lu.setId(u.getId());
		lu.setAccount(u.getUsername());
		lu.setName(u.getName());
		lu.setPhone(u.getPhone());
		lu.setRoles(baseMapper.selectRoleCodes(u.getId()));
		lu.setPermissions(baseMapper.selectPermissionCodes(u.getId()));
		lu.setOrgId(u.getOrgId());
		lu.setGridId(u.getGridId());
		lu.setDataScope(com.measure.community.common.enums.DataScope
				.resolve(baseMapper.selectRoleDataScopes(u.getId())).name());
		return lu;
	}
}
