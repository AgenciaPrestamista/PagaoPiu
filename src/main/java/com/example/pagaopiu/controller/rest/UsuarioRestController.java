package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.CrearUsuarioRequestDTO;
import com.example.pagaopiu.dto.ResetPasswordRequestDTO;
import com.example.pagaopiu.dto.SucursalDTO;
import com.example.pagaopiu.dto.UsuarioInfoDTO;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.UsuarioService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioRestController {

    private final UsuarioService usuarioService;

    public UsuarioRestController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioInfoDTO>> listarUsuarios() {
        return ResponseEntity.ok(usuarioService.listarUsuarios());
    }

    @PostMapping
    public ResponseEntity<?> crearUsuario(@RequestBody CrearUsuarioRequestDTO dto) {
        try {
            UsuarioInfoDTO creado = usuarioService.crearUsuario(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/activo")
    public ResponseEntity<?> cambiarEstadoActivo(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        try {
            boolean activo = body != null && Boolean.TRUE.equals(body.get("activo"));
            String usernameEjecutor = principal != null ? principal.getUsername() : null;
            UsuarioInfoDTO actualizado = usuarioService.cambiarEstadoActivo(id, activo, usernameEjecutor);
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordRequestDTO dto) {
        try {
            if (dto == null || dto.getNuevoPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña es requerida."));
            }
            usuarioService.resetPassword(id, dto.getNuevoPassword());
            return ResponseEntity.ok(Map.of("mensaje", "Contraseña restablecida exitosamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sucursales")
    public ResponseEntity<List<SucursalDTO>> listarSucursales() {
        return ResponseEntity.ok(usuarioService.listarSucursales());
    }
}
