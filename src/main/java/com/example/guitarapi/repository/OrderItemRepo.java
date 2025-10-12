package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.guitarapi.models.OrderItems;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItems, Integer> {
    
}