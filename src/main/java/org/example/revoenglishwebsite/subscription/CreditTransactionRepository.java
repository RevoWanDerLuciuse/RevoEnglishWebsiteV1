package org.example.revoenglishwebsite.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Append-only: the service only inserts; there are no update or delete operations on the ledger. */
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findBySubscriptionIdOrderByCreatedAtAscIdAsc(Long subscriptionId);

    List<CreditTransaction> findBySubscriptionIdIn(Collection<Long> subscriptionIds);

    Optional<CreditTransaction> findByRequestKey(String requestKey);

    boolean existsByReversesTransactionId(Long transactionId);
}
