package tn.digitworks.naanayamthavanai.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentIdOrderById(Long paymentId);

    List<PaymentAllocation> findByPaymentIdIn(Collection<Long> paymentIds);
}
