package com.example.guitarapi.controllers;

import com.example.guitarapi.models.Users;
import com.example.guitarapi.repository.UserRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

    // Simple credential-based login. Expects JSON { "email": "..", "password": ".." }
    // On success sets a random token on the user record and returns a small user object + token.
    @PostMapping(path = "/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_credentials"));
        }

        Users u = this.userRepo.findByEmailAndPassword(email, password);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
        }

        // Generate token, persist on user record
        String token = UUID.randomUUID().toString();
        u.setToken(token);
        this.userRepo.save(u);

        Map<String, Object> respUser = new HashMap<>();
        respUser.put("id", u.getId());
        respUser.put("firstName", u.getFirstName());
        respUser.put("lastName", u.getLastName());
        respUser.put("email", u.getEmail());
        respUser.put("role", u.getRole());
        respUser.put("images", u.getImages());
        respUser.put("token", token);

        return ResponseEntity.ok(Map.of("status", "success", "data", respUser));
    }

    // Register new user endpoint. Assigns default "users" role to all registrations
    @PostMapping(path = "/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        // Debug log incoming body to help diagnose missing fields
        System.out.println("[AuthController.register] Received body: " + body);
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        String email = body.get("email");
        String password = body.get("password");
        String genderStr = body.get("gender");
        
        // Validate required fields
        if (firstName == null || lastName == null || email == null || password == null || genderStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_required_fields"));
        }
        
        // Check if email already exists
        Users existingUser = this.userRepo.findByEmail(email);
        if (existingUser != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email_already_exists"));
        }
        
        // Convert gender string to char
        char gender;
        try {
            gender = genderStr.charAt(0);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_gender"));
        }
        
        // Create new user with default role "users"
        Users newUser = new Users();
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEmail(email);
        newUser.setPassword(password);
        newUser.setGender(gender);
        newUser.setRole("users"); // Default role for all registrations
        
        // Generate token for immediate login
        String token = UUID.randomUUID().toString();
        newUser.setToken(token);
        
        // Save user to database
        try {
            Users savedUser = this.userRepo.save(newUser);
            
            // Return user data (similar to login response)
            Map<String, Object> respUser = new HashMap<>();
            respUser.put("id", savedUser.getId());
            respUser.put("firstName", savedUser.getFirstName());
            respUser.put("lastName", savedUser.getLastName());
            respUser.put("email", savedUser.getEmail());
            respUser.put("role", savedUser.getRole());
            respUser.put("images", savedUser.getImages());
            respUser.put("token", token);
            
            return ResponseEntity.ok(Map.of("status", "success", "data", respUser));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "registration_failed", "message", e.getMessage()));
        }
    }

    // Return current user based on Authorization: Bearer <token>
    @GetMapping(path = "/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "missing_token"));
        }
        String token = auth.substring(7);
        Users u = this.userRepo.findByToken(token);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }

        Map<String, Object> respUser = new HashMap<>();
        respUser.put("id", u.getId());
        respUser.put("firstName", u.getFirstName());
        respUser.put("lastName", u.getLastName());
        respUser.put("email", u.getEmail());
        respUser.put("role", u.getRole());
        respUser.put("images", u.getImages());
        respUser.put("token", u.getToken());

        return ResponseEntity.ok(Map.of("status", "success", "data", respUser));
    }
}
