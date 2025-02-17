package com.example.docsapp;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.text.StringEscapeUtils;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/SignupServlet")
public class SignupServlet extends HttpServlet {


    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");

        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; frame-ancestors 'self'; form-action 'self';");

        StringBuilder jsonInput = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonInput.append(line);
        }

        JsonObject jsonObject = JsonParser.parseString(jsonInput.toString()).getAsJsonObject();
        String username = StringEscapeUtils.escapeHtml4(jsonObject.get("username").getAsString());
        String email = StringEscapeUtils.escapeHtml4(jsonObject.get("email").getAsString());
        String password = StringEscapeUtils.escapeHtml4(jsonObject.get("password").getAsString());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();

        if (username == null || username.trim().isEmpty() || email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            jsonResponse.addProperty("message", "All fields are required.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        if (!isValidInput(username) || !isValidInput(email) || !isValidInput(password)) {
            jsonResponse.addProperty("message", "Invalid input detected.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                String checkEmailQuery = "SELECT COUNT(*) FROM users WHERE email = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkEmailQuery)) {
                    checkStmt.setString(1, email);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        jsonResponse.addProperty("message", "Email already exists, try logging in.");
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response.getWriter().write(jsonResponse.toString());
                        return;
                    }
                }

                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, email);
                    stmt.setString(3, hashedPassword);

                    int rowsInserted = stmt.executeUpdate();
                    if (rowsInserted > 0) {
                        jsonResponse.addProperty("message", "Signup successful!");
                    } else {
                        jsonResponse.addProperty("message", "Signup failed. Try again.");
                    }
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            jsonResponse.addProperty("message", "Error: " + e.getMessage());
        }

        response.getWriter().write(jsonResponse.toString());
    }

    private boolean isValidInput(String input) {
        String regex = "<script.*?>.*?</script.*?>|javascript:|on\\w+=";
        return !input.matches(regex);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}