package ru.vgribv.parser.event;

public record ParsingResultEvent(boolean success, String message) {}
