package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.guitarapi.models.OrderItems;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItems, Integer> {
	// Delete all order items for a specific order id
	@Modifying
	@Transactional
	void deleteByOrderId(int orderId);

	// Delete a single order item by order id and product id
	@Modifying
	@Transactional
	void deleteByOrderIdAndProductId(int orderId, int productId);

	// Delete all order items that reference a particular product id
	@Modifying
	@Transactional
	void deleteByProductId(int productId);
	// Find order items for a specific order id
	java.util.List<OrderItems> findByOrderId(int orderId);
}