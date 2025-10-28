package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.guitarapi.repository.ProductRepo;
import com.example.guitarapi.models.Products;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private ProductRepo products;
    private com.example.guitarapi.repository.OrderItemRepo orderItemRepo;

    public ProductController(ProductRepo products, com.example.guitarapi.repository.OrderItemRepo orderItemRepo) {
        this.products = products;
        this.orderItemRepo = orderItemRepo;
    }

    @RequestMapping(path = "/products/brands", method = RequestMethod.GET)
    public List<String> getBrands() {
        try {
            return this.products.findDistinctBrands();
        } catch (Exception e) {
            logger.error("Failed to fetch product brands", e);
            return java.util.Collections.emptyList();
        }
    }

    @RequestMapping(path = "/products/categories", method = RequestMethod.GET)
    public List<String> getCategories() {
        try {
            return this.products.findDistinctCategories();
        } catch (Exception e) {
            logger.error("Failed to fetch product categories", e);
            return java.util.Collections.emptyList();
        }
    }

    @RequestMapping(path = "/products", method = RequestMethod.GET)
    public List<Products> getAllProducts() {
        return this.products.findAll();
    }

    @RequestMapping(path = "/products/{id}", method = RequestMethod.GET)
    public Products getProductById(@PathVariable int id) {
        return this.products.findById(id).get();
    }

    @RequestMapping(path = "/products/{id}", method = RequestMethod.DELETE)
    public org.springframework.http.ResponseEntity<Object> deleteProduct(@PathVariable int id,
            @RequestParam(value = "force", required = false) Boolean force) {
        logger.info("Attempting to delete product id={} force={}", id, force);
        try {
            if (Boolean.TRUE.equals(force)) {
                // remove dependent order items first
                try {
                    this.orderItemRepo.deleteByProductId(id);
                } catch (Exception ex) {
                    // log but continue to attempt delete; will fail if DB enforces constraints
                    // differently
                    ex.printStackTrace();
                }
            }
            this.products.deleteById(id);
            java.util.Map<String, Object> ok = java.util.Map.of("message", "Product deleted successfully");
            return org.springframework.http.ResponseEntity.ok(ok);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // No such id
            java.util.Map<String, Object> body = java.util.Map.of(
                    "message", "Product not found",
                    "exception", e.getClass().getSimpleName());
            return org.springframework.http.ResponseEntity.status(404).body(body);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Likely foreign key constraint (referenced by orders)
            logger.error("Data integrity violation while deleting product {}", id, e);
            java.util.Map<String, Object> body = java.util.Map.of(
                    "message", "Cannot delete product: it is referenced by other records",
                    "exception", e.getClass().getSimpleName(),
                    "cause", e.getMostSpecificCause() != null ? e.getMostSpecificCause().toString() : "");
            return org.springframework.http.ResponseEntity.status(409).body(body);
        } catch (Exception e) {
            logger.error("Failed to delete product {}", id, e);
            java.util.Map<String, Object> body = java.util.Map.of(
                    "message", "Failed to delete product: " + e.getMessage(),
                    "exception", e.getClass().getSimpleName(),
                    "cause", e.getCause() != null ? e.getCause().toString() : "");
            return org.springframework.http.ResponseEntity.status(500).body(body);
        }
    }

    @RequestMapping(path = "/products/{id}/force-delete", method = RequestMethod.DELETE)
    public org.springframework.http.ResponseEntity<Object> forceDeleteProduct(@PathVariable int id) {
        logger.info("Force-deleting product id={} (removing order items first)", id);
        try {
            // Remove order items referencing this product
            this.orderItemRepo.deleteByProductId(id);
            // Now delete product
            this.products.deleteById(id);
            java.util.Map<String, Object> ok = java.util.Map.of("message",
                    "Product force-deleted and related order items removed");
            return org.springframework.http.ResponseEntity.ok(ok);
        } catch (Exception e) {
            logger.error("Failed to force-delete product {}", id, e);
            java.util.Map<String, Object> body = java.util.Map.of(
                    "message", "Failed to force-delete product: " + e.getMessage(),
                    "exception", e.getClass().getSimpleName(),
                    "cause", e.getCause() != null ? e.getCause().toString() : "");
            return org.springframework.http.ResponseEntity.status(500).body(body);
        }
    }

    @RequestMapping(path = "/products", method = RequestMethod.POST)
    public String createProduct(
            @RequestParam("brand") String brand,
            @RequestParam("category") String category,
            @RequestParam("description") String description,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("stock_quantity") int stockQuantity,
            @RequestParam(value = "images", required = false) MultipartFile images) {

        try {
            // Set upload directory
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            String imageFileName = null;
            // Save image file if provided
            if (images != null && !images.isEmpty()) {
                imageFileName = images.getOriginalFilename();
                Path target = uploadDir.resolve(imageFileName);
                images.transferTo(target.toFile());
            }

            // Save product with image filename (can be null)
            Products newProduct = new Products(0, brand, category, LocalDateTime.now(), description, imageFileName,
                    name, new java.math.BigDecimal(Double.toString(price)), stockQuantity, LocalDateTime.now());
            this.products.save(newProduct);
            return "Product Inserted Successfully!";
        } catch (Exception e) {
            logger.error("Failed to insert product", e);
            return "Failed to Insert Product!";
        }
    }

    @RequestMapping(path = "/products/{id}", method = RequestMethod.PUT)
    public String updateProduct(
            @PathVariable int id,
            @RequestParam("brand") String brand,
            @RequestParam("category") String category,
            @RequestParam("description") String description,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("stock_quantity") int stockQuantity,
            @RequestParam(value = "images", required = false) MultipartFile images) {

        return this.products.findById(id).map((item) -> {
            item.setBrand(brand);
            item.setCategory(category);
            item.setDescription(description);
            item.setName(name);
            item.setPrice(new java.math.BigDecimal(Double.toString(price)));
            item.setStockQuantity(stockQuantity);

            // Update timestamp
            item.setUpdatedAt(LocalDateTime.now());

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
                    logger.error("Failed to update product image", e);
                    return "Failed to update image!";
                }
            }

            this.products.save(item);
            return "Product Updated Successfully!";
        }).orElse("Product not found!");
    }
}