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
public class UsuarioInfoDTO {
    private Long id;
    private String username;
    private String nombre;
    private String rol;
    private Long sucursalId;
    private String sucursalNombre;
    private Boolean activo;
}
