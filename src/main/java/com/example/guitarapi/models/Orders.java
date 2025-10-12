package com.example.guitarapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders_tbl")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "status")
    private String status;

    @Column(name = "total_amount")
    private double totalAmount;

    @Column(name = "updated_at")
    private String updatedAt;

    @Column(name = "user_id")
    private int userId;
}