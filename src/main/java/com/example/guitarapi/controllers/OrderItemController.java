package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.guitarapi.repository.OrderItemRepo;
import com.example.guitarapi.models.OrderItems;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class OrderItemController {

    private OrderItemRepo orderItems;

    public OrderItemController(OrderItemRepo orderItems) {
        this.orderItems = orderItems;
    }

    @RequestMapping(path = "/order-items", method = RequestMethod.GET)
    public List<OrderItems> getAllOrderItems() {
        return this.orderItems.findAll();
    }

    @RequestMapping(path = "/order-items/{id}", method = RequestMethod.GET)
    public OrderItems getOrderItemById(@PathVariable int id) {
        return this.orderItems.findById(id).get();
    }

    @RequestMapping(path = "/order-items/{id}", method = RequestMethod.DELETE)
    public String deleteOrderItem(@PathVariable int id) {
        this.orderItems.deleteById(id);
        return "Successfully Deleted";
    }

    @RequestMapping(path = "/order-items", method = RequestMethod.POST)
    public String createOrderItem(
            @RequestParam("quantity") int quantity,
            @RequestParam("unit_price") double unitPrice,
            @RequestParam("order_id") int orderId,
            @RequestParam("product_id") int productId) {

         try {
             // Calculate total price
             double totalPrice = quantity * unitPrice;
             
             // Save order item
             OrderItems newOrderItem = new OrderItems(0, quantity, new java.math.BigDecimal(Double.toString(totalPrice)), new java.math.BigDecimal(Double.toString(unitPrice)), orderId, productId);
             this.orderItems.save(newOrderItem);
             return "Order Item Inserted Successfully!";
         } catch (Exception e) {
             e.printStackTrace();
             return "Failed to Insert Order Item!";
         }
    }

    @RequestMapping(path = "/order-items/{id}", method = RequestMethod.PUT)
    public String updateOrderItem(
            @PathVariable int id,
            @RequestParam("quantity") int quantity,
            @RequestParam("unit_price") double unitPrice,
            @RequestParam("order_id") int orderId,
            @RequestParam("product_id") int productId) {

        return this.orderItems.findById(id).map((item) -> {
            item.setQuantity(quantity);
            item.setUnitPrice(new java.math.BigDecimal(Double.toString(unitPrice)));
            item.setOrderId(orderId);
            item.setProductId(productId);
            
            // Recalculate total price
            java.math.BigDecimal totalPrice = new java.math.BigDecimal(Double.toString(unitPrice)).multiply(java.math.BigDecimal.valueOf(quantity));
            item.setTotalPrice(totalPrice);

            this.orderItems.save(item);
            return "Order Item Updated Successfully!";
        }).orElse("Order Item not found!");
    }
}