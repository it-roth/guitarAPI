package com.example.guitarapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Determine uploads directory. Prefer <working-dir>/uploads, but fall back to parent/uploads
        String userDir = System.getProperty("user.dir");
        String uploadsPath = userDir + "/uploads/";
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(uploadsPath);
            if (!java.nio.file.Files.exists(p)) {
                java.nio.file.Path parent = java.nio.file.Paths.get(userDir).getParent();
                if (parent != null) {
                    java.nio.file.Path parentUploads = parent.resolve("uploads");
                    if (java.nio.file.Files.exists(parentUploads)) {
                        uploadsPath = parentUploads.toAbsolutePath().toString() + java.io.File.separator;
                    }
                }
            }
        } catch (Exception ex) {
            // If anything goes wrong, fall back to the original heuristic
            uploadsPath = userDir + "/uploads/";
        }

        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadsPath);
    }
}
