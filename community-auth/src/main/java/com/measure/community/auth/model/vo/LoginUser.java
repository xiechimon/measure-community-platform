package com.measure.community.auth.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户信息。写入 Redis(alibaba-token:<token>)并经网关 X-UserInfo 下发到各服务,
 * 其中 roles/permissions 供服务端功能级鉴权(§6)。
 */
@Data
public class LoginUser implements Serializable {

    private Long id;

    private String name;

    private String account;

    private String phone;

    private String token;

    /** 角色码 */
    private List<String> roles;

    /** 权限点码,如 population:export */
    private List<String> permissions;

    /** 归属组织 ID(数据范围过滤用,§P2b) */
    private Long orgId;

    /** 归属网格 ID(数据范围过滤用,§P2b) */
    private Long gridId;

    /** 多角色取最宽档后的数据范围:ALL/GRID/SELF */
    private String dataScope;

    /** 归属组织节点的物化路径(sys_org.path,如 /1/5/10/),供拦截器展开层级数据范围,无归属组织时为 null */
    private String orgPath;
}
