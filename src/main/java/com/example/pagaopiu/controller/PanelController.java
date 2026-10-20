package com.example.pagaopiu.controller;

import com.example.pagaopiu.dto.CrearUsuarioRequestDTO;
import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.dto.ResetPasswordRequestDTO;
import com.example.pagaopiu.model.enums.Rol;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.PanelService;
import com.example.pagaopiu.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/panel")
public class PanelController {

    private final PanelService panelService;
    private final UsuarioService usuarioService;

    public PanelController(PanelService panelService, UsuarioService usuarioService) {
        this.panelService = panelService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String verPanel(Model model) {
        PanelResponseDTO panel = panelService.obtenerMetricasPanel();
        model.addAttribute("panel", panel);
        model.addAttribute("tabActiva", "metricas");
        return "panel";
    }

    @GetMapping("/usuarios")
    public String verAdministracionUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuarios());
        model.addAttribute("sucursales", usuarioService.listarSucursales());
        model.addAttribute("roles", Rol.values());
        model.addAttribute("tabActiva", "usuarios");
        return "panel-usuarios";
    }

    @PostMapping("/usuarios/crear")
    public String crearUsuario(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String nombre,
            @RequestParam Rol rol,
            @RequestParam(required = false) Long sucursalId,
            RedirectAttributes redirectAttributes) {
        try {
            CrearUsuarioRequestDTO dto = CrearUsuarioRequestDTO.builder()
                    .username(username)
                    .password(password)
                    .nombre(nombre)
                    .rol(rol)
                    .sucursalId(sucursalId)
                    .build();
            usuarioService.crearUsuario(dto);
            redirectAttributes.addFlashAttribute("mensajeExito", "Usuario '" + username + "' creado exitosamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/panel/usuarios";
    }

    @PostMapping("/usuarios/{id}/toggle-activo")
    public String toggleActivo(
            @PathVariable Long id,
            @RequestParam boolean activo,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            String usernameEjecutor = principal != null ? principal.getUsername() : null;
            usuarioService.cambiarEstadoActivo(id, activo, usernameEjecutor);
            redirectAttributes.addFlashAttribute("mensajeExito", "Estado del usuario actualizado exitosamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/panel/usuarios";
    }

    @PostMapping("/usuarios/{id}/password")
    public String resetPassword(
            @PathVariable Long id,
            @RequestParam String nuevoPassword,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.resetPassword(id, nuevoPassword);
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña restablecida exitosamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/panel/usuarios";
    }
}
