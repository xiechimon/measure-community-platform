package com.measure.community.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.common.model.RetObj;
import com.measure.community.auth.model.entity.SysUser;
import com.measure.community.auth.model.req.LoginInfoReq;

/**
 * @Description:
 * @ClassName: UserService
 * @Author: xiongfeng
 * @Date: 2025/9/1 21:53
 * @Version: 1.0
 */
public interface UserService extends IService<SysUser> {

	RetObj login(LoginInfoReq req);
}
