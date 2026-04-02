package ru.vgribv.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductHtmlDto {
    private String imageUrl;
    private String condition;
    private String appearance;
    private String completeness;
    private String typeOfRepair;
}
