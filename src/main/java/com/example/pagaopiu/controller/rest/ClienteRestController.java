package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.service.ReniecClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cliente")
public class ClienteRestController {

    private final ReniecClient reniecClient;
    private final ClienteRepository clienteRepository;

    public ClienteRestController(ReniecClient reniecClient, ClienteRepository clienteRepository) {
        this.reniecClient = reniecClient;
        this.clienteRepository = clienteRepository;
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(@RequestParam("dni") String dni) {
        if (dni == null || !dni.trim().matches("\\d{8}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "El DNI debe tener exactamente 8 dígitos numéricos."));
        }

        String dniLimpio = dni.trim();
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni(dniLimpio);
        if (padronOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No se encontró registro para el DNI en el padrón de RENIEC."));
        }

        ReniecPadron padron = padronOpt.get();
        Optional<Cliente> clienteOpt = clienteRepository.findByDni(dniLimpio);

        boolean elegible = true;
        String motivoRechazo = null;

        if (padron.isFallecido()) {
            elegible = false;
            motivoRechazo = "Persona registrada como FALLECIDA en RENIEC.";
        } else if (!padron.isDniVigente()) {
            elegible = false;
            motivoRechazo = "El DNI de la persona NO se encuentra vigente.";
        } else if (!"PERUANA".equalsIgnoreCase(padron.getNacionalidad())) {
            elegible = false;
            motivoRechazo = "La persona no posee nacionalidad PERUANA (" + padron.getNacionalidad() + ").";
        } else if (clienteOpt.isPresent() && clienteOpt.get().getEstado() != EstadoCliente.ACTIVO) {
            elegible = false;
            motivoRechazo = "El cliente se encuentra en estado " + clienteOpt.get().getEstado() + " en la empresa.";
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dni", padron.getDni());
        respuesta.put("nombreCompleto", padron.getNombreCompleto());
        respuesta.put("nacionalidad", padron.getNacionalidad());
        respuesta.put("dniVigente", padron.isDniVigente());
        respuesta.put("fallecido", padron.isFallecido());
        respuesta.put("clienteEstado", clienteOpt.map(c -> c.getEstado().name()).orElse(null));
        respuesta.put("elegible", elegible);
        respuesta.put("motivoRechazo", motivoRechazo);

        return ResponseEntity.ok(respuesta);
    }
}
