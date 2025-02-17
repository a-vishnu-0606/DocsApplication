package com.example.docsapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import com.google.gson.*;

@WebServlet("/UpdatePermissionsServlet")
public class UpdatePermissionsServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        JsonObject jsonResponse = new JsonObject();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            JsonParser parser = new JsonParser();
            JsonObject requestBody = parser.parse(request.getReader()).getAsJsonObject();

            String email = requestBody.get("email").getAsString();
            String documentId = requestBody.get("documentId").getAsString();
            String role = requestBody.get("role").getAsString();

            int userId = getUserIdByEmail(conn, email);
            if (userId == -1) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "User not found.");
                out.print(jsonResponse.toString());
                return;
            }

            int docId = getDocumentIdByUniqueId(conn, documentId);
            if (docId == -1) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "Document not found.");
                out.print(jsonResponse.toString());
                return;
            }

            String query = "INSERT INTO permissions (user_id, document_id, role) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE role = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, docId);
                stmt.setString(3, role);
                stmt.setString(4, role);
                stmt.executeUpdate();
            }

            jsonResponse.addProperty("status", "success");
            out.print(jsonResponse.toString());

        } catch (Exception e) {
            e.printStackTrace();
            jsonResponse.addProperty("status", "error");
            jsonResponse.addProperty("message", "Database error occurred.");
            out.print(jsonResponse.toString());
        }
    }

    private int getUserIdByEmail(Connection conn, String email) throws SQLException {
        String query = "SELECT id FROM users WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }

    private int getDocumentIdByUniqueId(Connection conn, String uniqueId) throws SQLException {
        String query = "SELECT id FROM Documents WHERE uniqueId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uniqueId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
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