package com.tradeagent.websocket;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.config.AppProperties;
import com.tradeagent.dto.KlinePayload;
import com.tradeagent.dto.StockIdentity;
import com.tradeagent.service.StockService;
import com.tradeagent.util.TimeUtil;

/** Push refreshed stock K-line payloads over the frontend WebSocket path. */
@Component
public class StockWebSocketHandler extends TextWebSocketHandler {
    private final StockService stockService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, Future<?>> sessions = new ConcurrentHashMap<>();

    /** Create the WebSocket handler with stock workflow and JSON support. */
    public StockWebSocketHandler(StockService stockService, AppProperties properties, ObjectMapper objectMapper) {
        this.stockService = stockService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Resolve the requested symbol and start the push loop for a new session. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Future<?> future = executorService.submit(() -> streamStock(session));
        sessions.put(session.getId(), future);
    }

    /** Stop the push loop when the client disconnects. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Future<?> future = sessions.remove(session.getId());
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Cancel the push loop when transport errors occur. */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Future<?> future = sessions.remove(session.getId());
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Resolve and repeatedly send the latest payload while the connection is open. */
    private void streamStock(WebSocketSession session) {
        try {
            String query = queryFromPath(session);
            StockIdentity resolved = stockService.resolveStock(query);
            stockService.cache().addSymbol(resolved.symbol, resolved.name);
            stockService.refreshSymbol(resolved.symbol);

            while (session.isOpen() && !Thread.currentThread().isInterrupted()) {
                KlinePayload payload = stockService.cache().get(resolved.symbol);
                if (payload == null) {
                    payload = stockService.refreshSymbol(resolved.symbol);
                } else {
                    payload = payload.withName(resolved.name);
                }
                sendJson(session, payload);
                Thread.sleep(properties.refreshSecondsOrDefault() * 1000L);
            }
        } catch (ResponseStatusException exc) {
            sendJsonQuietly(session, errorPayload(exc.getReason()));
            closeQuietly(session);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        } catch (Exception exc) {
            sendJsonQuietly(session, errorPayload(exc.getMessage()));
            closeQuietly(session);
        }
    }

    /** Extract the stock query from the registered WebSocket URL. */
    private String queryFromPath(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String prefix = "/ws/stocks/";
        if (!path.startsWith(prefix)) {
            return "";
        }
        return URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
    }

    /** Send a JSON message with synchronization around the session writer. */
    private void sendJson(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    /** Send JSON while swallowing secondary errors during close handling. */
    private void sendJsonQuietly(WebSocketSession session, Object payload) {
        try {
            sendJson(session, payload);
        } catch (IOException ignored) {
            // Closing sockets can race with the final error payload.
        }
    }

    /** Close the session while ignoring secondary close errors. */
    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException ignored) {
            // Nothing else can be done once the connection is closing.
        }
    }

    /** Build an error payload that matches the stock WebSocket response shape. */
    private KlinePayload errorPayload(String message) {
        KlinePayload payload = new KlinePayload();
        payload.symbol = "";
        payload.name = "";
        payload.updatedAt = TimeUtil.nowIsoSeconds();
        payload.source = null;
        payload.error = message == null ? "股票数据获取失败。" : message;
        return payload;
    }
}

