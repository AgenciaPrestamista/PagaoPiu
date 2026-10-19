package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.CuotaDetalle;
import com.example.pagaopiu.dto.CuotaResponseDTO;
import com.example.pagaopiu.dto.PrestamoResponseDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.service.CronogramaCalculator;
import com.example.pagaopiu.service.PrestamoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prestamo")
public class PrestamoRestController {

    private final PrestamoService prestamoService;
    private final Clock clock;
    private final CronogramaCalculator cronogramaCalculator = new CronogramaCalculator();

    public PrestamoRestController(PrestamoService prestamoService, Clock clock) {
        this.prestamoService = prestamoService;
        this.clock = clock;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponseDTO> obtenerPorId(@PathVariable("id") Long id) {
        Prestamo prestamo = prestamoService.obtenerPrestamoPorId(id);
        List<Cuota> cuotas = prestamoService.obtenerCuotasPorPrestamoId(id);
        return ResponseEntity.ok(mapToDTO(prestamo, cuotas));
    }

    @GetMapping("/cliente/{dni}")
    public ResponseEntity<List<PrestamoResponseDTO>> listarPorClienteDni(@PathVariable("dni") String dni) {
        List<Prestamo> prestamos = prestamoService.obtenerPrestamosPorClienteDni(dni);
        List<PrestamoResponseDTO> dtos = prestamos.stream()
                .map(p -> mapToDTO(p, prestamoService.obtenerCuotasPorPrestamoId(p.getId())))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/recientes")
    public ResponseEntity<List<PrestamoResponseDTO>> listarRecientes() {
        List<Prestamo> prestamos = prestamoService.listarPrestamosRecientes();
        List<PrestamoResponseDTO> dtos = prestamos.stream()
                .map(p -> mapToDTO(p, prestamoService.obtenerCuotasPorPrestamoId(p.getId())))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/simular")
    public ResponseEntity<List<CuotaDetalle>> simular(
            @RequestParam("capital") BigDecimal capital,
            @RequestParam("plazoMeses") int plazoMeses,
            @RequestParam(value = "fechaPrestamo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPrestamo) {
        if (fechaPrestamo == null) {
            fechaPrestamo = LocalDate.now(clock);
        }
        List<CuotaDetalle> cronograma = cronogramaCalculator.generarCronograma(
                capital, plazoMeses, fechaPrestamo, CronogramaCalculator.TEA_DEFECTO);
        return ResponseEntity.ok(cronograma);
    }

    private PrestamoResponseDTO mapToDTO(Prestamo prestamo, List<Cuota> cuotas) {
        List<CuotaResponseDTO> cuotasDTO = cuotas.stream()
                .map(c -> CuotaResponseDTO.builder()
                        .id(c.getId())
                        .numero(c.getNumero())
                        .fechaVencimiento(c.getFechaVencimiento())
                        .capital(c.getCapital())
                        .interes(c.getInteres())
                        .monto(c.getMonto())
                        .saldoCapital(c.getSaldoCapital())
                        .estado(c.getEstado().name())
                        .pagoId(c.getPago() != null ? c.getPago().getId() : null)
                        .build())
                .toList();

        return PrestamoResponseDTO.builder()
                .id(prestamo.getId())
                .clienteDni(prestamo.getCliente().getDni())
                .clienteNombre(prestamo.getCliente().getNombreCompleto())
                .clienteEstado(prestamo.getCliente().getEstado().name())
                .sucursalId(prestamo.getSucursal().getId())
                .sucursalNombre(prestamo.getSucursal().getNombre())
                .asesorNombre(prestamo.getAsesor().getNombre())
                .fechaPrestamo(prestamo.getFechaPrestamo())
                .capital(prestamo.getCapital())
                .plazoMeses(prestamo.getPlazoMeses())
                .tea(prestamo.getTea())
                .tem(prestamo.getTem())
                .cuotaFija(prestamo.getCuotaFija())
                .estado(prestamo.getEstado().name())
                .fechaCancelacion(prestamo.getFechaCancelacion())
                .numeroContrato(prestamo.getNumeroContrato())
                .impreso(prestamo.getImpreso())
                .tieneContratoFirmado(prestamo.getContratoRuta() != null && !prestamo.getContratoRuta().isBlank())
                .contratoNombreOriginal(prestamo.getContratoNombreOriginal())
                .cuotas(cuotasDTO)
                .build();
    }
}
