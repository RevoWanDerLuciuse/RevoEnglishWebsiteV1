package org.example.revoenglishwebsite.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Row lock (SELECT … FOR UPDATE): every ledger write for a subscription first takes this lock, so
     * concurrent operations on the same subscription run one after another and the balance check is reliable.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);

    List<Subscription> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Subscription> findByStudentIdAndStatus(Long studentId, SubscriptionStatus status);

    List<Subscription> findByStatusIn(Collection<SubscriptionStatus> statuses);

    List<Subscription> findByStatusAndExpiresOnBefore(SubscriptionStatus status, LocalDate day);

    Optional<Subscription> findByRequestKey(String requestKey);
}
