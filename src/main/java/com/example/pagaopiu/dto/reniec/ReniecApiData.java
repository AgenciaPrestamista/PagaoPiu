package com.example.pagaopiu.dto.reniec;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ReniecApiData(
    String numero,
    @JsonProperty("nombre_completo") String nombreCompleto,
    String nombres,
    @JsonProperty("apellido_paterno") String apellidoPaterno,
    @JsonProperty("apellido_materno") String apellidoMaterno,
    @JsonProperty("codigo_verificacion") Integer codigoVerificacion,
    String direccion,
    @JsonProperty("direccion_completa") String direccionCompleta,
    @JsonProperty("ubigeo_reniec") String ubigeoReniec,
    @JsonProperty("ubigeo_sunat") String ubigeoSunat,
    List<String> ubigeo
) {
}
