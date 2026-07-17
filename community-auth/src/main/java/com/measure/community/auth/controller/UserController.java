package com.measure.community.auth.controller;

import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.utils.UserContextHolder;
import com.measure.community.common.model.RetObj;
import com.measure.community.auth.model.req.LoginInfoReq;
import com.measure.community.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description: 用户控制器
 * @ClassName: UserController
 * @Author: xiongfeng
 * @Date: 2025/9/1 21:51
 * @Version: 1.0
 */
@Tag(name = "用户控制器")
@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

	@Autowired
	private UserService userService;
    /**
     * 登录
     * @param req
     * @return
     */
    @Operation(summary = "登录")
	@PostMapping("/login")
	public RetObj login(@RequestBody LoginInfoReq req){
		return userService.login(req);
	}

    /**
     * 获取登录用户名称
     * @return
     */
    @Operation(summary = "获取登录用户名称")
    @RequiresPermission("system:user:query")
    @GetMapping("/getUserName")
    public RetObj getUserName(){
        return RetObj.success(UserContextHolder.getName());
    }
}
