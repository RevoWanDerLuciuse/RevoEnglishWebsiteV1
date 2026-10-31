package org.example.revoenglishwebsite.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** Expects an already normalised (trimmed, lower-case) login. */
    Optional<UserAccount> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByRole(Role role);

    List<UserAccount> findAllByRoleOrderByCreatedAtDesc(Role role);

    Optional<UserAccount> findByIdAndRole(Long id, Role role);

    /** Row lock on the account; serialises subscription changes per student (e.g. "one active subscription"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
