package com.example.guitarapi.controllers;

import com.example.guitarapi.services.KHQRGenerator;
import com.example.guitarapi.repository.BakongPaymentRepo;
import com.example.guitarapi.repository.OrderRepo;
import com.example.guitarapi.models.BakongPayment;
import com.example.guitarapi.models.Orders;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/bakong")
public class BakongController {
    private final KHQRGenerator khqrGenerator;
    private final BakongPaymentRepo bakongPaymentRepo;
    private final OrderRepo orderRepo;

    public BakongController(KHQRGenerator khqrGenerator, BakongPaymentRepo bakongPaymentRepo, OrderRepo orderRepo) {
        this.khqrGenerator = khqrGenerator;
        this.bakongPaymentRepo = bakongPaymentRepo;
        this.orderRepo = orderRepo;
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
            // Persisting should only happen when the frontend/merchant confirms the payment.
            if (orderId != null) {
                logger.info("Generated QR for orderId={} (not persisting payment at QR generation)", orderId);
            }

            return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating KHQR", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Simple ping endpoint for debugging mapping/runtime
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok", "endpoint", "/api/bakong/ping"));
    }

    /**
     * Callback endpoint called when payment has been confirmed by Bakong or the frontend.
     * Expects JSON: { "orderId": 1, "qrString": "...", "amount": 12.5, "currency": "USD" }
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String,Object>> paymentCallback(@RequestBody Map<String,Object> body) {
        try {
            Integer orderId = body.get("orderId") != null ? Integer.valueOf(body.get("orderId").toString()) : null;
            String qrString = body.get("qrString") != null ? body.get("qrString").toString() : null;
            Double amount = body.get("amount") != null ? Double.valueOf(body.get("amount").toString()) : null;
            String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";
            String transactionRef = body.get("transactionRef") != null ? body.get("transactionRef").toString() : null;

            if (orderId == null || qrString == null || amount == null) {
                return ResponseEntity.badRequest().body(Map.of("status","error","message","Missing fields"));
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
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status","error","message","verification_failed", "orderId", orderId));
            }

            // transactionRef from bank/backend is required to mark payment as completed.
            if (transactionRef == null || transactionRef.isBlank()) {
                logger.info("Missing transactionRef for orderId={}. Rejecting persist until bank callback provides a transaction reference.", orderId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status","error","message","missing_transactionRef" , "orderId", orderId));
            }

            // Persist payment record only when verified
            Orders order = orderRepo.findById(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status","error","message","Order not found"));
            }

            // Use transactionRef for idempotency if present
            if (transactionRef != null) {
                java.util.Optional<BakongPayment> byRef = bakongPaymentRepo.findByTransactionRef(transactionRef);
                if (byRef.isPresent()) {
                    return ResponseEntity.ok(Map.of("status","success","message","payment already recorded","orderId", orderId, "paymentId", byRef.get().getId()));
                }
            }

            BakongPayment payment = new BakongPayment();
            payment.setAmount(amount);
            payment.setCurrency(currency);
            payment.setQrString(qrString);
            payment.setTransactionRef(transactionRef);
            payment.setCreatedAt(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            payment.setOrder(order);
            bakongPaymentRepo.save(payment);

            // Aggregate collected amount and set order status accordingly
            java.util.List<BakongPayment> payments = bakongPaymentRepo.findAllByOrder_Id(orderId);
            double collected = payments.stream().mapToDouble(p -> p.getAmount() == null ? 0.0 : p.getAmount()).sum();
            double total = order.getTotalAmount();
            if (total > 0 && collected >= total) {
                order.setStatus("completed");
            } else if (collected > 0) {
                order.setStatus("partial");
            }
            String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            order.setUpdatedAt(now);
            orderRepo.save(order);

            return ResponseEntity.ok(Map.of("status","success","orderId", orderId, "verified", true, "collected", collected, "total", total));
        } catch (Exception e) {
            logger.error("Error in payment callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status","error","message","callback failed"));
        }
    }
}