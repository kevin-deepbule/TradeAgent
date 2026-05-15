package com.tradeagent.repository.mapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Type handler that preserves existing API timestamp strings for PostgreSQL timestamptz values. */
public class OffsetDateTimeStringTypeHandler extends BaseTypeHandler<String> {
    /** Write string timestamps back to JDBC unchanged when needed by mapper statements. */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(index, parameter);
    }

    /** Read a named timestamptz column as an ISO offset string. */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return format(rs.getObject(columnName, OffsetDateTime.class));
    }

    /** Read an indexed timestamptz column as an ISO offset string. */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return format(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    /** Read a callable timestamptz column as an ISO offset string. */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return format(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    /** Format nullable database timestamps for the existing watchlist DTO field. */
    private String format(OffsetDateTime value) {
        return value == null ? "" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }
}
