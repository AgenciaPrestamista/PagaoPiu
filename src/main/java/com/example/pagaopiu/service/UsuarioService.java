package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.CrearUsuarioRequestDTO;
import com.example.pagaopiu.dto.SucursalDTO;
import com.example.pagaopiu.dto.UsuarioInfoDTO;
import java.util.List;

public interface UsuarioService {
    List<UsuarioInfoDTO> listarUsuarios();
    UsuarioInfoDTO crearUsuario(CrearUsuarioRequestDTO dto);
    UsuarioInfoDTO cambiarEstadoActivo(Long id, boolean activo, String usernameEjecutor);
    void resetPassword(Long id, String nuevoPassword);
    List<SucursalDTO> listarSucursales();
}
