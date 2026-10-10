package com.tradeagent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeagent.watchlist.repository.SettingsRepository;
import com.tradeagent.watchlist.repository.WatchlistRepository;
import com.tradeagent.watchlist.service.WatchlistService;
import com.tradeagent.scanner.service.ScannerService;
import com.tradeagent.scanner.job.CollectorJob;
import com.tradeagent.scanner.messaging.ScannerMessaging;
import com.tradeagent.scanner.repository.ScannerRepository;

/** 验证模块化单体将各业务模块组装为一个 Web 应用。 */
@SpringBootTest(properties = {
        "debug=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
class TradeAgentApplicationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistService watchlistService;

    @MockBean
    private SettingsRepository settingsRepository;

    @MockBean
    private WatchlistRepository watchlistRepository;

    // 本组装测试排除了 JDBC，因此使用 Mock 隔离基础设施流程。
    @MockBean
    private ScannerService scannerService;

    @MockBean
    private CollectorJob collectorJob;

    @MockBean
    private ScannerMessaging scannerMessaging;

    @MockBean
    private ScannerRepository scannerRepository;

    /** 验证业务模块组装后仍提供原有 Health API。 */
    @Test
    void assemblesHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.refreshSeconds").value(60));
    }

    /** 验证无需启用配置即可注册 Scanner 路由。 */
    @Test
    void exposesScannerByDefault() throws Exception {
        when(scannerService.events(20)).thenReturn(List.of());
        mockMvc.perform(get("/api/scanner/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
