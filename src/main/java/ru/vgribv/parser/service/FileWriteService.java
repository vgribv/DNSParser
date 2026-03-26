package ru.vgribv.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.entity.Product;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileWriteService {

    public void writeFile(List<Product> bufferNew, List<Product> bufferPriceHasDecreased, List<Product> bufferRemoved) {

        String date = LocalDate.now().toString();
        Path path = Paths.get("data", "report_" + date + ".csv");

        try {
            Files.createDirectories(path.getParent());
            boolean isNewFile = Files.notExists(path);

            StringBuilder sb = new StringBuilder();

            if (isNewFile) {
                sb.append("Тип изменения;Название;Новая цена;Старая цена;Ссылка\n");
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            appendBuffer(sb, timestamp, "НОВАЯ ПОЗИЦИЯ", bufferNew);
            appendBuffer(sb, timestamp, "СКИДКА", bufferPriceHasDecreased);
            appendBuffer(sb, timestamp, "ПРОДАНО", bufferRemoved);

            Files.writeString(path, sb.toString(), Charset.forName("Windows-1251"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Отчет успешно обновлен: {}", path);

            deleteOldReports();
        } catch (IOException e) {
            log.error("Ошибка в блоке записи отчета: ", e);
        }
    }

    private void appendBuffer(StringBuilder sb, String timestamp, String type, List<Product> buffer) {
        if (buffer == null || buffer.isEmpty()) return;

        String linkPrefix = "https://www.dns-shop.ru";

        for (Product p : buffer) {
            sb.append(timestamp)
                    .append(type).append(";")
                    .append(p.getName().replace(";", " ")).append(";")
                    .append(p.getDiscountPrice()).append(";")
                    .append(p.getOldDiscountPrice() != null ? p.getOldDiscountPrice() : "").append(";")
                    .append(linkPrefix).append(p.getLinkId()).append("\n");
        }
    }

    private void deleteOldReports() {
        Path directory = Paths.get("data");
        int daysLimit = 7;
        Instant threshold = Instant.now().minus(daysLimit, ChronoUnit.DAYS);

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.toString().endsWith(".csv")).filter(path -> {
                        try {
                            FileTime lastModified = Files.getLastModifiedTime(path);
                            return lastModified.toInstant().isBefore(threshold);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.info("Удален старый отчет: {}", path.getFileName());
                        } catch (IOException e) {
                            log.error("Ну удалось удалить файл: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.error("Ошибка при очистке старых отчетов: ", e);
        }
    }
}
