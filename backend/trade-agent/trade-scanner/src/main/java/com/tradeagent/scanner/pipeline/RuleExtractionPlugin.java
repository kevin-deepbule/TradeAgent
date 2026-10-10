package com.tradeagent.scanner.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.domain.ScannerContracts.*;

/** 通过中文关键词保守抽取事件，未识别文本仅保留为证据。 */
@Component
@Order(100)
public class RuleExtractionPlugin implements MessagePlugin {
    /** 返回确定性抽取契约的版本标识。 */
    public String id() { return "rule-extract:v1"; }

    /** 拆分独立事件类型并保留原始来源引用。 */
    public StandardMessage process(StandardMessage message) {
        var candidates = new ArrayList<>(message.candidates());
        String text = message.text();
        for (var rule : List.of(new String[]{"EARNINGS", "业绩预告", "净利润", "业绩快报"},
                new String[]{"BUYBACK", "回购"}, new String[]{"CONTRACT", "中标", "重大合同"},
                new String[]{"RISK", "立案", "处罚", "退市"},
                new String[]{"OWNERSHIP", "控制人变更", "股权转让"},
                new String[]{"MARKET_MOVE", "行情异动"})) {
            for (int i = 1; i < rule.length; i++) {
                if (text.contains(rule[i])) {
                    candidates.add(new EventCandidate(rule[0], message.raw().title(),
                        text.substring(0, Math.min(1000, text.length())), message.raw().publishedAt(),
                        message.raw().symbols()));
                    break;
                }
            }
        }
        return new StandardMessage(message.rawMessageId(), message.raw(), text, candidates, message.trace());
    }
}
