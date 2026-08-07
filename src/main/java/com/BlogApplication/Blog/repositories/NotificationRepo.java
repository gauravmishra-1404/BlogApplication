package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(int recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(int recipientId);

    // Bulk mark-all-read (bell dropdown's "mark all as read") - one UPDATE instead of loading
    // every unread row into memory just to flip one field and save each individually.
    // @Transactional is required here (not just @Modifying) - a @Modifying @Query needs an
    // active transaction to run at all, and Spring Data only opens one automatically around a
    // repository method when the method itself is @Transactional; without it every call to this
    // method throws TransactionRequiredException before it ever reaches the database. Simple
    // derived reads/saves elsewhere in this codebase don't need this because Spring Data wraps
    // them in an implicit transaction on its own - only a hand-written bulk UPDATE/DELETE like
    // this one doesn't get that for free.
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    void markAllReadForRecipient(@Param("recipientId") int recipientId);
}
