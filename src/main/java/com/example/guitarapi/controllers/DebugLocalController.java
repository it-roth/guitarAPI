package com.example.guitarapi.controllers;

import com.example.guitarapi.services.KHQRGenerator;
import com.example.guitarapi.services.PaymentEventService;
import com.example.guitarapi.utils.ResponseUtil;
import com.example.guitarapi.models.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/debug")
public class DebugLocalController {

    private final KHQRGenerator khqrGenerator;
    private final PaymentEventService paymentEventService;

    // in-memory stores
    private final Map<Integer, Map<String, Object>> orders = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final AtomicInteger orderIdSeq = new AtomicInteger(1000);
    private final AtomicInteger paymentIdSeq = new AtomicInteger(1);

    public DebugLocalController(KHQRGenerator khqrGenerator, PaymentEventService paymentEventService) {
        this.khqrGenerator = khqrGenerator;
        this.paymentEventService = paymentEventService;
    }

    // Create a local in-memory order for testing
    @PostMapping("/local-order")
    public ResponseEntity<ApiResponse> createLocalOrder(@RequestBody(required = false) Map<String,Object> body) {
        double total = 1.00;
        if (body != null && body.get("total") != null) {
            try { total = Double.parseDouble(body.get("total").toString()); } catch (Exception ignored) {}
        }
        int id = orderIdSeq.incrementAndGet();
        Map<String,Object> order = new ConcurrentHashMap<>();
        order.put("id", id);
        order.put("totalAmount", total);
        order.put("status", "pending");
        order.put("paymentStatus", "unpaid");
        order.put("createdAt", LocalDateTime.now().toString());
        orders.put(id, order);
        return ResponseEntity.ok(ResponseUtil.success(Map.of("orderId", id, "order", order)));
    }

    // List local orders
    @GetMapping("/local-orders")
    public ResponseEntity<ApiResponse> listLocalOrders() {
        return ResponseEntity.ok(ResponseUtil.success(orders.values()));
    }

    // Generate a Merchant KHQR without using JPA/orderRepo
    @PostMapping("/create-khqr")
    public ResponseEntity<ApiResponse> createKhqr(@RequestBody Map<String,Object> body) {
        try {
            java.math.BigDecimal amount = body.get("amount") != null ? new java.math.BigDecimal(body.get("amount").toString()) : new java.math.BigDecimal("1.00");
            String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";
            Integer orderId = body.get("orderId") != null ? Integer.valueOf(body.get("orderId").toString()) : null;

            String qrString = khqrGenerator.generateKHQRString(amount.doubleValue(), currency);
            byte[] qrImage = khqrGenerator.generateQRImage(qrString);
            String imageBase64 = java.util.Base64.getEncoder().encodeToString(qrImage);

        // Save QR into in-memory order (so we can serve an HTML page later)
        if (orderId != null && orders.containsKey(orderId)) {
        orders.get(orderId).put("qrString", qrString);
        orders.get(orderId).put("imageBase64", imageBase64);
        }

        Map<String,Object> resp = Map.of(
            "qrString", qrString,
            "imageBase64", imageBase64,
            "amount", amount,
            "currency", currency,
            "orderId", orderId
        );
            return ResponseEntity.ok(ResponseUtil.success(resp));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ResponseUtil.failure(15, "create_khqr_failed"));
        }
    }

    // Simulate bank callback / payment for local order
    @PostMapping("/local-pay")
    public ResponseEntity<ApiResponse> localPay(@RequestBody Map<String,Object> body) {
        try {
            Integer orderId = body.get("orderId") != null ? Integer.valueOf(body.get("orderId").toString()) : null;
            String qrString = body.get("qrString") != null ? body.get("qrString").toString() : null;
            java.math.BigDecimal amount = body.get("amount") != null ? new java.math.BigDecimal(body.get("amount").toString()) : null;
            String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";
            String transactionRef = body.get("transactionRef") != null ? body.get("transactionRef").toString() : java.util.UUID.randomUUID().toString();

            if (orderId == null || !orders.containsKey(orderId)) {
                return ResponseEntity.status(404).body(ResponseUtil.failure(1, "Order not found"));
            }

            int pid = paymentIdSeq.incrementAndGet();
            Map<String,Object> payment = new ConcurrentHashMap<>();
            payment.put("id", pid);
            payment.put("orderId", orderId);
            payment.put("amount", amount != null ? amount : orders.get(orderId).get("totalAmount"));
            payment.put("currency", currency);
            payment.put("qrString", qrString);
            payment.put("transactionRef", transactionRef);
            payment.put("status", "success");
            payment.put("createdAt", LocalDateTime.now().toString());
            payments.put(pid, payment);

            // update in-memory order status
            Map<String,Object> order = orders.get(orderId);
            order.put("paymentStatus", "paid");
            order.put("status", "completed");
            order.put("updatedAt", LocalDateTime.now().toString());

            // publish SSE event to subscribers
            try {
                paymentEventService.publishEvent(orderId, "payment", Map.of("status","success","paymentId", pid, "amount", payment.get("amount")));
            } catch (Exception e) {
                // swallow but log
            }

            return ResponseEntity.ok(ResponseUtil.success(Map.of("message","local payment recorded","orderId", orderId, "paymentId", pid)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ResponseUtil.failure(15, "local_pay_failed"));
        }
    }

    @GetMapping("/local-payments")
    public ResponseEntity<ApiResponse> listPayments() {
        return ResponseEntity.ok(ResponseUtil.success(payments.values()));
    }

    // Serve an HTML page with the QR image for an order so you can scan it with a phone
    @GetMapping(value = "/show-qr/{orderId}")
    public ResponseEntity<String> showQr(@PathVariable int orderId) {
        if (!orders.containsKey(orderId)) {
            return ResponseEntity.status(404).body("Order not found");
        }
        Object b64 = orders.get(orderId).get("imageBase64");
        if (b64 == null) {
            return ResponseEntity.status(404).body("QR not generated for order");
        }
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>KHQR " + orderId + "</title></head><body style=\"display:flex;align-items:center;justify-content:center;height:100vh;\"><div><img alt=\"KHQR\" src=\"data:image/png;base64," + b64.toString() + "\" style=\"max-width:100%;height:auto;\"><p style=\"text-align:center;font-family:sans-serif\">Order: " + orderId + "</p></div></body></html>";
        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
    }
}
