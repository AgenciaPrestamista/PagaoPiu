package com.example.pagaopiu.controller;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.service.ReniecClient;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/cliente")
public class ClienteController {

    private final ReniecClient reniecClient;
    private final ClienteRepository clienteRepository;

    public ClienteController(ReniecClient reniecClient, ClienteRepository clienteRepository) {
        this.reniecClient = reniecClient;
        this.clienteRepository = clienteRepository;
    }

    @GetMapping("/buscar")
    public String buscarCliente(@RequestParam(value = "dni", required = false) String dni, Model model) {
        if (dni == null || dni.isBlank()) {
            return "cliente-buscar";
        }

        String dniLimpio = dni.trim();
        model.addAttribute("dniBuscado", dniLimpio);

        if (!dniLimpio.matches("\\d{8}")) {
            model.addAttribute("error", "El DNI debe tener exactamente 8 dígitos numéricos.");
            return "cliente-buscar";
        }

        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni(dniLimpio);
        if (padronOpt.isEmpty()) {
            model.addAttribute("error", "No se encontró registro para el DNI en el padrón de RENIEC.");
            return "cliente-buscar";
        }

        ReniecPadron padron = padronOpt.get();
        model.addAttribute("padron", padron);

        Optional<Cliente> clienteOpt = clienteRepository.findByDni(dniLimpio);
        clienteOpt.ifPresent(cliente -> model.addAttribute("cliente", cliente));

        // Validaciones de elegibilidad según R2 y R7
        if (padron.isFallecido()) {
            model.addAttribute("motivoRechazo", "Persona registrada como FALLECIDA en RENIEC.");
            model.addAttribute("elegible", false);
        } else if (!padron.isDniVigente()) {
            model.addAttribute("motivoRechazo", "El DNI de la persona NO se encuentra vigente.");
            model.addAttribute("elegible", false);
        } else if (!"PERUANA".equalsIgnoreCase(padron.getNacionalidad())) {
            model.addAttribute("motivoRechazo", "La persona no posee nacionalidad PERUANA (" + padron.getNacionalidad() + ").");
            model.addAttribute("elegible", false);
        } else if (clienteOpt.isPresent() && clienteOpt.get().getEstado() != EstadoCliente.ACTIVO) {
            model.addAttribute("motivoRechazo", "El cliente se encuentra en estado " + clienteOpt.get().getEstado() + " en la empresa.");
            model.addAttribute("elegible", false);
        } else {
            model.addAttribute("elegible", true);
        }

        return "cliente-buscar";
    }
}
