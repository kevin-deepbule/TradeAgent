package com.tradeagent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** SQLite datasource configuration shared by repository classes. */
@Configuration
public class DataSourceConfig {
    /** Create a SQLite datasource that reuses the existing Python database by default. */
    @Bean
    public DataSource dataSource(AppProperties properties) throws IOException {
        Path dbPath = resolveDbPath(properties.watchlistDbPath());
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    /** Resolve the watchlist database path from env configuration or known project locations. */
    private Path resolveDbPath(String configuredPath) {
        if (StringUtils.hasText(configuredPath)) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }

        Path rootRelative = Path.of("backend", "data", "watchlist.db").toAbsolutePath().normalize();
        if (Files.exists(Path.of("backend"))) {
            return rootRelative;
        }
        return Path.of("..", "backend", "data", "watchlist.db").toAbsolutePath().normalize();
    }
}
