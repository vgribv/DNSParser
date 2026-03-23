package ru.vgribv.parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vgribv.parser.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    void deleteByUpdatedAtBefore(LocalDateTime updatedAtBefore);

    List<Product> findAllByUpdatedAtBefore(LocalDateTime updatedAtBefore);

    Optional<Product> findProductByLinkId(String productId);
}
