package com.fusionxpay.order.repository;

import com.fusionxpay.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);

    // Pagination with optional status filter
    Page<Order> findByStatus(String status, Pageable pageable);

    // Pagination with optional userId (merchantId) filter
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // Pagination with both status and userId filter
    Page<Order> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    // Custom query for flexible filtering
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:userId IS NULL OR o.userId = :userId) AND " +
           "(:orderNumber IS NULL OR o.orderNumber LIKE %:orderNumber%)")
    Page<Order> findWithFilters(
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("orderNumber") String orderNumber,
            Pageable pageable);
}