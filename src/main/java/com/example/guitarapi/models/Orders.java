package com.example.guitarapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.CascadeType;
import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "orders_tbl")
public class Orders {
    
    public Orders() {}
    
    public Orders(int id, LocalDateTime createdAt, String customerName, String shippingAddress, String status, BigDecimal totalAmount, LocalDateTime updatedAt, int userId) {
        this.id = id;
        this.createdAt = createdAt;
        this.customerName = customerName;
        this.shippingAddress = shippingAddress;
        this.status = status;
        this.totalAmount = totalAmount;
        this.updatedAt = updatedAt;
        this.userId = userId;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "status")
    private String status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "payment_status")
    private String paymentStatus; // e.g. pending, partial, paid, failed

    @Column(name = "user_id")
    private int userId;

    // JPA relationship to Users (kept non-insertable/updatable to preserve existing userId usage)
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private Users user;

    // One-to-many relationship to order items
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItems> items;

    // One-to-one relationship to BakongPayment (if any)
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private com.example.guitarapi.models.BakongPayment bakongPayment;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public List<OrderItems> getItems() {
        return items;
    }

    public void setItems(List<OrderItems> items) {
        this.items = items;
    }

    public com.example.guitarapi.models.BakongPayment getBakongPayment() {
        return bakongPayment;
    }

    public void setBakongPayment(com.example.guitarapi.models.BakongPayment bakongPayment) {
        this.bakongPayment = bakongPayment;
    }

    // For convenience, get totalAmount as double when needed (avoid using for persistence)
    public double getTotalAmountAsDouble() {
        return this.totalAmount == null ? 0.0 : this.totalAmount.doubleValue();
    }
}