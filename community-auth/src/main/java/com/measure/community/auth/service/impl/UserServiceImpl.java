package com.measure.community.auth.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.auth.mapper.SysUserMapper;
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
			return RetObj.error("账号或密码错误");
		}
		if (user.getStatus() != null && user.getStatus() == 0) {
			return RetObj.error("账号已停用");
		}

		// 3. 加载角色码 / 权限点码
		List<String> roles = baseMapper.selectRoleCodes(user.getId());
		List<String> permissions = baseMapper.selectPermissionCodes(user.getId());

		// 4. 组装登录用户 + 签发 JWT
		LoginUser loginUser = new LoginUser();
		loginUser.setId(user.getId());
		loginUser.setAccount(user.getUsername());
		loginUser.setName(user.getName());
		loginUser.setPhone(user.getPhone());
		loginUser.setRoles(roles);
		loginUser.setPermissions(permissions);
		String token = JwtTokenUtils.createToken(user.getId());
		loginUser.setToken(token);

		// 5. 缓存会话(TTL 与 token 过期一致)
		long ttl = JwtTokenUtils.getExpireSeconds();
		redisTemplate.opsForValue().set("alibaba-token:" + token, JSON.toJSONString(loginUser), ttl, TimeUnit.SECONDS);
		redisTemplate.opsForValue().set("alibaba_user_login_token:" + user.getId(), token, ttl, TimeUnit.SECONDS);
		return RetObj.success(loginUser);
	}
}
