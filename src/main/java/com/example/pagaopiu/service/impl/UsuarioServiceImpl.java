package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.CrearUsuarioRequestDTO;
import com.example.pagaopiu.dto.SucursalDTO;
import com.example.pagaopiu.dto.UsuarioInfoDTO;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.Rol;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.service.UsuarioService;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(
            UsuarioRepository usuarioRepository,
            SucursalRepository sucursalRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.sucursalRepository = sucursalRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioInfoDTO> listarUsuarios() {
        return usuarioRepository.findAllByOrderByIdAsc().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioInfoDTO crearUsuario(CrearUsuarioRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos del usuario son obligatorios.");
        }

        String username = dto.getUsername() != null ? dto.getUsername().trim().toLowerCase() : "";
        if (username.isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (username.contains(" ")) {
            throw new IllegalArgumentException("El nombre de usuario no debe contener espacios.");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("El nombre de usuario no puede exceder los 50 caracteres.");
        }
        if (usuarioRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El usuario '" + username + "' ya existe en el sistema.");
        }

        String nombre = dto.getNombre() != null ? dto.getNombre().trim() : "";
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio.");
        }
        if (nombre.length() > 200) {
            throw new IllegalArgumentException("El nombre completo no puede exceder los 200 caracteres.");
        }

        String password = dto.getPassword() != null ? dto.getPassword().trim() : "";
        if (password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }

        Rol rol = dto.getRol();
        if (rol == null) {
            throw new IllegalArgumentException("El rol del usuario es obligatorio (ASESOR o GERENTE).");
        }

        Sucursal sucursal = null;
        if (rol == Rol.ASESOR) {
            if (dto.getSucursalId() == null) {
                throw new IllegalArgumentException("Debe asignar una sucursal para un asesor.");
            }
            sucursal = sucursalRepository.findById(dto.getSucursalId())
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal seleccionada no existe."));
        } else if (dto.getSucursalId() != null) {
            sucursal = sucursalRepository.findById(dto.getSucursalId())
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal seleccionada no existe."));
        }

        Usuario nuevoUsuario = Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .nombre(nombre)
                .rol(rol)
                .sucursal(sucursal)
                .activo(true)
                .build();

        Usuario guardado = usuarioRepository.save(nuevoUsuario);
        return mapToDTO(guardado);
    }

    @Override
    @Transactional
    public UsuarioInfoDTO cambiarEstadoActivo(Long id, boolean activo, String usernameEjecutor) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        if (usernameEjecutor != null && usuario.getUsername().equalsIgnoreCase(usernameEjecutor) && !activo) {
            throw new IllegalArgumentException("No puede desactivar su propia cuenta de usuario en uso.");
        }

        if (!activo && usuario.getRol() == Rol.GERENTE) {
            long otrosGerentesActivos = usuarioRepository.findAll().stream()
                    .filter(u -> u.getRol() == Rol.GERENTE && u.isActivo() && !u.getId().equals(id))
                    .count();
            if (otrosGerentesActivos == 0) {
                throw new IllegalArgumentException("No se puede desactivar al único Gerente activo del sistema.");
            }
        }

        usuario.setActivo(activo);
        Usuario guardado = usuarioRepository.save(usuario);
        return mapToDTO(guardado);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String nuevoPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        if (nuevoPassword == null || nuevoPassword.trim().length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
        }

        usuario.setPasswordHash(passwordEncoder.encode(nuevoPassword.trim()));
        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SucursalDTO> listarSucursales() {
        return sucursalRepository.findAll().stream()
                .map(s -> SucursalDTO.builder()
                        .id(s.getId())
                        .nombre(s.getNombre())
                        .build())
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    private UsuarioInfoDTO mapToDTO(Usuario u) {
        return UsuarioInfoDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nombre(u.getNombre())
                .rol(u.getRol().name())
                .sucursalId(u.getSucursal() != null ? u.getSucursal().getId() : null)
                .sucursalNombre(u.getSucursal() != null ? u.getSucursal().getNombre() : null)
                .activo(u.isActivo())
                .build();
    }
}
