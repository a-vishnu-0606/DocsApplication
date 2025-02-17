package com.example.docsapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.*;

@WebServlet("/GetUserDocumentsServlet")
public class GetUserDocumentsServlet extends HttpServlet {

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
            String username = jsonObject.get("email").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String userQuery = "SELECT id FROM users WHERE email = ?";
                try (PreparedStatement stmt = conn.prepareStatement(userQuery)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        int userId = rs.getInt("id");

                        String docQuery = "SELECT id, title, uniqueId FROM Documents WHERE user_id = ?";
                        try (PreparedStatement docStmt = conn.prepareStatement(docQuery)) {
                            docStmt.setInt(1, userId);
                            ResultSet docRs = docStmt.executeQuery();

                            List<JsonObject> documents = new ArrayList<>();
                            while (docRs.next()) {
                                JsonObject doc = new JsonObject();
                                doc.addProperty("id", docRs.getInt("id"));
                                doc.addProperty("title", docRs.getString("title"));
                                doc.addProperty("uniqueId", docRs.getString("uniqueId"));
                                documents.add(doc);
                            }

                            JsonObject result = new JsonObject();
                            result.addProperty("status", "success");
                            result.add("documents", new Gson().toJsonTree(documents));
                            out.print(result);
                            return;
                        }
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
