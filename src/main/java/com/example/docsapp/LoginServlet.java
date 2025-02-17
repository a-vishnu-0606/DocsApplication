package com.example.docsapp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.text.StringEscapeUtils;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final String DB_URL = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static final String DB_USER = System.getProperty("DB_USER", System.getenv("DB_USER"));;
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));;
    private static final String JWT_SECRET = System.getProperty("JWT_SECRET", System.getenv("JWT_SECRET"));

    private static final ConcurrentHashMap<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> blockedIPs = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_TIME = TimeUnit.MINUTES.toMillis(5);

    private static final int SC_TOO_MANY_REQUESTS = 429;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setContentType("application/json");

        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; frame-ancestors 'self'; form-action 'self';");

        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        String clientIP = request.getRemoteAddr();

        if (isIPBlocked(clientIP)) {
            jsonResponse.addProperty("status", "error");
            jsonResponse.addProperty("message", "Too many failed attempts. Please try again later.");
            response.setStatus(SC_TOO_MANY_REQUESTS);
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        try {
            StringBuilder jsonInput = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }

            JsonObject jsonObject = JsonParser.parseString(jsonInput.toString()).getAsJsonObject();
            String email = StringEscapeUtils.escapeHtml4(jsonObject.get("email").getAsString());
            String password = StringEscapeUtils.escapeHtml4(jsonObject.get("password").getAsString());

            if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "Email and password are required.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            if (!isValidInput(email) || !isValidInput(password)) {
                jsonResponse.addProperty("status", "error");
                jsonResponse.addProperty("message", "Invalid input detected.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(jsonResponse.toString());
                out.flush();
                return;
            }

            if (validateUser(email, password)) {
                failedAttempts.remove(clientIP);

                String jwt = Jwts.builder()
                        .setSubject(email)
                        .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                        .signWith(SignatureAlgorithm.HS512, JWT_SECRET)
                        .compact();

                Cookie jwtCookie = new Cookie("jwt", jwt);
                jwtCookie.setHttpOnly(true);
                jwtCookie.setSecure(true);
                jwtCookie.setPath("/");
                response.addCookie(jwtCookie);

                String csrfToken = UUID.randomUUID().toString();
                Cookie csrfCookie = new Cookie("X-CSRF-Token", csrfToken);
                csrfCookie.setHttpOnly(true);
                csrfCookie.setSecure(true);
                csrfCookie.setPath("/");
                response.addCookie(csrfCookie);

                jsonResponse.addProperty("status", "success");
                jsonResponse.addProperty("message", "Login successful!");
                jsonResponse.addProperty("csrfToken", csrfToken);
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                int attempts = failedAttempts.getOrDefault(clientIP, 0) + 1;
                failedAttempts.put(clientIP, attempts);

                if (attempts >= MAX_ATTEMPTS) {
                    blockedIPs.put(clientIP, System.currentTimeMillis() + BLOCK_TIME);
                    jsonResponse.addProperty("status", "error");
                    jsonResponse.addProperty("message", "Too many failed attempts. Please try again later.");
                    response.setStatus(SC_TOO_MANY_REQUESTS);
                } else {
                    jsonResponse.addProperty("status", "error");
                    jsonResponse.addProperty("message", "Invalid email or password.");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }
            }
        } catch (Exception e) {
            jsonResponse.addProperty("status", "error");
            jsonResponse.addProperty("message", "Internal server error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        out.print(jsonResponse.toString());
        out.flush();
    }

    private boolean validateUser(String email, String password) {
        boolean isValid = false;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT password FROM users WHERE email = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password");
                isValid = BCrypt.checkpw(password, hashedPassword);
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isValid;
    }

    private boolean isIPBlocked(String clientIP) {
        Long blockEndTime = blockedIPs.get(clientIP);
        if (blockEndTime != null && System.currentTimeMillis() < blockEndTime) {
            return true;
        } else if (blockEndTime != null) {
            blockedIPs.remove(clientIP);
        }
        return false;
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
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}