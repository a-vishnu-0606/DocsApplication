package com.example.docsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

@ServerEndpoint("/ws/{documentId}")
public class DocumentWebSocket {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;
    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static Map<Session, String> userSessions = Collections.synchronizedMap(new HashMap<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @OnOpen
    public void onOpen(Session session, @PathParam("documentId") String documentId) {
        sessions.add(session);

        String initialContent = fetchInitialContent(documentId);

        try {
            if (initialContent != null) {
                JsonObject initialContentMessage = new JsonObject();
                initialContentMessage.addProperty("type", "initialContent");
                initialContentMessage.addProperty("content", initialContent);
                session.getBasicRemote().sendText(initialContentMessage.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String fetchInitialContent(String documentId) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT content FROM document_content WHERE document_id = (SELECT id FROM Documents WHERE uniqueId = ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, documentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("content");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String fetchUserName(String email) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT username FROM users WHERE email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("username");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> data = objectMapper.readValue(message, HashMap.class);

            if (data.get("type").equals("typingIndicator")) {
                String userEmail = (String) data.get("userEmail");
                String userName = fetchUserName(userEmail);
                userSessions.put(session, userEmail);

                for (Session s : sessions) {
                    if (s.isOpen() && !s.equals(session)) {
                        JsonObject typingMessage = new JsonObject();
                        typingMessage.addProperty("type", "typingIndicator");
                        typingMessage.addProperty("userEmail", userName);
                        typingMessage.addProperty("isTyping", true);
                        s.getBasicRemote().sendText(typingMessage.toString());

                        scheduler.schedule(() -> {
                            try {
                                if (s.isOpen()) {
                                    JsonObject stopTypingMessage = new JsonObject();
                                    stopTypingMessage.addProperty("type", "typingIndicator");
                                    stopTypingMessage.addProperty("userEmail", userName);
                                    stopTypingMessage.addProperty("isTyping", false);
                                    s.getBasicRemote().sendText(stopTypingMessage.toString());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }, 10, TimeUnit.SECONDS);
                    }
                }
            } else if (data.get("type").equals("contentUpdate")) {
                for (Session s : sessions) {
                    if (s.isOpen() && !s.equals(session)) {
                        s.getBasicRemote().sendText(message);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        String userEmail = userSessions.get(session);
        if (userEmail != null) {
            userSessions.remove(session);
            for (Session s : sessions) {
                if (s.isOpen()) {
                    try {
                        JsonObject typingMessage = new JsonObject();
                        typingMessage.addProperty("type", "typingIndicator");
                        typingMessage.addProperty("userEmail", userEmail);
                        typingMessage.addProperty("isTyping", false);
                        s.getBasicRemote().sendText(typingMessage.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error in session: " + session.getId());
        throwable.printStackTrace();
    }
}