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
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO archived_product (id, link_id, name, discount_price, full_price, category_id, image_url, archived_at) " +
            "SELECT nextval('archived_product_sequence'), " +
            "link_id, name, discount_price, full_price, category_id, image_url, :now " +
            "FROM product WHERE updated_at < :archived_at", nativeQuery = true)
    void archiveOldProducts(@Param("archived_at") LocalDateTime archived_at, @Param("now") LocalDateTime now);

}
