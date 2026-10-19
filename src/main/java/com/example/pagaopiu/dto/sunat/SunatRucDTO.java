package com.example.pagaopiu.dto.sunat;

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
public class SunatRucDTO {
    private String ruc;
    private String razonSocial;
    private String estado;
    private String condicion;
    private String direccion;
    private String representanteNombre;
    private String representanteDocumento;
    private String representanteCargo;
    @Builder.Default
    private boolean activo = true;
    @Builder.Default
    private boolean habido = true;
}
