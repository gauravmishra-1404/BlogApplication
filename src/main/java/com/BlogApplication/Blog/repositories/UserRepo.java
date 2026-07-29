package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User,Integer> {
    @Override
    <S extends User> S save(S entity);

    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);

    // Accounts that predate the username feature (or came in via the DB shared with the other
    // app) - used by UsernameBackfillRunner to backfill every one of them once at startup,
    // rather than relying on each account happening to log back in (GlobalModelAttributes only
    // backfills the current viewer, not every other user whose posts/comments they see).
    List<User> findByUsernameIsNull();
}
