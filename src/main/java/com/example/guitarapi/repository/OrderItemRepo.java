package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.guitarapi.models.OrderItems;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItems, Integer> {
	// Delete all order items for a specific order id
	void deleteByOrderId(int orderId);
	// Find order items for a specific order id
	java.util.List<OrderItems> findByOrderId(int orderId);
}