package app.js.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WikimediaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WikimediaWebSocketHandler.class);

    private final Sinks.Many<String> sink;
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    public WikimediaWebSocketHandler(Sinks.Many<String> sink) {
        this.sink = sink;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket client connected: {}", session.getId());

        Disposable subscription = sink.asFlux()
                .subscribe(data -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(data));
                        }
                    } catch (IOException e) {
                        log.error("Error sending message to WebSocket client: {}", e.getMessage());
                    }
                });

        subscriptions.put(session.getId(), subscription);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket client disconnected: {}", session.getId());

        Disposable subscription = subscriptions.remove(session.getId());
        if (subscription != null) {
            subscription.dispose();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }
}
