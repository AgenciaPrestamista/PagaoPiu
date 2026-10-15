package com.example.pagaopiu.controller;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.PrestamoService;
import com.example.pagaopiu.service.ReniecClient;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/prestamo")
public class PrestamoController {

    private final PrestamoService prestamoService;
    private final ReniecClient reniecClient;
    private final Clock clock;

    public PrestamoController(PrestamoService prestamoService, ReniecClient reniecClient, Clock clock) {
        this.prestamoService = prestamoService;
        this.reniecClient = reniecClient;
        this.clock = clock;
    }

    @GetMapping("/registrar")
    public String mostrarFormularioRegistro(
            @RequestParam(value = "dni", required = false) String dni,
            Model model) {
        PrestamoRegistroDTO dto = new PrestamoRegistroDTO();
        dto.setFechaPrestamo(LocalDate.now(clock));

        if (dni != null && !dni.isBlank()) {
            String dniLimpio = dni.trim();
            dto.setDni(dniLimpio);
            Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni(dniLimpio);
            padronOpt.ifPresent(p -> dto.setNombreCliente(p.getNombreCompleto()));
        }

        model.addAttribute("prestamo", dto);
        return "prestamo-registrar";
    }

    @PostMapping("/registrar")
    public String procesarRegistroPrestamo(
            @ModelAttribute("prestamo") PrestamoRegistroDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Prestamo prestamo = prestamoService.registrarPrestamo(dto, principal.getUsername());
        return "redirect:/prestamo/" + prestamo.getId();
    }

    @GetMapping("/{id}")
    public String verDetallePrestamo(@PathVariable("id") Long id, Model model) {
        Prestamo prestamo = prestamoService.obtenerPrestamoPorId(id);
        List<Cuota> cuotas = prestamoService.obtenerCuotasPorPrestamoId(id);

        model.addAttribute("prestamo", prestamo);
        model.addAttribute("cuotas", cuotas);
        return "prestamo-detalle";
    }
}
