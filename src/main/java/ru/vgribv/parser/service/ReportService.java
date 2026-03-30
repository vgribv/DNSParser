package ru.vgribv.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class ReportService {
    @Value("${project.path}")
    private String projectPath;

    public Optional<File> getLogReport() {
        Path directory = Paths.get(projectPath + "data");
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith("report_"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .map(Path::toFile);
        } catch (IOException e) {
            log.error("Ошибка при поиске последнего лога: ", e);
            return Optional.empty();
        }
    }

    public Optional<File> getProductsReport() {
        File file = new File(projectPath + "data/productsReport.csv");
        return file.exists() ? Optional.of(file) : Optional.empty();
    }

    public Optional<File> getProductsPercentReport() {
        File file = new File(projectPath + "data/productsPercentReport.csv");
        return file.exists() ? Optional.of(file) : Optional.empty();
    }
}
