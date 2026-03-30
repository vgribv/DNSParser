package ru.vgribv.parser.repository;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.vgribv.parser.entity.UserTelegram;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTelegramRepository extends JpaRepository<UserTelegram, Long> {
    @Override
    @NonNull
    List<UserTelegram> findAll();

    List<UserTelegram> findAllByIsActive(boolean isActive);

    Optional<UserTelegram> findByChatId(long chatId);

    @Query("SELECT u.chatId FROM UserTelegram u")
    List<Long> findAllIds();
}
