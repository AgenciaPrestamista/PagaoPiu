package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.caja.CajaEstadoResponseDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesRequestDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesResponseDTO;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.CajaHermesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/caja")
public class CajaRestController {

    private final CajaHermesService cajaHermesService;

    public CajaRestController(CajaHermesService cajaHermesService) {
        this.cajaHermesService = cajaHermesService;
    }

    @PostMapping("/hermes/recepcion")
    public ResponseEntity<RecepcionHermesResponseDTO> registrarRecepcionHermes(
            @Valid @RequestBody RecepcionHermesRequestDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RecepcionHermesResponseDTO resp = cajaHermesService.registrarRecepcionHermes(dto, principal.getUsername());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/estado")
    public ResponseEntity<CajaEstadoResponseDTO> obtenerEstadoCaja(
            @RequestParam(value = "sucursalId", required = false) Long sucursalId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        CajaEstadoResponseDTO resp = cajaHermesService.obtenerEstadoCaja(sucursalId, principal.getUsername());
        return ResponseEntity.ok(resp);
    }
}
