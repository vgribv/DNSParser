package ru.vgribv.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.repository.ProductRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private final ProductRepository productRepository;
    private final String linkPrefix;

    public FileWriteService(ProductRepository productRepository, @Value("${dns.link.prefix}") String linkPrefix) {
        this.productRepository = productRepository;
        this.linkPrefix = linkPrefix;
    }

    public void updateAllReports(List<Product> bufferNew, List<Product> bufferPriceHasDecreased, List<Product> bufferRemoved){
        createReportFile(bufferNew, bufferPriceHasDecreased, bufferRemoved);
        deleteOldReports();
        createProductsFile();
        createPercentReport();
    }

    private void createProductsFile() {
        Path path = Paths.get("data", "productsReport.csv");
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("\uFEFF");
            sb.append("Название;Скидочная цена;Полная цена;Категория;Ссылка\n");
            for (Product product : productRepository.findAll()) {
                sb.append(product.getName().replace(";", " ")).append(";")
                        .append(product.getDiscountPrice()).append(";")
                        .append(product.getFullPrice()).append(";")
                        .append(product.getCategory().getName()).append(";")
                        .append(linkPrefix).append(product.getLinkId()).append("\n");
            }
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            logInfo(path);
        } catch (IOException e) {
            log.error("Ошибка в блоке записи отчета: ", e);
        }
    }

    private void createPercentReport() {
        Path path = Paths.get("data", "productsPercentReport.csv");

        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("\uFEFF");
            sb.append("Скидка %;Название;Скидочная цена;Полная цена;Категория;Ссылка\n");

            for(Product product : productRepository.findAllOrderByDiscountPercentDesc()){
                int percent = (product.getFullPrice() - product.getDiscountPrice()) * 100 / product.getFullPrice();

                sb.append(percent).append("%;")
                        .append(product.getName().replace(";", " ")).append(";")
                        .append(product.getDiscountPrice()).append(";")
                        .append(product.getFullPrice()).append(";")
                        .append(product.getCategory().getName()).append(";")
                        .append(linkPrefix).append(product.getLinkId()).append("\n");
            }

            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            logInfo(path);
        } catch (IOException e) {
            log.error("Ошибка в блоке записи отчета: ", e);
        }
    }

    private void createReportFile(List<Product> bufferNew, List<Product> bufferPriceHasDecreased, List<Product> bufferRemoved) {

        String date = LocalDate.now().toString();
        Path path = Paths.get("data", "report_" + date + ".csv");

        try {
            Files.createDirectories(path.getParent());
            boolean isNewFile = Files.notExists(path);

            StringBuilder sb = new StringBuilder();

            if (isNewFile) {
                sb.append("\uFEFF");
                sb.append("Время;Тип изменения;Название;Новая цена;Старая цена;Полная цена;Ссылка\n");
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            appendBuffer(sb, timestamp, "Новый товар", bufferNew);
            appendBuffer(sb, timestamp, "Понижение цены", bufferPriceHasDecreased);
            appendBuffer(sb, timestamp, "Продано", bufferRemoved);

            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            logInfo(path);
        } catch (IOException e) {
            log.error("Ошибка в блоке записи отчета: ", e);
        }
    }

    private void appendBuffer(StringBuilder sb, String timestamp, String type, List<Product> buffer) {
        if (buffer == null || buffer.isEmpty()) return;

        for (Product product : buffer) {
            Integer current = product.getDiscountPrice();
            Integer old = product.getOldDiscountPrice();
            Integer full = product.getFullPrice();
            boolean isChanged = old != null && !old.equals(current);
            sb.append(timestamp).append(";")
                    .append(type).append(";")
                    .append(product.getName().replace(";", " ")).append(";")
                    .append(isChanged ? current : "").append(";")
                    .append(isChanged ? old : current).append(";")
                    .append(full != null ? full : "").append(";")
                    .append(linkPrefix).append(product.getLinkId()).append("\n");
        }
    }

    private void deleteOldReports() {
        Path directory = Paths.get("data");
        int daysLimit = 7;
        Instant threshold = Instant.now().minus(daysLimit, ChronoUnit.DAYS);

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().startsWith("report_")).filter(path -> {
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

    private void logInfo(Path path) {
        log.info("Отчет успешно обновлен: {}", path);
    }
}
