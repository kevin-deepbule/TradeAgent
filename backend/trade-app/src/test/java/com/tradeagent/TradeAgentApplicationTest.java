package com.tradeagent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeagent.watchlist.repository.SettingsRepository;
import com.tradeagent.watchlist.repository.WatchlistRepository;
import com.tradeagent.watchlist.service.WatchlistService;

/** Verifies that the modular monolith assembles its modules into one web application. */
@SpringBootTest(properties = {
        "debug=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
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

    /** Load every capability module and expose the unchanged health contract. */
    @Test
    void assemblesHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.refreshSeconds").value(60));
    }
}
