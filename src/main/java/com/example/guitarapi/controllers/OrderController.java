    package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.http.ResponseEntity;

import com.example.guitarapi.repository.OrderRepo;
import com.example.guitarapi.models.Orders;
import com.example.guitarapi.models.OrderItems;
import com.example.guitarapi.models.Products;
import com.example.guitarapi.repository.OrderItemRepo;
import com.example.guitarapi.services.KHQRGenerator;
import com.example.guitarapi.models.BakongPayment;
import com.example.guitarapi.repository.BakongPaymentRepo;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.guitarapi.services.PaymentEventService;
import com.example.guitarapi.repository.UserRepo;
import com.example.guitarapi.models.Users;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private OrderRepo orders;
    private KHQRGenerator khqrGenerator;
    private OrderItemRepo orderItemRepo;
    private BakongPaymentRepo bakongPaymentRepo;
    private PaymentEventService paymentEventService;
    private UserRepo userRepo;

    public OrderController(OrderRepo orders, OrderItemRepo orderItemRepo, KHQRGenerator khqrGenerator, BakongPaymentRepo bakongPaymentRepo, PaymentEventService paymentEventService, UserRepo userRepo) {
        this.orders = orders;
        this.orderItemRepo = orderItemRepo;
        this.khqrGenerator = khqrGenerator;
        this.bakongPaymentRepo = bakongPaymentRepo;
        this.paymentEventService = paymentEventService;
        this.userRepo = userRepo;
    }

    // Update order items for a pending order
    @RequestMapping(path = "/orders/{id}/items", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<?> updateOrderItems(@PathVariable int id, @RequestBody List<Map<String, Object>> items, @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            logger.info("updateOrderItems called for orderId={} with {} items", id, items == null ? 0 : items.size());
            logger.debug("updateOrderItems payload: {}", items);
            Orders order = this.orders.findById(id).orElse(null);
            // If order is missing or not pending, create a new pending order as a safe fallback.
            if (order == null || !"pending".equalsIgnoreCase(order.getStatus())) {
                logger.warn("updateOrderItems: order {} not found or not pending (status={}), creating fallback pending order", id, (order == null ? "<missing>" : order.getStatus()));
                // Create a lightweight pending order and use its id
                LocalDateTime now = LocalDateTime.now();
                String fallbackName = "Guest Customer";
                int fallbackUserId = 1;
                // If Authorization header present, try to resolve user and use their name/id
                if (auth != null && auth.startsWith("Bearer ") && this.userRepo != null) {
                    try {
                        String token = auth.substring(7);
                        logger.info("updateOrderItems received Authorization token: {}", token);
                        Users u = this.userRepo.findByToken(token);
                        if (u != null) {
                            fallbackName = (u.getFirstName() == null ? "" : u.getFirstName()) + (u.getLastName() == null ? "" : " " + u.getLastName());
                            fallbackUserId = u.getId();
                            logger.info("updateOrderItems resolved user id={} name={}", u.getId(), fallbackName);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to resolve user from token in updateOrderItems: {}", ex.getMessage());
                    }
                }
                Orders fallback = new Orders(0, now, fallbackName, "Pick up at store", "pending", java.math.BigDecimal.ZERO, now, fallbackUserId);
                Orders saved = this.orders.save(fallback);
                order = saved;
                id = saved.getId();
            }
            // Remove existing items for this order
            try {
                orderItemRepo.deleteByOrderId(id);
            } catch (Exception dbDelEx) {
                // Log full exception (stacktrace) for diagnostics
                logger.error("Failed to delete existing order items for order {}", id, dbDelEx);
                return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to clear existing order items"));
            }

            // Add new items (treat missing items as empty list -> no-op)
            java.util.List<Map<String, Object>> safeItems = items == null ? java.util.Collections.<Map<String, Object>>emptyList() : items;
            int idx = 0;
            for (Map<String, Object> item : safeItems) {
                idx++;
                int productId = 0;
                int quantity = 0;
                java.math.BigDecimal unitPrice = java.math.BigDecimal.ZERO;
                try {
                    if (item.get("productId") != null) productId = Integer.parseInt(item.get("productId").toString());
                    else if (item.get("id") != null) productId = Integer.parseInt(item.get("id").toString());
                    else if (item.get("_id") != null) productId = Integer.parseInt(item.get("_id").toString());

                    if (item.get("quantity") != null) quantity = Integer.parseInt(item.get("quantity").toString());
                    if (item.get("unitPrice") != null) unitPrice = new java.math.BigDecimal(item.get("unitPrice").toString());
                } catch (Exception parseEx) {
                    logger.warn("updateOrderItems: failed to parse item at index {}: {} - skipping item", idx, parseEx.getMessage());
                    continue; // skip invalid item
                }

                // Skip invalid items instead of failing the whole request
                if (productId <= 0) {
                    logger.warn("updateOrderItems: skipping item at index {} due to invalid productId={}", idx, productId);
                    continue;
                }
                if (quantity <= 0) {
                    logger.warn("updateOrderItems: skipping item at index {} due to invalid quantity={}", idx, quantity);
                    continue;
                }

                try {
                    java.math.BigDecimal totalPrice = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
                    OrderItems newOrderItem = new OrderItems(0, quantity, totalPrice, unitPrice, id, productId);
                    orderItemRepo.save(newOrderItem);
                } catch (Exception saveEx) {
                    // Log full exception for diagnostics and continue
                    logger.error("Failed to save order item for order {} at index {}", id, idx, saveEx);
                    // don't abort entire request; continue with other items
                    continue;
                }
            }

            // Recompute order total and update order
            java.math.BigDecimal computedTotal = orderItemRepo.findByOrderId(id).stream()
                    .map(oi -> oi.getTotalPrice() == null ? java.math.BigDecimal.ZERO : oi.getTotalPrice())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            if (computedTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                order.setTotalAmount(computedTotal);
                this.orders.save(order);
            }

            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            // Log full stacktrace and return 500
            logger.error("Failed to update order items", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to update order items"));
        }
    }

    @RequestMapping(path = "/orders", method = RequestMethod.GET)
    public List<Orders> getAllOrders() {
        return this.orders.findAll();
    }

    @RequestMapping(path = "/orders/me", method = RequestMethod.GET)
    public ResponseEntity<?> getMyOrders(@RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            int userId = 1; // guest by default
            if (auth != null && auth.startsWith("Bearer ") && this.userRepo != null) {
                try {
                    String token = auth.substring(7);
                    Users u = this.userRepo.findByToken(token);
                    if (u != null) userId = u.getId();
                } catch (Exception ex) {
                    logger.warn("Failed to resolve user from token in getMyOrders: {}", ex.getMessage());
                }
            }
            
            // Get orders with complete product data via JOINs
            java.util.List<Orders> ordersList = this.orders.findByUserId(userId);
            java.util.List<Map<String, Object>> enrichedOrders = new java.util.ArrayList<>();
            
            for (Orders order : ordersList) {
                Map<String, Object> orderData = new java.util.HashMap<>();
                orderData.put("id", order.getId());
                orderData.put("userId", order.getUserId());
                orderData.put("totalAmount", order.getTotalAmount());
                orderData.put("status", order.getStatus());
                orderData.put("createdAt", order.getCreatedAt());
                orderData.put("updatedAt", order.getUpdatedAt());
                orderData.put("customerName", order.getCustomerName());
                orderData.put("shippingAddress", order.getShippingAddress());
                orderData.put("paymentStatus", order.getPaymentStatus());
                
                // Get order items with complete product data
                java.util.List<Map<String, Object>> enrichedItems = new java.util.ArrayList<>();
                for (OrderItems item : order.getItems()) {
                    Map<String, Object> itemData = new java.util.HashMap<>();
                    itemData.put("id", item.getId());
                    itemData.put("orderId", item.getOrderId());
                    itemData.put("productId", item.getProductId());
                    itemData.put("quantity", item.getQuantity());
                    itemData.put("unitPrice", item.getUnitPrice());
                    itemData.put("totalPrice", item.getTotalPrice());
                    
                    // Include complete product data via JOIN relationship
                    Products product = item.getProduct();
                    if (product != null) {
                        itemData.put("productName", product.getName());
                        itemData.put("productImages", product.getImages());
                        itemData.put("productBrand", product.getBrand());
                        itemData.put("productDescription", product.getDescription());
                        itemData.put("productPrice", product.getPrice());
                        itemData.put("productStockQuantity", product.getStockQuantity());
                        itemData.put("productCategory", product.getCategory());
                    }
                    
                    enrichedItems.add(itemData);
                }
                orderData.put("orderItems", enrichedItems);
                enrichedOrders.add(orderData);
            }
            
            return ResponseEntity.ok(enrichedOrders);
        } catch (Exception e) {
            logger.error("Failed to fetch my orders", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to fetch orders"));
        }
    }

    @RequestMapping(path = "/orders/{id:\\d+}", method = RequestMethod.GET)
    public ResponseEntity<?> getOrderById(@PathVariable int id) {
        try {
            Orders order = this.orders.findById(id).orElse(null);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Get order items with product details using repository method
            List<OrderItems> orderItems = orderItemRepo.findByOrderId(id);
            
            // Create response with complete order data including product details
            Map<String, Object> response = new HashMap<>();
            response.put("id", order.getId());
            response.put("customerName", order.getCustomerName());
            response.put("totalAmount", order.getTotalAmount());
            response.put("status", order.getStatus());
            response.put("createdAt", order.getCreatedAt());
            response.put("userId", order.getUserId());
            
            // Build items array with complete product data
            List<Map<String, Object>> itemsWithProducts = new java.util.ArrayList<>();
            for (OrderItems item : orderItems) {
                // Load the product data (not @JsonIgnored in this context)
                Products product = item.getProduct();
                if (product != null) {
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("id", item.getId());
                    itemData.put("quantity", item.getQuantity());
                    itemData.put("unitPrice", item.getUnitPrice());
                    itemData.put("totalPrice", item.getTotalPrice());
                    itemData.put("productId", item.getProductId());
                    itemData.put("orderId", item.getOrderId());
                    
                    // Include complete product data
                    itemData.put("productName", product.getName());
                    itemData.put("productBrand", product.getBrand());
                    itemData.put("productCategory", product.getCategory());
                    itemData.put("productImages", product.getImages());
                    itemData.put("productDescription", product.getDescription());
                    itemData.put("productPrice", product.getPrice());
                    
                    itemsWithProducts.add(itemData);
                }
            }
            
            response.put("items", itemsWithProducts);
            response.put("orderItems", itemsWithProducts); // For backward compatibility
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to fetch order by id {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to fetch order"));
        }
    }

    @RequestMapping(path = "/orders/{id:\\d+}", method = RequestMethod.DELETE)
    public String deleteOrder(@PathVariable int id) {
        this.orders.deleteById(id);
        return "Successfully Deleted";
    }
    
        // Delete a single order item by order id and product id
        @RequestMapping(path = "/orders/{orderId:\\d+}/items/{productId:\\d+}", method = RequestMethod.DELETE)
        @Transactional
        public ResponseEntity<?> deleteOrderItem(@PathVariable int orderId, @PathVariable int productId) {
            try {
                // attempt to delete the specific item
                orderItemRepo.deleteByOrderIdAndProductId(orderId, productId);
                // Recompute order total
                java.math.BigDecimal computedTotal = orderItemRepo.findByOrderId(orderId).stream()
                        .map(oi -> oi.getTotalPrice() == null ? java.math.BigDecimal.ZERO : oi.getTotalPrice())
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                Orders order = this.orders.findById(orderId).orElse(null);
                if (order != null) {
                    order.setTotalAmount(computedTotal);
                    this.orders.save(order);
                }
                return ResponseEntity.ok(Map.of("status", "success"));
            } catch (Exception e) {
                logger.error("Failed to delete order item {} for order {}", productId, orderId, e);
                return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to delete order item"));
            }
        }

    @RequestMapping(path = "/orders", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> orderData, @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            // Get current timestamp
            // (use LocalDateTime directly when constructing the entity)
            // Extract data from request with null checks
            String customerName = orderData.get("customerName") != null ? (String) orderData.get("customerName") : "Guest Customer";
            String shippingAddress = orderData.get("shippingAddress") != null ? (String) orderData.get("shippingAddress") : "Pick up at store";
            java.math.BigDecimal totalAmount;

            try {
                totalAmount = orderData.get("totalAmount") != null ? 
                    new java.math.BigDecimal(orderData.get("totalAmount").toString()) : java.math.BigDecimal.ZERO;
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Invalid total amount"));
            }

            String status = orderData.get("status") != null ? (String) orderData.get("status") : "pending";  // Default status
            int userId = 1;  // Default guest user ID

            // If Authorization header present, resolve user and override customerName/userId to avoid spoofing
            if (auth != null && auth.startsWith("Bearer ") && this.userRepo != null) {
                try {
                    String token = auth.substring(7);
                    logger.info("createOrder received Authorization token: {}", token);
                    Users u = this.userRepo.findByToken(token);
                    if (u != null) {
                        userId = u.getId();
                        String resolvedName = (u.getFirstName() == null ? "" : u.getFirstName()) + (u.getLastName() == null ? "" : " " + u.getLastName());
                        if (!resolvedName.trim().isEmpty()) customerName = resolvedName.trim();
                        logger.info("createOrder resolved user id={} name={}", u.getId(), customerName);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to resolve user from token in createOrder: {}", ex.getMessage());
                }
            }
            
            // Validate total amount (no 'draft' status exists anymore)
            if (totalAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Total amount must be greater than 0"));
            }

            // Save order
            Orders newOrder = new Orders(0, LocalDateTime.now(), customerName, shippingAddress, status, totalAmount, LocalDateTime.now(), userId);
            Orders savedOrder = this.orders.save(newOrder);

            // If items provided, create order items (use unitPrice if provided, otherwise 0)
            if (orderData.get("items") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
                for (Map<String, Object> it : items) {
                    int productId = it.get("productId") != null ? Integer.parseInt(it.get("productId").toString()) : 0;
                    int quantity = it.get("quantity") != null ? Integer.parseInt(it.get("quantity").toString()) : 0;
                    java.math.BigDecimal unitPrice = it.get("unitPrice") != null ? new java.math.BigDecimal(it.get("unitPrice").toString()) : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal totalPrice = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
                    OrderItems newOrderItem = new OrderItems(0, quantity, totalPrice, unitPrice, savedOrder.getId(), productId);
                    orderItemRepo.save(newOrderItem);
                }
                // Optionally update order total based on items
                java.math.BigDecimal computedTotal = orderItemRepo.findAll().stream()
                    .filter(oi -> oi.getOrderId() == savedOrder.getId())
                    .map(oi -> oi.getTotalPrice() == null ? java.math.BigDecimal.ZERO : oi.getTotalPrice())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                if (computedTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
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
            // Log full stacktrace for diagnostics
            logger.error("Failed to create order", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to create order");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @RequestMapping(path = "/orders/{id:\\d+}", method = RequestMethod.PUT)
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
            item.setTotalAmount(java.math.BigDecimal.valueOf(totalAmount));
            item.setUserId(userId);
            
            // Update timestamp
            item.setUpdatedAt(LocalDateTime.now());

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
            java.math.BigDecimal collected = payments.stream().map(p -> p.getAmount() == null ? java.math.BigDecimal.ZERO : p.getAmount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal total = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();

            String status;
            if (total.compareTo(java.math.BigDecimal.ZERO) > 0 && collected.compareTo(total) >= 0) {
                status = "completed";
            } else if (collected.compareTo(java.math.BigDecimal.ZERO) > 0) {
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

    // Server-Sent Events endpoint for order events (payment success notifications)
    @GetMapping(path = "/orders/{id}/events", produces = "text/event-stream")
    public SseEmitter subscribeOrderEvents(@PathVariable("id") int id) {
        SseEmitter emitter = paymentEventService.createEmitter(id);
        return emitter;
    }

    // Debug endpoint to see how many SSE subscribers exist for an order
    @GetMapping(path = "/orders/{id}/emitters")
    public ResponseEntity<Map<String, Object>> getEmitterCount(@PathVariable("id") int id) {
        int count = paymentEventService.getEmitterCount(id);
        return ResponseEntity.ok(Map.of("orderId", id, "emitters", count));
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
            java.math.BigDecimal amount = payload.get("amount") != null ? new java.math.BigDecimal(payload.get("amount").toString()) : null;
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
            bp.setCreatedAt(LocalDateTime.now());
            bp.setStatus("pending");

            BakongPayment saved = this.bakongPaymentRepo.save(bp);

            // Recompute collected amount and update order status conditionally
            java.util.List<BakongPayment> payments = this.bakongPaymentRepo.findAllByOrder_Id(id);
            java.math.BigDecimal collected = payments.stream().map(p -> p.getAmount() == null ? java.math.BigDecimal.ZERO : p.getAmount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal total = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
            if (total.compareTo(java.math.BigDecimal.ZERO) > 0 && collected.compareTo(total) >= 0) {
                order.setStatus("completed");
                order.setPaymentStatus("paid");
            } else if (collected.compareTo(java.math.BigDecimal.ZERO) > 0) {
                order.setStatus("partial");
                order.setPaymentStatus("partial");
            }
            order.setUpdatedAt(LocalDateTime.now());
            this.orders.save(order);

            // Publish SSE event to notify subscribers about the new payment
            try {
                paymentEventService.publishEvent(id, "payment", Map.of("status", "success", "paymentId", saved.getId(), "amount", saved.getAmount()));
            } catch (Exception e) {
                logger.warn("Failed to publish payment SSE event for order {}: {}", id, e.getMessage());
            }

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