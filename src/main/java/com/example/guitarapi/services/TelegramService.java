package com.example.guitarapi.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.guitarapi.models.Orders;
import com.example.guitarapi.models.OrderItems;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class TelegramService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String chatId;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    /**
     * Send order notification to Telegram with order details
     */
    public void sendOrderNotification(Orders order) {
        if (order == null) {
            logger.warn("sendOrderNotification called with null order");
            return;
        }
        if (!isConfigured()) {
            logger.warn("TelegramService not configured - telegram.bot.token or telegram.chat.id is empty");
            return;
        }

        try {
            String text = buildOrderMessage(order);
            sendMessage(text);
        } catch (Exception e) {
            logger.error("Failed to send Telegram order notification", e);
        }
    }

    /**
     * Send a plain text message to the configured chat
     */
    public void sendTextMessage(String text) {
        if (!isConfigured()) {
            logger.warn("TelegramService not configured - skipping sendTextMessage");
            return;
        }
        try {
            sendMessage(text);
        } catch (Exception e) {
            logger.error("Failed to send Telegram text message", e);
        }
    }

    private void sendMessage(String text) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "Markdown");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            logger.info("Telegram sendMessage response: {}", resp.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Failed to send Telegram message", e);
            throw e;
        }
    }

    private String buildOrderMessage(Orders order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎸 *New Order - PickAndPlay*\n\n");
        sb.append("📋 Order ID: *#").append(order.getId()).append("*\n");
        sb.append("👤 Customer: ")
                .append(escapeMarkdown(order.getCustomerName() != null ? order.getCustomerName() : "Guest"))
                .append("\n");
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        sb.append("💰 Total: *$").append(total).append("*\n");
        sb.append("📦 Status: ").append(order.getStatus() != null ? order.getStatus() : "N/A").append("\n");
        sb.append("📍 Shipping: ")
                .append(escapeMarkdown(order.getShippingAddress() != null ? order.getShippingAddress() : "N/A"))
                .append("\n\n");

        List<OrderItems> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            sb.append("*Items:*\n");
            for (OrderItems it : items) {
                String name = it.getProduct() != null && it.getProduct().getName() != null
                        ? escapeMarkdown(it.getProduct().getName())
                        : ("Product #" + it.getProductId());
                BigDecimal itemTotal = it.getTotalPrice() != null ? it.getTotalPrice() : BigDecimal.ZERO;
                sb.append("• ").append(name).append(" x").append(it.getQuantity())
                        .append(" - $").append(itemTotal).append("\n");
            }
        }

        sb.append("\n✅ Payment successful!");
        return sb.toString();
    }

    private String escapeMarkdown(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}
