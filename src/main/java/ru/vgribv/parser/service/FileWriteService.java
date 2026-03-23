package ru.vgribv.parser.service;

import org.springframework.stereotype.Service;
import ru.vgribv.parser.entity.Product;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FileWriteService {
    public void writeFile(List<Product> bufferNew, List<Product> bufferPriceHasDecreased, List<Product> bufferRemoved) throws IOException {

        Path directory = Paths.get("data");
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        Path path = directory.resolve("goodsLog.txt");

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== ОТЧЕТ ОТ ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(" ===\n");


        appendBuffer(sb, "НОВЫЕ ТОВАРЫ:", bufferNew);
        appendBuffer(sb, "ПОНИЖЕНИЕ ЦЕНЫ:", bufferPriceHasDecreased);
        appendBuffer(sb, "УДАЛЕННЫЕ ТОВАРЫ:", bufferRemoved);

        Files.writeString(path, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void appendBuffer(StringBuilder sb, String title, List<Product> buffer) {
        if (buffer != null && !buffer.isEmpty()) {
            sb.append("\n").append(title).append("\n");

            String line;
            String link = "https://www.dns-shop.ru/catalog/markdown/";

            for (Product p : buffer) {
                switch (title){
                    case "НОВЫЕ ТОВАРЫ" ->
                        line = p.getName() + " | " + p.getDiscountPrice() + "руб./" + p.getFullPrice() + "руб. " +
                                "категория:" + p.getCategory() + " | " + link + p.getId() + "\n";

                    case "ПОНИЖЕНИЕ ЦЕНЫ" ->
                        line = p.getName() + " | новая цена: " + p.getDiscountPrice() + "руб. | старая цена: " +
                                p.getOldDiscountPrice() + " | " + link + p.getId() + "\n";

                    case "УДАЛЕННЫЕ ТОВАРЫ" ->
                        line = "Купили: " + p.getName() + " за " + p.getDiscountPrice() + "руб." + "\n";
                    default ->
                            line = p.getName();
                }
                sb.append("- ").append(line).append("\n");
            }

        }
    }
}
