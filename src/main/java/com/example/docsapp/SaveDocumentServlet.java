package com.example.docsapp;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet("/SaveDocumentServlet")
public class SaveDocumentServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");

        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        try {
            StringBuilder jsonInput = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }

            JsonObject jsonObject = JsonParser.parseString(jsonInput.toString()).getAsJsonObject();
            String email = jsonObject.get("email").getAsString();
            String uniqueId = jsonObject.get("uniqueId").getAsString();

            if (email == null || email.trim().isEmpty() || uniqueId == null || uniqueId.trim().isEmpty()) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "Invalid email or uniqueId.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String getUserSql = "SELECT id FROM users WHERE email = ?";
                int userId = -1;
                try (PreparedStatement getUserStmt = conn.prepareStatement(getUserSql)) {
                    getUserStmt.setString(1, email);
                    try (ResultSet rs = getUserStmt.executeQuery()) {
                        if (rs.next()) {
                            userId = rs.getInt("id");
                        } else {
                            jsonResponse.addProperty("status", "error");
                            jsonResponse.addProperty("message", "User not found.");
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            out.print(jsonResponse.toString());
                            out.flush();
                            return;
                        }
                    }
                }

//                String sql = "INSERT INTO Documents (user_id, uniqueId, title) VALUES (?, ?, 'Untitled Document')";
//                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
//                    stmt.setInt(1, userId);
//                    stmt.setString(2, uniqueId);
//                    int rowsInserted = stmt.executeUpdate();
//
//                    if (rowsInserted > 0) {
//                        jsonResponse.addProperty("status", "success");
//                        jsonResponse.addProperty("message", "Document ID saved successfully.");
//                        response.setStatus(HttpServletResponse.SC_OK);
//                    } else {
//                        jsonResponse.addProperty("status", "error");
//                        jsonResponse.addProperty("message", "Failed to save document ID.");
//                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//                    }
//                }

                String sql = "INSERT INTO Documents (user_id, uniqueId, title) VALUES (?, ?, 'Untitled Document')";
                try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, userId);
                    stmt.setString(2, uniqueId);
                    int rowsInserted = stmt.executeUpdate();

                    if (rowsInserted > 0) {
                        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                int documentId = generatedKeys.getInt(1);

                                String contentSql = "INSERT INTO document_content (document_id, content) VALUES (?, '<br>')";
                                try (PreparedStatement contentStmt = conn.prepareStatement(contentSql)) {
                                    contentStmt.setInt(1, documentId);
                                    contentStmt.executeUpdate();
                                }

                                String permSql = "INSERT INTO permissions (user_id, document_id, role) VALUES (?, ?, 'owner')";
                                try (PreparedStatement permStmt = conn.prepareStatement(permSql)) {
                                    permStmt.setInt(1, userId);
                                    permStmt.setInt(2, documentId);
                                    permStmt.executeUpdate();
                                }

                                jsonResponse.addProperty("status", "success");
                                jsonResponse.addProperty("message", "Document ID saved successfully.");
                                response.setStatus(HttpServletResponse.SC_OK);
                            }
                        }
                    } else {
                        jsonResponse.addProperty("status", "error");
                        jsonResponse.addProperty("message", "Failed to save document ID.");
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                }

            }
        } catch (Exception e) {
            jsonResponse.addProperty("status", "error");
            jsonResponse.addProperty("message", "Server error.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        }

        out.print(jsonResponse.toString());
        out.flush();
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}