package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.CambiarPasswordRequestDTO;
import com.example.pagaopiu.dto.LoginRequestDTO;
import com.example.pagaopiu.dto.UsuarioInfoDTO;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthRestController(
            AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO requestDTO, HttpServletRequest request) {
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(requestDTO.getUsername(), requestDTO.getPassword());
            Authentication authentication = authenticationManager.authenticate(token);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
            UsuarioInfoDTO info = UsuarioInfoDTO.builder()
                    .id(principal.getId())
                    .username(principal.getUsername())
                    .nombre(principal.getNombre())
                    .rol(principal.getUsuario().getRol().name())
                    .sucursalId(principal.getSucursal() != null ? principal.getSucursal().getId() : null)
                    .sucursalNombre(principal.getSucursal() != null ? principal.getSucursal().getNombre() : null)
                    .activo(principal.getUsuario().isActivo())
                    .build();

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas: usuario o contraseña no válidos."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autenticado."));
        }
        UsuarioInfoDTO info = UsuarioInfoDTO.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .nombre(principal.getNombre())
                .rol(principal.getUsuario().getRol().name())
                .sucursalId(principal.getSucursal() != null ? principal.getSucursal().getId() : null)
                .sucursalNombre(principal.getSucursal() != null ? principal.getSucursal().getNombre() : null)
                .activo(principal.getUsuario().isActivo())
                .build();
        return ResponseEntity.ok(info);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada exitosamente."));
    }

    @PostMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(
            @RequestBody CambiarPasswordRequestDTO requestDTO,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autenticado."));
        }
        if (requestDTO == null || requestDTO.getPasswordActual() == null || requestDTO.getPasswordActual().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña actual es obligatoria."));
        }
        if (requestDTO.getPasswordNuevo() == null || requestDTO.getPasswordNuevo().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña es obligatoria."));
        }
        if (requestDTO.getPasswordNuevo().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe tener al menos 6 caracteres."));
        }
        if (requestDTO.getPasswordConfirmacion() != null && !requestDTO.getPasswordNuevo().equals(requestDTO.getPasswordConfirmacion())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña y la confirmación no coinciden."));
        }

        Usuario usuario = usuarioRepository.findById(principal.getId())
                .orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }

        if (!passwordEncoder.matches(requestDTO.getPasswordActual(), usuario.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña actual es incorrecta."));
        }

        usuario.setPasswordHash(passwordEncoder.encode(requestDTO.getPasswordNuevo()));
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada exitosamente."));
    }
}
