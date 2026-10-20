package com.example.pagaopiu.dto;

import com.example.pagaopiu.model.enums.Rol;
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
public class CrearUsuarioRequestDTO {
    private String username;
    private String password;
    private String nombre;
    private Rol rol;
    private Long sucursalId;
}
