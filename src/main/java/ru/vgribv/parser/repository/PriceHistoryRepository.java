package ru.vgribv.parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.vgribv.parser.entity.PriceHistory;

import java.time.LocalDateTime;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    @Modifying (clearAutomatically = true)
    @Query("DELETE FROM PriceHistory ph WHERE ph.product IN (SELECT p FROM Product p WHERE p.updatedAt < :time)")
    void deleteHistoryForOldProducts(@Param("time") LocalDateTime time);
}
