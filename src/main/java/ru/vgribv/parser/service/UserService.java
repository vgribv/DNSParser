package ru.vgribv.parser.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.entity.UserTelegram;
import ru.vgribv.parser.repository.UserTelegramRepository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserTelegramRepository userTelegramRepository;
    private final Set<Long> activeUsersCache = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void loadCache() {
        List<Long> ids = userTelegramRepository.findAllIds();
        activeUsersCache.addAll(ids);
        log.info("Загружено {} пользователей в кэш", activeUsersCache.size());
    }

    public boolean isUserNotAuthorized(Long chatId) {
        return !activeUsersCache.contains(chatId);
    }

    public void registerUser(long chatId, String name) {
        userTelegramRepository.save(new UserTelegram(chatId, name));
        activeUsersCache.add(chatId);
    }
}

