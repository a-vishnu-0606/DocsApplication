package com.example.docsapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import com.google.gson.*;

@WebServlet("/GetDocumentContentServlet")
public class GetDocumentContentServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try {
            BufferedReader reader = request.getReader();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            String uniqueId = jsonObject.get("uniqueId").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String getDocumentIdQuery = "SELECT id FROM Documents WHERE uniqueId = ?";
                try (PreparedStatement stmt = conn.prepareStatement(getDocumentIdQuery)) {
                    stmt.setString(1, uniqueId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        int documentId = rs.getInt("id");

                        String getContentQuery = "SELECT content FROM document_content WHERE document_id = ?";
                        try (PreparedStatement contentStmt = conn.prepareStatement(getContentQuery)) {
                            contentStmt.setInt(1, documentId);
                            ResultSet contentRs = contentStmt.executeQuery();

                            JsonObject result = new JsonObject();
                            if (contentRs.next()) {
                                result.addProperty("status", "success");
                                result.addProperty("content", contentRs.getString("content"));
                            } else {
                                result.addProperty("status", "no_content");
                                result.addProperty("message", "No content found for this document.");
                            }
                            out.print(result);
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