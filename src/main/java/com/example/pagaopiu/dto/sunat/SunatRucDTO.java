package com.example.pagaopiu.dto.sunat;

import java.util.ArrayList;
import java.util.List;
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
    /** Todos los representantes legales que SUNAT lista; si viene vacía se usa el representante principal. */
    @Builder.Default
    private List<Representante> representantes = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Representante {
        private String nombre;
        private String documento;
        private String cargo;
    }

    /** Representantes contra los que se puede validar a la persona que se presenta. */
    public List<Representante> todosLosRepresentantes() {
        if (representantes != null && !representantes.isEmpty()) {
            return representantes;
        }
        if (representanteDocumento == null || representanteDocumento.isBlank()) {
            return List.of();
        }
        return List.of(new Representante(representanteNombre, representanteDocumento, representanteCargo));
    }

    /** Busca, entre todos los representantes, el que tenga el DNI indicado. */
    public java.util.Optional<Representante> buscarRepresentantePorDocumento(String dni) {
        if (dni == null || dni.isBlank()) {
            return java.util.Optional.empty();
        }
        String buscado = dni.trim();
        return todosLosRepresentantes().stream()
                .filter(r -> r.getDocumento() != null && buscado.equals(r.getDocumento().trim()))
                .findFirst();
    }
}
