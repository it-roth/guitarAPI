package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.guitarapi.models.BakongPayment;

@Repository
public interface BakongPaymentRepo extends JpaRepository<BakongPayment, Long> {

	// Find a payment by the associated order id
	java.util.Optional<BakongPayment> findByOrder_Id(int orderId);

	// Find all payments for an order
	java.util.List<BakongPayment> findAllByOrder_Id(int orderId);

	// Find by transaction reference for idempotency
	java.util.Optional<BakongPayment> findByTransactionRef(String transactionRef);

    

}
