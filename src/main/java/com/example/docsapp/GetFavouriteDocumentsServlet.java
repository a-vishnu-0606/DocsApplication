package com.example.docsapp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/GetFavouriteDocumentsServlet")
public class GetFavouriteDocumentsServlet extends HttpServlet {

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

            JsonObject requestData = JsonParser.parseString(jsonInput.toString()).getAsJsonObject();
            String email = requestData.get("email").getAsString();


            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String getUserIdQuery = "SELECT id FROM users WHERE email = ?";
                try (PreparedStatement userIdStmt = conn.prepareStatement(getUserIdQuery)) {
                    userIdStmt.setString(1, email);
                    ResultSet userIdRs = userIdStmt.executeQuery();

                    if (userIdRs.next()) {
                        int userId = userIdRs.getInt("id");

                        String getFavouritesQuery = "SELECT d.id, d.title, d.uniqueId " +
                                "FROM Documents d " +
                                "JOIN favourites f ON d.id = f.document_id " +
                                "WHERE f.user_id = ?";
                        try (PreparedStatement favouritesStmt = conn.prepareStatement(getFavouritesQuery)) {
                            favouritesStmt.setInt(1, userId);
                            ResultSet favouritesRs = favouritesStmt.executeQuery();

                            List<JsonObject> documents = new ArrayList<>();
                            while (favouritesRs.next()) {
                                JsonObject doc = new JsonObject();
                                doc.addProperty("id", favouritesRs.getInt("id"));
                                doc.addProperty("title", favouritesRs.getString("title"));
                                doc.addProperty("uniqueId", favouritesRs.getString("uniqueId"));
                                documents.add(doc);
                            }

                            result.addProperty("status", "success");
                            result.add("documents", new com.google.gson.JsonParser().parse(new com.google.gson.Gson().toJson(documents)));
                        }
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "User not found.");
                        System.out.println("User not found for email: " + email);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                result.addProperty("status", "error");
                result.addProperty("message", "Database error occurred.");
                System.out.println("Database error: " + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.addProperty("status", "error");
            result.addProperty("message", "An unexpected error occurred.");
            System.out.println("Unexpected error: " + e.getMessage());
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