package com.example.pagaopiu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambiarPasswordRequestDTO {
    private String passwordActual;
    private String passwordNuevo;
    private String passwordConfirmacion;
}
