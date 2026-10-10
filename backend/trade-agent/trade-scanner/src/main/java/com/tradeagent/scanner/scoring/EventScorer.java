package com.tradeagent.scanner.scoring;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.domain.ScannerContracts.*;

/** 实现版本化确定性评分策略，仅将配置的官方来源标记为 CONFIRMED。 */
@Component
public class EventScorer {
    private final List<String> officialSources;
    /** 从部署配置读取来源可信策略，不依赖请求中的可信声明。 */
    public EventScorer(@Value("${tradeagent.scanner.official-sources:}") List<String> officialSources) {
        this.officialSources = officialSources;
    }
    /** 保守评估证据，普通股票关联不等同于自选股匹配。 */
    public Score score(EventCandidate candidate, String sourceId) {
        boolean trusted = sourceId != null && !sourceId.isBlank() && officialSources.contains(sourceId);
        int credibility = trusted ? 25 : 5;
        int impact = switch (candidate.type()) { case "RISK", "EARNINGS" -> 25; default -> 20; };
        int relevance = candidate.symbols().isEmpty() ? 0 : 10;
        int novelty = 10;
        int persistence = 5;
        return new Score(credibility, impact, relevance, novelty, persistence,
            credibility + impact + relevance + novelty + persistence,
            trusted ? "CONFIRMED" : "UNVERIFIED",
            List.of(trusted ? "配置的官方来源" : "来源尚未验证", "关键词规则分类: " + candidate.type()), "rules:v1");
    }
}
