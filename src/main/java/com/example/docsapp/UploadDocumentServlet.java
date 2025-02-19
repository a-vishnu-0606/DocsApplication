package com.example.docsapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import com.google.gson.*;

@WebServlet("/UploadDocumentServlet")
public class UploadDocumentServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("email") == null) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Session not found or invalid.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(error.toString());
            return;
        }

        JsonObject requestBody = new JsonParser().parse(request.getReader()).getAsJsonObject();
        String email = requestBody.get("email").getAsString();
        String uniqueId = requestBody.get("uniqueId").getAsString();
        String title = requestBody.get("title").getAsString();
        String content = requestBody.get("content").getAsString();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String documentQuery = "INSERT INTO Documents (user_id, title, uniqueId) VALUES ((SELECT id FROM users WHERE email = ?), ?, ?)";
            try (PreparedStatement documentStmt = conn.prepareStatement(documentQuery, Statement.RETURN_GENERATED_KEYS)) {
                documentStmt.setString(1, email);
                documentStmt.setString(2, title);
                documentStmt.setString(3, uniqueId);
                documentStmt.executeUpdate();

                ResultSet generatedKeys = documentStmt.getGeneratedKeys();
                int documentId = -1;
                if (generatedKeys.next()) {
                    documentId = generatedKeys.getInt(1);
                }

                if (documentId != -1) {
                    String contentQuery = "INSERT INTO document_content (document_id, content) VALUES (?, ?)";
                    try (PreparedStatement contentStmt = conn.prepareStatement(contentQuery)) {
                        contentStmt.setInt(1, documentId);
                        contentStmt.setString(2, content);
                        contentStmt.executeUpdate();
                    }

                    String permissionsQuery = "INSERT INTO permissions (user_id, document_id, role) VALUES ((SELECT id FROM users WHERE email = ?), ?, 'owner')";
                    try (PreparedStatement permissionsStmt = conn.prepareStatement(permissionsQuery)) {
                        permissionsStmt.setString(1, email);
                        permissionsStmt.setInt(2, documentId);
                        permissionsStmt.executeUpdate();
                    }

                    JsonObject result = new JsonObject();
                    result.addProperty("status", "success");
                    result.addProperty("uniqueId", uniqueId);
                    out.print(result.toString());
                } else {
                    throw new SQLException("Failed to retrieve document ID.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Database error occurred: " + e.getMessage());
            out.print(error.toString());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}