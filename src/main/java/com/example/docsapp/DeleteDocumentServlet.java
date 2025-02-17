package com.example.docsapp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/DeleteDocumentServlet")
public class DeleteDocumentServlet extends HttpServlet {

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

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            JsonObject json = new JsonParser().parse(request.getReader()).getAsJsonObject();
            String email = json.get("email").getAsString();
            String uniqueId = json.get("uniqueId").getAsString();

            // Delete the document
            String deleteQuery = "DELETE FROM Documents WHERE uniqueId = ? AND user_id = (SELECT id FROM users WHERE email = ?)";
            try (PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
                stmt.setString(1, uniqueId);
                stmt.setString(2, email);
                int rowsAffected = stmt.executeUpdate();

                JsonObject result = new JsonObject();
                if (rowsAffected > 0) {
                    result.addProperty("status", "success");
                    result.addProperty("message", "Document deleted successfully.");
                } else {
                    result.addProperty("status", "error");
                    result.addProperty("message", "Document not found or you do not have permission to delete it.");
                }
                out.print(result.toString());
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