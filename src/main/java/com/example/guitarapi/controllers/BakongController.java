package com.example.guitarapi.controllers;

import com.example.guitarapi.services.KHQRGenerator;
import com.example.guitarapi.repository.BakongPaymentRepo;
import com.example.guitarapi.repository.OrderRepo;
import com.example.guitarapi.models.BakongPayment;
import com.example.guitarapi.models.Orders;
import com.example.guitarapi.services.PaymentEventService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.guitarapi.models.ApiResponse;
import com.example.guitarapi.utils.ResponseUtil;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Base64;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/bakong")
public class BakongController {
    private final KHQRGenerator khqrGenerator;
    private final BakongPaymentRepo bakongPaymentRepo;
    private final OrderRepo orderRepo;
    private final PaymentEventService paymentEventService;

    public BakongController(KHQRGenerator khqrGenerator, BakongPaymentRepo bakongPaymentRepo, OrderRepo orderRepo, PaymentEventService paymentEventService) {
        this.khqrGenerator = khqrGenerator;
        this.bakongPaymentRepo = bakongPaymentRepo;
        this.orderRepo = orderRepo;
        this.paymentEventService = paymentEventService;
    }
    private static final Logger logger = LoggerFactory.getLogger(BakongController.class);
    
    @GetMapping("/generate-qr")
    public ResponseEntity<byte[]> generateQR(
        @RequestParam Double amount,
        @RequestParam(defaultValue = "USD") String currency,
        @RequestParam(required = false) Integer orderId) {
        try {
            // Validate amount
            if (amount == null || amount < 0) {
                logger.error("Invalid amount: {}", amount);
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            // Validate currency
            if (!currency.equals("USD") && !currency.equals("KHR")) {
                logger.error("Invalid currency: {}", currency);
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            logger.info("Generating KHQR for amount: {} {}", amount, currency);
            
            String qrString = khqrGenerator.generateKHQRString(amount, currency);
            logger.info("Generated KHQR string: {}", qrString);
            
            byte[] qrImage = khqrGenerator.generateQRImage(qrString);
            logger.info("QR Image generated with size: {} bytes", qrImage.length);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("no-cache");
            
            // Do NOT persist a BakongPayment here. QR generation is separate from actual payment confirmation.
            // Persist a lightweight pending BakongPayment when orderId is present.
            // This makes it visible in payment status calls so the frontend shows a pending payment
            // while waiting for the bank callback which will contain the final transactionRef.
            if (orderId != null) {
                logger.info("Generated QR for orderId={} (persisting pending payment)", orderId);
                try {
                    Orders order = orderRepo.findById(orderId).orElse(null);
                    if (order != null) {
                        BakongPayment pending = new BakongPayment();
                        pending.setAmount(amount == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(amount.toString()));
                        pending.setCurrency(currency);
                        pending.setQrString(qrString);
                        // Do not set transactionRef; bank callback should provide it when confirming payment
                        pending.setTransactionRef(null);
                        pending.setCreatedAt(LocalDateTime.now());
                        pending.setStatus("pending");
                        pending.setOrder(order);
                        BakongPayment saved = bakongPaymentRepo.save(pending);
                        logger.info("Created pending BakongPayment id={} for order {}", saved.getId(), orderId);
                    } else {
                        logger.warn("Order {} not found while attempting to persist pending payment", orderId);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to persist pending payment for order {}: {}", orderId, e.getMessage());
                }
            }

            return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating KHQR", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

        /**
         * Create a KHQR and return JSON payload containing the qrString, a base64 PNG image,
         * an optional deep link, and echoed order/amount information.
         * Request body: { "amount": 0.01, "currency": "USD", "orderId": 123 }
         */
        @PostMapping("/create")
        public ResponseEntity<ApiResponse> createKHQR(@RequestBody Map<String, Object> body) {
            try {
                if (body == null || body.get("amount") == null) {
                    return ResponseEntity.badRequest().body(ResponseUtil.failure(4, "Missing amount"));
                }

                java.math.BigDecimal amount = null;
                try { amount = new java.math.BigDecimal(body.get("amount").toString()); } catch (Exception ex) { }
                if (amount == null) return ResponseEntity.badRequest().body(ResponseUtil.failure(4, "Invalid amount"));

                String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";
                Integer orderId = null;
                if (body.get("orderId") != null) {
                    try { orderId = Integer.valueOf(body.get("orderId").toString()); } catch (Exception ignored) {}
                }

                // Generate qr string and image
                String qrString = khqrGenerator.generateKHQRString(amount.doubleValue(), currency);
                byte[] qrImage = khqrGenerator.generateQRImage(qrString);
                String imageBase64 = Base64.getEncoder().encodeToString(qrImage);

                // Deep-link generation disabled: use Merchant KHQR only.
                // The frontend will receive the merchant QR string and PNG image (base64).

                // Persist pending payment if orderId present (same behaviour as generateQR)
                if (orderId != null) {
                    try {
                        Orders order = orderRepo.findById(orderId).orElse(null);
                        if (order != null) {
                            // Defensive: check for existing pending/payment records for this order to avoid duplicates
                            java.util.List<BakongPayment> existingPayments = bakongPaymentRepo.findAllByOrder_Id(orderId);
                            boolean hasPending = existingPayments.stream().anyMatch(p -> "pending".equalsIgnoreCase(p.getStatus()));
                            if (hasPending) {
                                logger.info("Order {} already has a pending BakongPayment; skipping create", orderId);
                            } else {
                                BakongPayment pending = new BakongPayment();
                                pending.setAmount(amount == null ? java.math.BigDecimal.ZERO : amount);
                                pending.setCurrency(currency);
                                pending.setQrString(qrString);
                                pending.setTransactionRef(null);
                                pending.setCreatedAt(LocalDateTime.now());
                                pending.setStatus("pending");
                                pending.setOrder(order);
                                BakongPayment saved = bakongPaymentRepo.save(pending);
                                logger.info("Created pending BakongPayment id={} for order {} via /create", saved.getId(), orderId);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to persist pending payment for order {}: {}", orderId, e.getMessage());
                    }
                }

                java.util.Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("qrString", qrString);
                resp.put("imageBase64", imageBase64);
                // deepLink intentionally omitted (disabled) - merchant QR is returned instead
                resp.put("orderId", orderId);
                resp.put("amount", amount);
                resp.put("currency", currency);

                return ResponseEntity.ok(ResponseUtil.success(resp));
            } catch (Exception e) {
                logger.error("Error in createKHQR", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseUtil.failure(15, "create_khqr_failed"));
            }
        }

    // Simple ping endpoint for debugging mapping/runtime
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse> ping() {
        return ResponseEntity.ok(ResponseUtil.success(Map.of("status", "ok", "endpoint", "/api/bakong/ping")));
    }

    /**
     * Callback endpoint called when payment has been confirmed by Bakong or the frontend.
     * Expects JSON: { "orderId": 1, "qrString": "...", "amount": 12.5, "currency": "USD" }
     */
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse> paymentCallback(@RequestBody Map<String,Object> body) {
        try {
            Integer orderId = body.get("orderId") != null ? Integer.valueOf(body.get("orderId").toString()) : null;
            String qrString = body.get("qrString") != null ? body.get("qrString").toString() : null;
            java.math.BigDecimal amount = body.get("amount") != null ? new java.math.BigDecimal(body.get("amount").toString()) : null;
            String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";
            String transactionRef = body.get("transactionRef") != null ? body.get("transactionRef").toString() : null;

            if (orderId == null || qrString == null || amount == null) {
                return ResponseEntity.badRequest().body(ResponseUtil.failure(4, "Missing fields"));
            }

            // Verify QR (must pass verification to persist)
            boolean verified = false;
            try {
                verified = khqrGenerator.verifyKHQR(qrString);
            } catch (Exception e) {
                logger.warn("KHQR verification call failed: {}", e.getMessage());
                // Treat verification failure as not verified
                verified = false;
            }

            if (!verified) {
                logger.info("KHQR verification failed for orderId={}", orderId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseUtil.failure(8, "verification_failed"));
            }

            // transactionRef from bank/backend is required to mark payment as completed.
            if (transactionRef == null || transactionRef.isBlank()) {
                logger.info("Missing transactionRef for orderId={}. Rejecting persist until bank callback provides a transaction reference.", orderId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseUtil.failure(1, "missing_transactionRef"));
            }

            // Persist payment record only when verified
            Orders order = orderRepo.findById(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseUtil.failure(1, "Order not found"));
            }

            // Use transactionRef for idempotency if present
            if (transactionRef != null) {
                java.util.Optional<BakongPayment> byRef = bakongPaymentRepo.findByTransactionRef(transactionRef);
                if (byRef.isPresent()) {
                    return ResponseEntity.ok(ResponseUtil.success(Map.of("message","payment already recorded","orderId", orderId, "paymentId", byRef.get().getId())));
                }
            }

            BakongPayment payment = new BakongPayment();
            payment.setAmount(amount);
            payment.setCurrency(currency);
            payment.setQrString(qrString);
            payment.setTransactionRef(transactionRef);
            payment.setCreatedAt(LocalDateTime.now());
            payment.setStatus("success");
            payment.setOrder(order);
            BakongPayment savedPayment = bakongPaymentRepo.save(payment);

            // Publish SSE event to any subscribers for this order
            try {
                paymentEventService.publishEvent(orderId, "payment", Map.of("status","success","paymentId", savedPayment.getId(), "amount", savedPayment.getAmount()));
            } catch (Exception e) {
                logger.warn("Failed to publish payment SSE event for order {}: {}", orderId, e.getMessage());
            }

            // Aggregate collected amount and set order status accordingly
            java.util.List<BakongPayment> payments = bakongPaymentRepo.findAllByOrder_Id(orderId);
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
            orderRepo.save(order);

            return ResponseEntity.ok(ResponseUtil.success(Map.of("orderId", orderId, "verified", true, "collected", collected, "total", total)));
        } catch (Exception e) {
            logger.error("Error in payment callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseUtil.failure(15, "callback failed"));
        }
    }

    /**
     * Lightweight endpoint to acknowledge a KHQR scan.
     * Returns a simple success message so Postman / frontend can receive immediate confirmation.
     * Example request body: { "orderId": 1, "qrString": "..." }
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse> scanCallback(@RequestBody(required = false) Map<String,Object> body) {
        try {
            Integer orderId = null;
            String qrString = null;
            java.math.BigDecimal amount = null;
            String currency = "USD";
            String transactionRef = null;

            if (body != null) {
                if (body.get("orderId") != null) {
                    try { orderId = Integer.valueOf(body.get("orderId").toString()); } catch (Exception ignored) {}
                }
                if (body.get("qrString") != null) {
                    qrString = body.get("qrString").toString();
                }
                if (body.get("amount") != null) {
                    try { amount = new java.math.BigDecimal(body.get("amount").toString()); } catch (Exception ignored) {}
                }
                if (body.get("currency") != null) {
                    currency = body.get("currency").toString();
                }
                if (body.get("transactionRef") != null) {
                    transactionRef = body.get("transactionRef").toString();
                }
            }

            if (orderId == null) {
                return ResponseEntity.badRequest().body(ResponseUtil.failure(1, "orderId required"));
            }

            Orders order = orderRepo.findById(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseUtil.failure(1, "Order not found"));
            }

            // If order already paid/completed, return success immediately
            if ("completed".equalsIgnoreCase(order.getStatus()) || "paid".equalsIgnoreCase(order.getPaymentStatus())) {
                return ResponseEntity.ok(ResponseUtil.success(Map.of("message","Order already completed","orderId",orderId)));
            }

            // If transactionRef provided, check idempotency
            if (transactionRef != null && !transactionRef.isBlank()) {
                java.util.Optional<BakongPayment> existing = bakongPaymentRepo.findByTransactionRef(transactionRef);
                if (existing.isPresent()) {
                    return ResponseEntity.ok(ResponseUtil.success(Map.of("message","payment already recorded","orderId",orderId,"paymentId",existing.get().getId())));
                }
            }

            // If amount not provided, assume full order total
            if (amount == null) {
                amount = order.getTotalAmount() == null ? java.math.BigDecimal.ZERO : order.getTotalAmount();
            }

            // Optionally verify the QR if qrString present; if verification fails, return error
            if (qrString != null) {
                boolean verified = false;
                try { verified = khqrGenerator.verifyKHQR(qrString); } catch (Exception ex) { logger.warn("KHQR verify failed on scan: {}", ex.getMessage()); }
                if (!verified) {
                    logger.info("KHQR verification failed for scan on order {}", orderId);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseUtil.failure(8, "verification_failed"));
                }
            }

            // Create payment record and mark success
            BakongPayment payment = new BakongPayment();
            payment.setAmount(amount);
            payment.setCurrency(currency);
            payment.setQrString(qrString);
            payment.setTransactionRef(transactionRef != null ? transactionRef : java.util.UUID.randomUUID().toString());
            payment.setCreatedAt(LocalDateTime.now());
            payment.setStatus("success");
            payment.setOrder(order);
            BakongPayment saved = bakongPaymentRepo.save(payment);

            // Update order status to completed/paid when collected >= total
            java.util.List<BakongPayment> payments = bakongPaymentRepo.findAllByOrder_Id(orderId);
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
            orderRepo.save(order);

            // Publish SSE event
            try {
                paymentEventService.publishEvent(orderId, "payment", Map.of("status","success","paymentId", saved.getId(), "amount", saved.getAmount()));
            } catch (Exception e) {
                logger.warn("Failed to publish payment SSE event for order {}: {}", orderId, e.getMessage());
            }

            return ResponseEntity.ok(ResponseUtil.success(Map.of("message","Scan processed","orderId",orderId,"paymentId",saved.getId(),"collected",collected,"total",total)));
        } catch (Exception e) {
            logger.error("Error in scan endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseUtil.failure(15, "scan failed"));
        }
    }
}