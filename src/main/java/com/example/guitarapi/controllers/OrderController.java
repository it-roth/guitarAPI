package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.guitarapi.repository.OrderRepo;
import com.example.guitarapi.models.Orders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class OrderController {

    private OrderRepo orders;

    public OrderController(OrderRepo orders) {
        this.orders = orders;
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
    public String createOrder(
            @RequestParam("customer_name") String customerName,
            @RequestParam("shipping_address") String shippingAddress,
            @RequestParam("status") String status,
            @RequestParam("total_amount") double totalAmount,
            @RequestParam("user_id") int userId) {

         try {
             // Get current timestamp
             String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
             
             // Save order
             Orders newOrder = new Orders(0, currentTime, customerName, shippingAddress, status, totalAmount, currentTime, userId);
             this.orders.save(newOrder);
             return "Order Inserted Successfully!";
         } catch (Exception e) {
             e.printStackTrace();
             return "Failed to Insert Order!";
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
}