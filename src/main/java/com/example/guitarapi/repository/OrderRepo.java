package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.guitarapi.models.Orders;

@Repository
public interface OrderRepo extends JpaRepository<Orders, Integer> {
	java.util.List<Orders> findByUserId(int userId);
}