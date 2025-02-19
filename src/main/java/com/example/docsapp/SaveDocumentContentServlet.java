package com.example.docsapp;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.text.SimpleDateFormat;

@WebServlet("/SaveDocumentContentServlet")
public class SaveDocumentContentServlet extends HttpServlet {

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
        JsonObject result = new JsonObject();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("email") == null) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Session not found or invalid.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(error.toString());
            return;
        }

        try (BufferedReader reader = request.getReader()) {
            StringBuilder jsonInput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }

            JsonObject requestData = new com.google.gson.JsonParser().parse(jsonInput.toString()).getAsJsonObject();
            String uniqueId = requestData.get("uniqueId").getAsString();
            String content = requestData.get("content").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String getIdQuery = "SELECT id FROM Documents WHERE uniqueId = ?";
                try (PreparedStatement idStmt = conn.prepareStatement(getIdQuery)) {
                    idStmt.setString(1, uniqueId);
                    ResultSet rs = idStmt.executeQuery();

                    if (rs.next()) {
                        int documentId = rs.getInt("id");

                        String query = "INSERT INTO document_content (document_id, content) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE content = VALUES(content)";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setInt(1, documentId);
                            stmt.setString(2, content);
                            stmt.executeUpdate();

                            String getTimestampQuery = "SELECT last_updated FROM document_content WHERE document_id = ?";
                            try (PreparedStatement timestampStmt = conn.prepareStatement(getTimestampQuery)) {
                                timestampStmt.setInt(1, documentId);
                                ResultSet timestampRs = timestampStmt.executeQuery();
                                if (timestampRs.next()) {
                                    String lastUpdated = timestampRs.getString("last_updated");
                                    result.addProperty("status", "success");
                                    result.addProperty("last_updated", lastUpdated);
                                }
                            }
                        }
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Document not found.");
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

