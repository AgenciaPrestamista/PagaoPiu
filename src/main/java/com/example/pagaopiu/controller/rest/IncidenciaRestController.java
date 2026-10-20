package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.caja.CerrarIncidenciaRequestDTO;
import com.example.pagaopiu.dto.caja.IncidenciaHermesDTO;
import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.CajaHermesService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaRestController {

    private final CajaHermesService cajaHermesService;

    public IncidenciaRestController(CajaHermesService cajaHermesService) {
        this.cajaHermesService = cajaHermesService;
    }

    @GetMapping
    public ResponseEntity<List<IncidenciaHermesDTO>> listarIncidencias(
            @RequestParam(value = "sucursalId", required = false) Long sucursalId,
            @RequestParam(value = "estado", required = false) EstadoIncidenciaHermes estado,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<IncidenciaHermesDTO> list = cajaHermesService.listarIncidencias(estado, sucursalId, principal.getUsername());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<IncidenciaHermesDTO> cerrarIncidencia(
            @PathVariable("id") Long id,
            @Valid @RequestBody CerrarIncidenciaRequestDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        IncidenciaHermesDTO res = cajaHermesService.cerrarIncidencia(id, dto.getResolucion(), principal.getUsername());
        return ResponseEntity.ok(res);
    }
}
