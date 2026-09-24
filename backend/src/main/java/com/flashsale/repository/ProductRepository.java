package com.flashsale.repository;

import com.flashsale.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * A single atomic SQL UPDATE expressed as arithmetic (stock = stock - qty),
     * guarded by a WHERE clause that only allows the update when enough stock
     * exists. This is safe from races without needing any Java-level locking,
     * because Postgres itself serializes the individual UPDATE statement.
     * Returns the number of rows updated: 1 on success, 0 if the guard failed
     * (which should never happen here, since the Redis Lua script already
     * verified sufficient stock — a 0 here signals a cache/DB desync).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
           "WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}