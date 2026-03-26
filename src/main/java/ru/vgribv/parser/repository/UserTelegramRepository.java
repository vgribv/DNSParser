package ru.vgribv.parser.repository;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import ru.vgribv.parser.entity.UserTelegram;

import java.util.List;
import java.util.Optional;

public interface UserTelegramRepository extends JpaRepository<UserTelegram, Long> {
    @Override
    @NonNull
    List<UserTelegram> findAll();

    List<UserTelegram> findAllByIsActive(boolean isActive);

    Optional<UserTelegram> findByChatId(long chatId);
}
