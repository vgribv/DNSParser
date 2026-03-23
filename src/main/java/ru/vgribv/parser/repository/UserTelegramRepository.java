package ru.vgribv.parser.repository;

import org.springframework.data.repository.CrudRepository;
import ru.vgribv.parser.entity.UserTelegram;

import java.util.List;

public interface UserTelegramRepository extends CrudRepository<UserTelegram, Long> {
    @Override
    List<UserTelegram> findAll();
}
