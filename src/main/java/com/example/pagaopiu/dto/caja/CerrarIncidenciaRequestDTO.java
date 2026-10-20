package com.example.pagaopiu.dto.caja;

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
public class CerrarIncidenciaRequestDTO {
    private String resolucion;
}
