package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.example.guitarapi.repository.UserRepo;
import com.example.guitarapi.models.Users;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepo userRepo;

    public AuthController(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    // Simple login: POST /api/login { "email":"...", "password":"..." }
    @PostMapping("/login")
    public Object login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || password == null) {
            return Map.of("ok", false, "message", "email and password required");
        }
        Users user = userRepo.findByEmailAndPassword(email, password);
        if (user == null) return Map.of("ok", false, "message", "invalid credentials");
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        userRepo.save(user);
        return Map.of("ok", true, "token", token, "role", user.getRole());
    }

    // Logout: POST /api/logout with header Authorization: Bearer <token>
    @PostMapping("/logout")
    public Object logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return Map.of("ok", false, "message", "missing token");
        String token = auth.substring(7);
        Users user = userRepo.findByToken(token);
        if (user == null) return Map.of("ok", false, "message", "invalid token");
        user.setToken(null);
        userRepo.save(user);
        return Map.of("ok", true);
    }

    // Me: GET /api/me with header Authorization: Bearer <token>
    @GetMapping("/me")
    public Object me(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return Map.of("ok", false, "message", "missing token");
        String token = auth.substring(7);
        Users user = userRepo.findByToken(token);
        if (user == null) return Map.of("ok", false, "message", "invalid token");
        // Return limited user data
        return Map.of(
            "ok", true,
            "id", user.getId(),
            "first_name", user.getFirstName(),
            "last_name", user.getLastName(),
            "email", user.getEmail(),
            "role", user.getRole()
        );
    }
}
