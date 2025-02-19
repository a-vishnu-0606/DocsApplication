package com.example.docsapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.*;

@WebServlet("/GetSharedUsersServlet")
public class GetSharedUsersServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

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

        try {
            JsonObject requestBody = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String uniqueId = requestBody.get("uniqueId").getAsString();

            List<Map<String, String>> sharedUsers = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT u.email, p.role " +
                        "FROM permissions p " +
                        "JOIN users u ON p.user_id = u.id " +
                        "JOIN Documents d ON p.document_id = d.id " +
                        "WHERE d.uniqueId = ? AND p.role != 'owner'";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uniqueId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        Map<String, String> user = new HashMap<>();
                        user.put("email", rs.getString("email"));
                        user.put("role", rs.getString("role"));
                        sharedUsers.add(user);
                    }
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "success");
            JsonArray usersArray = new JsonArray();
            for (Map<String, String> user : sharedUsers) {
                JsonObject userObj = new JsonObject();
                userObj.addProperty("email", user.get("email"));
                userObj.addProperty("role", user.get("role"));
                usersArray.add(userObj);
            }
            result.add("sharedUsers", usersArray);
            out.print(result.toString());
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