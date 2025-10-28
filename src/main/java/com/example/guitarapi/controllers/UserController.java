package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.guitarapi.repository.UserRepo;
import com.example.guitarapi.models.Users;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private UserRepo users;

    public UserController(UserRepo users) {
        this.users = users;
    }

    @RequestMapping(path = "/users", method = RequestMethod.GET)
    public List<Users> getAllUsers() {
        return this.users.findAll();
    }

    @RequestMapping(path = "/users/{id}", method = RequestMethod.GET)
    public Users getUserById(@PathVariable int id) {
        return this.users.findById(id).get();
    }

    @RequestMapping(path = "/users/{id}", method = RequestMethod.DELETE)
    public String deleteUser(@PathVariable int id) {
        this.users.deleteById(id);
        return "Successfully Delted";
    }

    @RequestMapping(path = "/users", method = RequestMethod.POST)
    public String createUser(
            @RequestParam("first_name") String firstName,
            @RequestParam("last_name") String lastName,
            @RequestParam("password") String password,
            @RequestParam("email") String email,
            @RequestParam("gender") char gender,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam("images") MultipartFile images) {

        try {
            // Set upload directory
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);

            }
            // Save image file
            String imageFileName = images.getOriginalFilename();
            Path target = uploadDir.resolve(imageFileName);
            images.transferTo(target.toFile());
            // Get current timestamp
            String currentTime = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // Save user with image filename
            Users newObj = new Users(0, firstName, lastName, gender, email, password, imageFileName, null, role,
                    currentTime, currentTime);
            this.users.save(newObj);
            return "Inserted Successfully!";
        } catch (Exception e) {
            logger.error("Failed to insert user", e);
            return "Failed to Insert!";
        }
    }

    @RequestMapping(path = "/users/{id}", method = RequestMethod.PUT)
    public String updateUser(
            @PathVariable int id,
            @RequestParam("first_name") String firstName,
            @RequestParam("last_name") String lastName,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "old_password", required = false) String oldPassword,
            @RequestParam("email") String email,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "images", required = false) MultipartFile images) {

        try {
            return this.users.findById(id).map((item) -> {
                item.setFirstName(firstName);
                item.setLastName(lastName);
                if (gender != null && !gender.isEmpty()) {
                    item.setGender(gender.charAt(0));
                }
                item.setEmail(email);

                // Validate old password before updating to new password
                if (password != null && !password.isEmpty()) {
                    if (oldPassword == null || oldPassword.isEmpty()) {
                        return "Old password is required to change password";
                    }
                    if (!item.getPassword().equals(oldPassword)) {
                        return "Old password is incorrect";
                    }
                    item.setPassword(password);
                }
                if (role != null)
                    item.setRole(role);

                if (images != null && !images.isEmpty()) {
                    try {
                        Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
                        if (!Files.exists(uploadDir)) {
                            Files.createDirectories(uploadDir);
                        }
                        String imageFileName = images.getOriginalFilename();
                        Path target = uploadDir.resolve(imageFileName);
                        images.transferTo(target.toFile());
                        item.setImages(imageFileName);
                    } catch (Exception e) {
                        logger.error("Failed to update user image", e);
                        return "Failed to update image: " + e.getMessage();
                    }
                }

                // Update timestamp
                String currentTime = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                item.setUpdatedAt(currentTime);

                this.users.save(item);
                return "Updated Successfully!";
            }).orElse("User not found!");
        } catch (Exception ex) {
            logger.error("Failed to update user", ex);
            return "Failed to update user: " + ex.getMessage();
        }
    }
}