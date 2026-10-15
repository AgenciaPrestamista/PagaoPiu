package com.example.pagaopiu.dto.reniec;

public record ReniecApiResponse(
    boolean success,
    String message,
    ReniecApiData data
) {
}
