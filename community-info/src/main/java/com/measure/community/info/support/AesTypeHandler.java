package com.measure.community.info.support;

import com.measure.community.common.crypto.SensitiveCrypto;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;

/**
 * 敏感字段 AES 加密 TypeHandler:落库前 AES-256-GCM 加密,读取时解密。
 * 实际加解密与密钥管理集中在 {@link SensitiveCrypto}(§5,密钥经配置/后续 KMS 注入)。
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
    private String encrypt(String v) { return SensitiveCrypto.encrypt(v); }
    private String decrypt(String v) { return SensitiveCrypto.decrypt(v); }
}
