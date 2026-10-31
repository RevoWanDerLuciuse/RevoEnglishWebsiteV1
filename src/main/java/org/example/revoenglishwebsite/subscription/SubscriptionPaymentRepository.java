package org.example.revoenglishwebsite.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findBySubscriptionIdOrderByPaidOnAscIdAsc(Long subscriptionId);

    Optional<SubscriptionPayment> findByRequestKey(String requestKey);
}
