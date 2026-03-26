package ru.vgribv.parser.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import ru.vgribv.parser.entity.SearchFilter;

import java.util.List;

public interface SearchFilterRepository extends JpaRepository<SearchFilter, String> {
    List<SearchFilter> getAllByChatId(long chatId);

    SearchFilter getFirstById(Long id);

    @Transactional
    void deleteById(Long id);
}
