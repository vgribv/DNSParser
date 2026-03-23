package ru.vgribv.parser.repository;

import org.springframework.data.repository.CrudRepository;
import ru.vgribv.parser.entity.Tracker;

import java.util.List;

public interface TrackerRepository extends CrudRepository<Tracker, Long> {
    boolean existsTrackerByChatIdAndLink(Long chatId, String link);

    List<Tracker> findAllByChatId(long chatId);

    Tracker findFirstByChatIdAndLink(long chatId, String trackerId);

    void deleteTrackerByChatIdAndId(Long chatId, Long id);

    List<Tracker> getAllByChatId(Long chatId);

    void deleteTrackerByChatIdAndLink(Long chatId, String link);
}
