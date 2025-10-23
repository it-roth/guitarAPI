package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.guitarapi.models.Products;

@Repository
public interface ProductRepo extends JpaRepository<Products, Integer> {
	@Query("SELECT DISTINCT p.brand FROM Products p WHERE p.brand IS NOT NULL ORDER BY p.brand")
	java.util.List<String> findDistinctBrands();

	@Query("SELECT DISTINCT p.category FROM Products p WHERE p.category IS NOT NULL ORDER BY p.category")
	java.util.List<String> findDistinctCategories();
    
}