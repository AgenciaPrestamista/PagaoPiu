package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.caja.CajaEstadoResponseDTO;
import com.example.pagaopiu.dto.caja.IncidenciaHermesDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesRequestDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesResponseDTO;
import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
import java.math.BigDecimal;
import java.util.List;

public interface CajaHermesService {

    RecepcionHermesResponseDTO recibirEntregaHermes(RecepcionHermesRequestDTO dto, String usernameAsesor);

    RecepcionHermesResponseDTO registrarRecepcionHermes(RecepcionHermesRequestDTO dto, String usernameAsesor);

    CajaEstadoResponseDTO obtenerEstadoCaja(String username);

    CajaEstadoResponseDTO obtenerEstadoCaja(Long sucursalId, String username);

    List<CajaEstadoResponseDTO> listarCierres(java.time.LocalDate desde, java.time.LocalDate hasta, Long sucursalId, String username);

    CajaEstadoResponseDTO cerrarCaja(Long sucursalId, String observacion, String username);

    List<IncidenciaHermesDTO> listarIncidencias(EstadoIncidenciaHermes estado, Long sucursalId, String username);

    IncidenciaHermesDTO cerrarIncidencia(Long incidenciaId, String resolucion, String usernameGerente);

    void verificarYDescontarSaldoCaja(Long sucursalId, BigDecimal monto);
}
