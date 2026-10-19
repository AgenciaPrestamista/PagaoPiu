package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.service.ReniecClient;
import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import com.example.pagaopiu.service.SunatClient;
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
    private final SunatClient sunatClient;
    private final ClienteRepository clienteRepository;

    public ClienteRestController(ReniecClient reniecClient, SunatClient sunatClient, ClienteRepository clienteRepository) {
        this.reniecClient = reniecClient;
        this.sunatClient = sunatClient;
        this.clienteRepository = clienteRepository;
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(@RequestParam("dni") String dni) {
        if (dni == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El documento es obligatorio."));
        }

        String dniLimpio = dni.trim();

        if (dniLimpio.matches("\\d{11}")) {
            Optional<Cliente> clienteOpt = clienteRepository.findByDni(dniLimpio);
            Optional<SunatRucDTO> rucOpt = sunatClient.buscarPorRuc(dniLimpio);

            String razonSocial = rucOpt.map(SunatRucDTO::getRazonSocial)
                    .orElseGet(() -> clienteOpt.map(Cliente::getNombreCompleto).orElse(obtenerNombreEmpresaDemo(dniLimpio)));
            String direccion = rucOpt.map(SunatRucDTO::getDireccion).orElse("Dirección fiscal registrada en SUNAT");
            String repNombre = rucOpt.map(SunatRucDTO::getRepresentanteNombre).orElse("Representante Legal");
            String repDoc = rucOpt.map(SunatRucDTO::getRepresentanteDocumento).orElse("-");
            String repCargo = rucOpt.map(SunatRucDTO::getRepresentanteCargo).orElse("GERENTE GENERAL");
            String estadoSunat = rucOpt.map(SunatRucDTO::getEstado).orElse("ACTIVO");
            String condicionSunat = rucOpt.map(SunatRucDTO::getCondicion).orElse("HABIDO");

            boolean elegible = true;
            String motivoRechazo = null;
            if (clienteOpt.isPresent() && clienteOpt.get().getEstado() != EstadoCliente.ACTIVO) {
                elegible = false;
                motivoRechazo = "La empresa se encuentra en estado " + clienteOpt.get().getEstado() + " en la financiera.";
            } else if (rucOpt.isPresent() && (!rucOpt.get().isActivo() || !rucOpt.get().isHabido())) {
                if (!rucOpt.get().isHabido()) {
                    elegible = false;
                    motivoRechazo = "La empresa figura con condición " + condicionSunat + " en SUNAT (debe ser HABIDO).";
                } else if (!rucOpt.get().isActivo()) {
                    elegible = false;
                    motivoRechazo = "La empresa figura en estado " + estadoSunat + " en SUNAT (debe estar ACTIVO).";
                }
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("dni", dniLimpio);
            respuesta.put("nombreCompleto", razonSocial);
            respuesta.put("razonSocial", razonSocial);
            respuesta.put("direccion", direccion);
            respuesta.put("estadoSunat", estadoSunat);
            respuesta.put("condicionSunat", condicionSunat);
            respuesta.put("representanteNombre", repNombre);
            respuesta.put("representanteDocumento", repDoc);
            respuesta.put("representanteCargo", repCargo);
            respuesta.put("nacionalidad", "PERUANA");
            respuesta.put("dniVigente", true);
            respuesta.put("fallecido", false);
            respuesta.put("clienteEstado", clienteOpt.map(c -> c.getEstado().name()).orElse(null));
            respuesta.put("elegible", elegible);
            respuesta.put("motivoRechazo", motivoRechazo);
            return ResponseEntity.ok(respuesta);
        }

        if (!dniLimpio.matches("\\d{8}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "El documento debe ser un DNI de 8 dígitos o un RUC de 11 dígitos."));
        }
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

    private String obtenerNombreEmpresaDemo(String ruc) {
        if ("20987654321".equals(ruc)) {
            return "COMERCIAL LOS ANDES S.A.C.";
        } else if ("20123456789".equals(ruc)) {
            return "PRÉSTAMOS DEL NORTE S.A.C.";
        } else if ("20555666771".equals(ruc)) {
            return "DISTRIBUIDORA NORTEÑA S.R.L.";
        } else if ("20444333221".equals(ruc)) {
            return "SERVICIOS LOGÍSTICOS INTEGRALES S.A.C.";
        }
        return "EMPRESA RUC " + ruc + " S.A.C.";
    }
}
