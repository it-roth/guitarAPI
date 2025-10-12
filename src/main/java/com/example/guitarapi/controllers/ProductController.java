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
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class ProductController {

    private ProductRepo products;

    public ProductController(ProductRepo products) {
        this.products = products;
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
    public String deleteProduct(@PathVariable int id) {
        this.products.deleteById(id);
        return "Successfully Deleted";
    }

    @RequestMapping(path = "/products", method = RequestMethod.POST)
    public String createProduct(
            @RequestParam("brand") String brand,
            @RequestParam("category") String category,
            @RequestParam("description") String description,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("stock_quantity") int stockQuantity,
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
             String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
             
             // Save product with image filename
             Products newProduct = new Products(0, brand, category, currentTime, description, imageFileName, name, price, stockQuantity, currentTime);
             this.products.save(newProduct);
             return "Product Inserted Successfully!";
         } catch (Exception e) {
             e.printStackTrace();
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
            item.setPrice(price);
            item.setStockQuantity(stockQuantity);
            
            // Update timestamp
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            item.setUpdatedAt(currentTime);

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
                    e.printStackTrace();
                    return "Failed to update image!";
                }
            }

            this.products.save(item);
            return "Product Updated Successfully!";
        }).orElse("Product not found!");
    }
}