package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.guitarapi.models.Products;

@Repository
public interface ProductRepo extends JpaRepository<Products, Integer> {
    
}