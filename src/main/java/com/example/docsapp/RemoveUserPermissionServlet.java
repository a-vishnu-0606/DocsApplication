package com.example.docsapp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Date;
import com.google.gson.*;

@WebServlet("/RemoveUserPermissionServlet")
public class RemoveUserPermissionServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));
    private static final String JWT_SECRET = System.getProperty("JWT_SECRET", System.getenv("JWT_SECRET"));

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

        try {
            JsonObject requestBody = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String uniqueId = requestBody.get("uniqueId").getAsString();
            String email = requestBody.get("email").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String userIdQuery = "SELECT id FROM users WHERE email = ?";
                try (PreparedStatement userIdStmt = conn.prepareStatement(userIdQuery)) {
                    userIdStmt.setString(1, email);
                    ResultSet userIdRs = userIdStmt.executeQuery();
                    if (!userIdRs.next()) {
                        JsonObject error = new JsonObject();
                        error.addProperty("status", "error");
                        error.addProperty("message", "User not found.");
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        out.print(error.toString());
                        return;
                    }
                    int userId = userIdRs.getInt("id");

                    String documentIdQuery = "SELECT id FROM Documents WHERE uniqueId = ?";
                    try (PreparedStatement documentIdStmt = conn.prepareStatement(documentIdQuery)) {
                        documentIdStmt.setString(1, uniqueId);
                        ResultSet documentIdRs = documentIdStmt.executeQuery();
                        if (!documentIdRs.next()) {
                            JsonObject error = new JsonObject();
                            error.addProperty("status", "error");
                            error.addProperty("message", "Document not found.");
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            out.print(error.toString());
                            return;
                        }
                        int documentId = documentIdRs.getInt("id");
                        String deletePermissionQuery = "DELETE FROM permissions WHERE user_id = ? AND document_id = ?";
                        try (PreparedStatement deletePermissionStmt = conn.prepareStatement(deletePermissionQuery)) {
                            deletePermissionStmt.setInt(1, userId);
                            deletePermissionStmt.setInt(2, documentId);
                            int rowsDeleted = deletePermissionStmt.executeUpdate();
                            if (rowsDeleted > 0) {
                                JsonObject success = new JsonObject();
                                success.addProperty("status", "success");
                                out.print(success.toString());
                            } else {
                                JsonObject error = new JsonObject();
                                error.addProperty("status", "error");
                                error.addProperty("message", "Permission not found.");
                                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                                out.print(error.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Database error occurred.");
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