package com.example.docsapp;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/ToggleFavouriteServlet")
public class ToggleFavouriteServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject result = new JsonObject();

        try (BufferedReader reader = request.getReader()) {
            StringBuilder jsonInput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }

            JsonObject requestData = new com.google.gson.JsonParser().parse(jsonInput.toString()).getAsJsonObject();
            String uniqueId = requestData.get("uniqueId").getAsString();
            String email = requestData.get("email").getAsString();
            boolean isFavorited = requestData.get("isFavorited").getAsBoolean();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String getUserIdQuery = "SELECT id FROM users WHERE email = ?";
                try (PreparedStatement userIdStmt = conn.prepareStatement(getUserIdQuery)) {
                    userIdStmt.setString(1, email);
                    ResultSet userIdRs = userIdStmt.executeQuery();

                    if (userIdRs.next()) {
                        int userId = userIdRs.getInt("id");

                        String getDocumentIdQuery = "SELECT id FROM Documents WHERE uniqueId = ?";
                        try (PreparedStatement documentIdStmt = conn.prepareStatement(getDocumentIdQuery)) {
                            documentIdStmt.setString(1, uniqueId);
                            ResultSet documentIdRs = documentIdStmt.executeQuery();

                            if (documentIdRs.next()) {
                                int documentId = documentIdRs.getInt("id");

                                if (isFavorited) {
                                    String insertFavouriteQuery = "INSERT INTO favourites (user_id, document_id) VALUES (?, ?)";
                                    try (PreparedStatement insertFavouriteStmt = conn.prepareStatement(insertFavouriteQuery)) {
                                        insertFavouriteStmt.setInt(1, userId);
                                        insertFavouriteStmt.setInt(2, documentId);
                                        insertFavouriteStmt.executeUpdate();
                                    }
                                } else {
                                    String deleteFavouriteQuery = "DELETE FROM favourites WHERE user_id = ? AND document_id = ?";
                                    try (PreparedStatement deleteFavouriteStmt = conn.prepareStatement(deleteFavouriteQuery)) {
                                        deleteFavouriteStmt.setInt(1, userId);
                                        deleteFavouriteStmt.setInt(2, documentId);
                                        deleteFavouriteStmt.executeUpdate();
                                    }
                                }

                                result.addProperty("status", "success");
                            } else {
                                result.addProperty("status", "error");
                                result.addProperty("message", "Document not found.");
                            }
                        }
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "User not found.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.addProperty("status", "error");
            result.addProperty("message", "Database error occurred.");
        }
        out.print(result.toString());
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