package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Backs the "Follow" directory page - every VERIFIED user, most-followed first, ties broken
    // alphabetically by username so the order is stable rather than reshuffling on every reload
    // for tied (commonly zero-follower) accounts. LEFT JOIN so a user with no followers at all
    // still appears with a count of 0, rather than being excluded the way an inner join would.
    // countQuery is explicit since Spring can't reliably derive one from a GROUP BY query - one
    // row per User regardless of follower count, so counting User itself is correct here.
    //
    // "u.emailVerified IS NULL OR u.emailVerified = true" (not just "= true") on purpose -
    // emailVerified is a nullable Boolean specifically so existing rows from before this column
    // existed default to verified (User.isEmailVerified() treats null the same way); a plain
    // "= true" would silently drop every one of those real, already-verified legacy accounts
    // from this page. An unverified row (emailVerified = false) is excluded until its owner
    // actually clicks the link - before that, the account isn't confirmed to belong to whoever
    // typed that email, so it shouldn't be publicly discoverable/followable yet.
    @Query(value = "SELECT u AS user, COUNT(f) AS followerCount FROM User u LEFT JOIN Follow f ON f.followed = u " +
            "WHERE u.emailVerified IS NULL OR u.emailVerified = true " +
            "GROUP BY u ORDER BY COUNT(f) DESC, u.username ASC",
            countQuery = "SELECT COUNT(u) FROM User u WHERE u.emailVerified IS NULL OR u.emailVerified = true")
    Page<UserFollowerCount> findAllOrderByFollowerCountDesc(Pageable pageable);
}
