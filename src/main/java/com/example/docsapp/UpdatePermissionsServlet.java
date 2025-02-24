package com.example.docsapp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import com.google.gson.*;
import java.util.Date;

@WebServlet("/UpdatePermissionsServlet")
public class UpdatePermissionsServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));
    private static final String JWT_SECRET = System.getProperty("JWT_SECRET", System.getenv("JWT_SECRET"));

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        JsonObject jsonResponse = new JsonObject();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("email") == null) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Session not found or invalid.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(error.toString());
            return;
        }

        Cookie[] cookies = request.getCookies();
        String jwt = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("jwt")) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        if (jwt == null) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "No JWT found.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(error.toString());
            return;
        }

        Claims claims = Jwts.parser()
                .setSigningKey(JWT_SECRET)
                .parseClaimsJws(jwt)
                .getBody();

        if (claims.getExpiration().before(new Date())) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "JWT has expired.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(error.toString());
            return;
        }

        String loggedInUserEmail = (String) session.getAttribute("email");

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

            String loggedInUsername = getUsernameByEmail(conn, loggedInUserEmail);
            if (loggedInUsername == null) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "Logged-in user not found.");
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

            String notificationMessage = loggedInUsername + " has shared the document '" + getDocumentTitle(conn, docId) + "' with you as " + role + ".";
            insertNotification(conn, userId, docId, role, notificationMessage);

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

    private String getDocumentTitle(Connection conn, int docId) throws SQLException {
        String query = "SELECT title FROM Documents WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, docId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("title");
            }
        }
        return "Untitled Document";
    }

    private String getUsernameByEmail(Connection conn, String email) throws SQLException {
        String query = "SELECT username FROM users WHERE email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        }
        return null;
    }

    private void insertNotification(Connection conn, int userId, int docId, String role, String message) throws SQLException {
        String query = "INSERT INTO notifications (user_id, document_id, role, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, docId);
            stmt.setString(3, role);
            stmt.setString(4, message);
            stmt.executeUpdate();
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