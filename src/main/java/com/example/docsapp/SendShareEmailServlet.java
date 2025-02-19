package com.example.docsapp;

import java.io.IOException;
import java.util.Properties;

import com.google.gson.JsonObject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.util.Date;

import static java.lang.System.out;

@WebServlet("/SendShareEmailServlet")
public class SendShareEmailServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JWT_SECRET = System.getProperty("JWT_SECRET", System.getenv("JWT_SECRET"));


    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token");
        response.setHeader("Access-Control-Allow-Credentials", "true");


        JSONObject jsonResponse = new JSONObject();

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

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = request.getReader().readLine()) != null) {
                sb.append(s);
            }
            JSONObject jsonRequest = new JSONObject(sb.toString());

            String email = jsonRequest.getString("email");
            String documentId = jsonRequest.getString("documentId");
            String accessLevel = jsonRequest.getString("accessLevel");

            sendEmail(email, documentId, accessLevel);

            jsonResponse.put("status", "success");
        } catch (Exception e) {
            jsonResponse.put("status", "error");
            jsonResponse.put("message", e.getMessage());
        }

        response.getWriter().write(jsonResponse.toString());
    }

    private void sendEmail(String email, String documentId, String accessLevel) {
        final String username = System.getProperty("EMAIL_USERNAME", System.getenv("EMAIL_USERNAME"));
        final String password = System.getProperty("EMAIL_PASSWORD", System.getenv("EMAIL_PASSWORD"));

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new jakarta.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Document Shared with You");
            message.setText("A document has been shared with you.\n\n" +
                    "Document ID: " + documentId + "\n" +
                    "Access Level: " + accessLevel +"\n\n"+ "Link: "+"http://localhost:4200/document/"+documentId +"\n\n"+"Thanks Regards \n"+"Vishnu A");


            Transport.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException(e);
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
