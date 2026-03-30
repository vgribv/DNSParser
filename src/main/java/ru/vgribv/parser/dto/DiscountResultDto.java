package ru.vgribv.parser.dto;

import ru.vgribv.parser.entity.PriceHistory;
import ru.vgribv.parser.entity.Product;
import java.util.List;

public record DiscountResultDto(
        List<Product> discountedProducts, List<PriceHistory> historyRecords)
{}