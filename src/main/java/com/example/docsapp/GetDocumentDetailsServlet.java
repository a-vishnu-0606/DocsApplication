package com.example.docsapp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import com.google.gson.*;
import java.util.Date;

@WebServlet("/GetDocumentDetailsServlet")
public class GetDocumentDetailsServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;
    private static final String JWT_SECRET = System.getProperty("JWT_SECRET", System.getenv("JWT_SECRET"));

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
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
            BufferedReader reader = request.getReader();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            String uniqueId = jsonObject.get("uniqueId").getAsString();
            String email = jsonObject.get("email").getAsString();


            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String query = "SELECT d.id, d.title FROM Documents d WHERE d.uniqueId = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, uniqueId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        int documentId = rs.getInt("id");
                        String title = rs.getString("title");

                        query = "SELECT id FROM users WHERE email = ?";
                        try (PreparedStatement userStmt = conn.prepareStatement(query)) {
                            userStmt.setString(1, email);
                            ResultSet userRs = userStmt.executeQuery();

                            if (userRs.next()) {
                                int userId = userRs.getInt("id");

                                query = "SELECT role FROM permissions WHERE user_id = ? AND document_id = ?";
                                try (PreparedStatement permStmt = conn.prepareStatement(query)) {
                                    permStmt.setInt(1, userId);
                                    permStmt.setInt(2, documentId);
                                    ResultSet permRs = permStmt.executeQuery();

                                    if (permRs.next()) {
                                        String role = permRs.getString("role");
                                        JsonObject result = new JsonObject();
                                        result.addProperty("status", "success");
                                        result.addProperty("title", title);
                                        result.addProperty("role", role);
                                        out.print(result);
                                    } else {
                                        JsonObject error = new JsonObject();
                                        error.addProperty("status", "error");
                                        error.addProperty("message", "You do not have permission to access this document.");
                                        out.print(error);
                                    }
                                }
                            } else {
                                JsonObject error = new JsonObject();
                                error.addProperty("status", "error");
                                error.addProperty("message", "User not found.");
                                out.print(error);
                            }
                        }
                    } else {
                        JsonObject error = new JsonObject();
                        error.addProperty("status", "error");
                        error.addProperty("message", "Document not found.");
                        out.print(error);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Database error occurred.");
            out.print(error);
        }
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