package ru.vgribv.parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.vgribv.parser.entity.ArchivedProduct;

import java.time.LocalDateTime;

@Repository
public interface ArchivedProductRepository extends JpaRepository<ArchivedProduct, Long> {
    @Modifying
    @Query(value = "INSERT INTO archived_product (link_id, name, discount_price, full_price, category_id, archived_at) " +
            "SELECT link_id, name, discount_price, full_price, category_id, :now " +
            "FROM product WHERE updated_at < :archived_at", nativeQuery = true)
    void archiveOldProducts(@Param("archived_at") LocalDateTime archived_at, @Param("now") LocalDateTime now);
}
