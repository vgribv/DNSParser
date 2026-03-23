package ru.vgribv.parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vgribv.parser.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, String> {
}
