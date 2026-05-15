package com.tradeagent.repository.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for dashboard settings persistence. */
@Mapper
public interface SettingsMapper {
    /** Create the settings table when local PostgreSQL has not initialized it yet. */
    void createSettingsTable();

    /** Keep legacy settings tables aligned with timestamptz defaults. */
    void ensureTimestampDefaults();

    /** Insert one setting only when the key does not already exist. */
    void insertSettingIfAbsent(@Param("key") String key, @Param("value") String value);

    /** Select setting rows for the provided keys. */
    List<Map<String, Object>> selectSettings(@Param("keys") Collection<String> keys);

    /** Insert or update one setting value and refresh its update timestamp. */
    void upsertSetting(@Param("key") String key, @Param("value") String value);
}
