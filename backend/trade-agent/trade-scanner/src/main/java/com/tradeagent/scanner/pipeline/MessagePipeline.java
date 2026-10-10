package com.tradeagent.scanner.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.domain.ScannerContracts.*;

/** 执行 Spring 排序后的插件，保留包含版本信息的处理轨迹。 */
@Component
public class MessagePipeline {
    private final List<MessagePlugin> plugins;
    /** 按 Spring 确定的顺序注入插件。 */
    public MessagePipeline(List<MessagePlugin> plugins) { this.plugins = List.copyOf(plugins); }

    /** 规范空白字符后执行插件，禁止替换原始证据身份。 */
    public StandardMessage execute(UUID id, RawMessage raw) {
        raw = new RawMessage(raw.sourceId(), raw.externalId(), raw.title(), raw.content(),
            raw.url(), raw.publishedAt(), List.copyOf(raw.symbols()));
        var message = new StandardMessage(id, raw,
            (raw.title() + "\n" + raw.content()).replaceAll("\\s+", " ").trim(), List.of(), List.of());
        for (var plugin : plugins) {
            var next = plugin.process(message);
            if (next == null || !id.equals(next.rawMessageId()) || !raw.equals(next.raw())) {
                throw new IllegalStateException("Plugin changed evidence identity: " + plugin.id());
            }
            var trace = new ArrayList<>(message.trace());
            trace.add(plugin.id());
            message = new StandardMessage(id, raw, next.text(), List.copyOf(next.candidates()), List.copyOf(trace));
        }
        return message;
    }
}
