package com.measure.community.info.support;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;

/**
 * 敏感字段 AES 加密接线占位。
 * 实际密钥管理/加解密见《详细功能设计说明书》第 5 章,后续接入 KMS。
 */
public class AesTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }
    // TODO 接入真实 AES/KMS,当前为直通占位
    private String encrypt(String v) { return v; }
    private String decrypt(String v) { return v; }
}
