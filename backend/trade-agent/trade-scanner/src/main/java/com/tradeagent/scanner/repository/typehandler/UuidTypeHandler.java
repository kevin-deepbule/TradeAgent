package com.tradeagent.scanner.repository.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** 使用 PostgreSQL 原生 UUID 参数与结果，避免将 UUID 绑定为文本。 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    /** 将 UUID 作为 JDBC 原生对象绑定。 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, UUID value, JdbcType jdbcType) throws SQLException {
        ps.setObject(index, value);
    }
    /** 按列名读取可空 UUID。 */
    @Override
    public UUID getNullableResult(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, UUID.class);
    }
    /** 按列序号读取可空 UUID。 */
    @Override
    public UUID getNullableResult(ResultSet rs, int index) throws SQLException {
        return rs.getObject(index, UUID.class);
    }
    /** 从 CallableStatement 读取可空 UUID。 */
    @Override
    public UUID getNullableResult(CallableStatement statement, int index) throws SQLException {
        return statement.getObject(index, UUID.class);
    }
}
