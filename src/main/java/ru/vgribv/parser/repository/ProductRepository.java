package ru.vgribv.parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.vgribv.parser.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Product p WHERE p.updatedAt < :timestamp")
    void deleteOldProductsInBatch(@Param("timestamp") LocalDateTime timestamp);

    List<Product> findAllByUpdatedAtBefore(LocalDateTime updatedAtBefore);

    Optional<Product> findProductByLinkId(String productId);

    @Query("SELECT p FROM Product p JOIN FETCH p.category")
    List<Product> findAllWithCategories();

    @Query("SELECT p FROM Product p " +
            "WHERE p.fullPrice > 0 " +
            "ORDER BY ((p.fullPrice - p.discountPrice) * 100 / p.fullPrice) DESC")
    List<Product> findAllOrderByDiscountPercentDesc();
}