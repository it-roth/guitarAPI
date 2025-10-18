    package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import com.example.guitarapi.repository.OrderRepo;
import com.example.guitarapi.models.Orders;
import com.example.guitarapi.models.OrderItems;
import com.example.guitarapi.repository.OrderItemRepo;
import com.example.guitarapi.services.KHQRGenerator;
import com.example.guitarapi.models.BakongPayment;
import com.example.guitarapi.repository.BakongPaymentRepo;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private OrderRepo orders;
    private KHQRGenerator khqrGenerator;
    private OrderItemRepo orderItemRepo;
    private BakongPaymentRepo bakongPaymentRepo;

    public OrderController(OrderRepo orders, OrderItemRepo orderItemRepo, KHQRGenerator khqrGenerator, BakongPaymentRepo bakongPaymentRepo) {
        this.orders = orders;
        this.orderItemRepo = orderItemRepo;
        this.khqrGenerator = khqrGenerator;
        this.bakongPaymentRepo = bakongPaymentRepo;
    }

    // Update order items for a pending order
    @RequestMapping(path = "/orders/{id}/items", method = RequestMethod.PUT)
    public ResponseEntity<?> updateOrderItems(@PathVariable int id, @RequestBody List<Map<String, Object>> items) {
        try {
            logger.info("updateOrderItems called for orderId={} with {} items", id, items == null ? 0 : items.size());
            Orders order = this.orders.findById(id).orElse(null);
            // If order is missing or not pending, create a new pending order as a safe fallback.
            if (order == null || !"pending".equalsIgnoreCase(order.getStatus())) {
                logger.warn("updateOrderItems: order {} not found or not pending (status={}), creating fallback pending order", id, (order == null ? "<missing>" : order.getStatus()));
                // Create a lightweight pending order and use its id
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                Orders fallback = new Orders(0, now, "Guest Customer", "Pick up at store", "pending", 0.0, now, 1);
                Orders saved = this.orders.save(fallback);
                order = saved;
                id = saved.getId();
            }
            // Remove existing items for this order
            orderItemRepo.deleteByOrderId(id);
            // Add new items
            for (Map<String, Object> item : items) {
                int productId = item.get("productId") != null ? Integer.parseInt(item.get("productId").toString()) : 0;
                int quantity = item.get("quantity") != null ? Integer.parseInt(item.get("quantity").toString()) : 0;
                double unitPrice = item.get("unitPrice") != null ? Double.valueOf(item.get("unitPrice").toString()) : 0.0;
                double totalPrice = unitPrice * quantity;
                OrderItems newOrderItem = new OrderItems(0, quantity, totalPrice, unitPrice, id, productId);
                orderItemRepo.save(newOrderItem);
            }

            // Recompute order total and update order
            double computedTotal = orderItemRepo.findByOrderId(id).stream().mapToDouble(oi -> oi.getTotalPrice()).sum();
            if (computedTotal > 0) {
                order.setTotalAmount(computedTotal);
                this.orders.save(order);
            }

            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to update order items"));
        }
    }

    @RequestMapping(path = "/orders", method = RequestMethod.GET)
    public List<Orders> getAllOrders() {
        return this.orders.findAll();
    }

    @RequestMapping(path = "/orders/{id}", method = RequestMethod.GET)
    public Orders getOrderById(@PathVariable int id) {
        return this.orders.findById(id).get();
    }

    @RequestMapping(path = "/orders/{id}", method = RequestMethod.DELETE)
    public String deleteOrder(@PathVariable int id) {
        this.orders.deleteById(id);
        return "Successfully Deleted";
    }

    @RequestMapping(path = "/orders", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> orderData) {
        try {
            // Get current timestamp
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // Extract data from request with null checks
            String customerName = orderData.get("customerName") != null ? (String) orderData.get("customerName") : "Guest Customer";
            String shippingAddress = orderData.get("shippingAddress") != null ? (String) orderData.get("shippingAddress") : "Pick up at store";
            Double totalAmount;

            try {
                totalAmount = orderData.get("totalAmount") != null ? 
                    Double.valueOf(orderData.get("totalAmount").toString()) : 0.0;
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Invalid total amount"));
            }

            String status = orderData.get("status") != null ? (String) orderData.get("status") : "pending";  // Default status
            int userId = 1;  // Default guest user ID
            
            // Validate total amount (no 'draft' status exists anymore)
            if (totalAmount <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Total amount must be greater than 0"));
            }

            // Save order
            Orders newOrder = new Orders(0, currentTime, customerName, shippingAddress, status, totalAmount, currentTime, userId);
            Orders savedOrder = this.orders.save(newOrder);

            // If items provided, create order items (use unitPrice if provided, otherwise 0)
            if (orderData.get("items") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
                for (Map<String, Object> it : items) {
                    int productId = it.get("productId") != null ? Integer.parseInt(it.get("productId").toString()) : 0;
                    int quantity = it.get("quantity") != null ? Integer.parseInt(it.get("quantity").toString()) : 0;
                    double unitPrice = it.get("unitPrice") != null ? Double.valueOf(it.get("unitPrice").toString()) : 0.0;
                    double totalPrice = unitPrice * quantity;
                    OrderItems newOrderItem = new OrderItems(0, quantity, totalPrice, unitPrice, savedOrder.getId(), productId);
                    orderItemRepo.save(newOrderItem);
                }
                // Optionally update order total based on items
                double computedTotal = orderItemRepo.findAll().stream()
                    .filter(oi -> oi.getOrderId() == savedOrder.getId())
                    .mapToDouble(oi -> oi.getTotalPrice())
                    .sum();
                if (computedTotal > 0) {
                    savedOrder.setTotalAmount(computedTotal);
                    this.orders.save(savedOrder);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedOrder.getId());
            response.put("status", "success");
            response.put("message", "Order created successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to create order");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @RequestMapping(path = "/orders/{id}", method = RequestMethod.PUT)
    public String updateOrder(
            @PathVariable int id,
            @RequestParam("customer_name") String customerName,
            @RequestParam("shipping_address") String shippingAddress,
            @RequestParam("status") String status,
            @RequestParam("total_amount") double totalAmount,
            @RequestParam("user_id") int userId) {

        return this.orders.findById(id).map((item) -> {
            item.setCustomerName(customerName);
            item.setShippingAddress(shippingAddress);
            item.setStatus(status);
            item.setTotalAmount(totalAmount);
            item.setUserId(userId);
            
            // Update timestamp
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            item.setUpdatedAt(currentTime);

            this.orders.save(item);
            return "Order Updated Successfully!";
        }).orElse("Order not found!");
    }

    @RequestMapping(path = "/orders/{id}/khqr", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> generateKhqrCode(
            @PathVariable int id,
            @RequestBody Map<String, Double> request) {
        
        if (!this.orders.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Double amount = request.get("amount");
            if (amount == null) {
                return ResponseEntity.badRequest().build();
            }

            String qrString = khqrGenerator.generateKHQRString(amount, "USD");
            
            Map<String, Object> response = new HashMap<>();
            response.put("qrString", qrString);
            response.put("amount", amount);
            response.put("orderId", id);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @RequestMapping(path = "/orders/{id}/khqr/status", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Object>> getKhqrPaymentStatus(@PathVariable int id) {
        return this.orders.findById(id).map(order -> {
            Map<String, Object> response = new HashMap<>();
            // Aggregate payments for this order (support multiple payments)
            java.util.List<BakongPayment> payments = this.bakongPaymentRepo.findAllByOrder_Id(id);
            double collected = payments.stream().mapToDouble(p -> p.getAmount() == null ? 0.0 : p.getAmount()).sum();
            double total = order.getTotalAmount();

            String status;
            if (collected >= total && total > 0) {
                status = "completed";
            } else if (collected > 0) {
                status = "partial";
            } else {
                status = "pending";
            }

            response.put("status", status);
            response.put("orderId", id);
            response.put("collected", collected);
            response.put("total", total);
            // Return recent payments (id, amount, currency, transactionRef, createdAt)
            java.util.List<Map<String, Object>> payList = new java.util.ArrayList<>();
            for (BakongPayment p : payments) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("amount", p.getAmount());
                m.put("currency", p.getCurrency());
                m.put("transactionRef", p.getTransactionRef());
                m.put("createdAt", p.getCreatedAt());
                payList.add(m);
            }
            response.put("payments", payList);
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Persist Bakong payment for an order and mark the order as complete
    @RequestMapping(path = "/orders/{id}/bakong", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> persistBakongPayment(@PathVariable("id") int id, @RequestBody Map<String, Object> payload) {
        try {
            Orders order = this.orders.findById(id).orElse(null);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            // Accept transactionRef to dedupe bank callbacks
            String transactionRef = payload.get("transactionRef") != null ? payload.get("transactionRef").toString() : null;
            String currency = payload.get("currency") != null ? payload.get("currency").toString() : "USD";
            Double amount = payload.get("amount") != null ? Double.valueOf(payload.get("amount").toString()) : null;
            String qrString = payload.get("qrString") != null ? payload.get("qrString").toString() : null;

            // If transactionRef provided, ensure idempotency by transactionRef
            if (transactionRef != null) {
                java.util.Optional<BakongPayment> byRef = this.bakongPaymentRepo.findByTransactionRef(transactionRef);
                if (byRef.isPresent()) {
                    return ResponseEntity.ok(Map.of("status", "success", "message", "payment already recorded", "paymentId", byRef.get().getId(), "orderId", id));
                }
            }

            BakongPayment bp = new BakongPayment();
            bp.setAmount(amount);
            bp.setCurrency(currency);
            bp.setQrString(qrString);
            bp.setTransactionRef(transactionRef);
            bp.setOrder(order);
            bp.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            BakongPayment saved = this.bakongPaymentRepo.save(bp);

            // Recompute collected amount and update order status conditionally
            java.util.List<BakongPayment> payments = this.bakongPaymentRepo.findAllByOrder_Id(id);
            double collected = payments.stream().mapToDouble(p -> p.getAmount() == null ? 0.0 : p.getAmount()).sum();
                double total = order.getTotalAmount();
            if (total > 0 && collected >= total) {
                order.setStatus("completed");
            } else if (collected > 0) {
                order.setStatus("partial");
            }
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            order.setUpdatedAt(currentTime);
            this.orders.save(order);

            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("paymentId", saved.getId());
            resp.put("orderId", id);
            resp.put("collected", collected);
            resp.put("total", total);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to persist payment"));
        }
    }
}