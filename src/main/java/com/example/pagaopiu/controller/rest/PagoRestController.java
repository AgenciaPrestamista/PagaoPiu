package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.PagoResponseDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Pago;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.PagoService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pago")
public class PagoRestController {

    private final PagoService pagoService;
    private final CuotaRepository cuotaRepository;

    public PagoRestController(PagoService pagoService, CuotaRepository cuotaRepository) {
        this.pagoService = pagoService;
        this.cuotaRepository = cuotaRepository;
    }

    @PostMapping("/cuota/{prestamoId}")
    public ResponseEntity<PagoResponseDTO> pagarCuota(
            @PathVariable("prestamoId") Long prestamoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Pago pago = pagoService.pagarSiguienteCuota(prestamoId, principal.getUsername());
        return ResponseEntity.ok(mapToDTO(pago));
    }

    @PostMapping("/cancelar/{prestamoId}")
    public ResponseEntity<PagoResponseDTO> cancelarAnticipado(
            @PathVariable("prestamoId") Long prestamoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Pago pago = pagoService.cancelarAnticipado(prestamoId, principal.getUsername());
        return ResponseEntity.ok(mapToDTO(pago));
    }

    @GetMapping("/prestamo/{prestamoId}")
    public ResponseEntity<List<PagoResponseDTO>> listarPagos(@PathVariable("prestamoId") Long prestamoId) {
        List<Pago> pagos = pagoService.obtenerPagosPorPrestamoId(prestamoId);
        List<PagoResponseDTO> dtos = pagos.stream().map(this::mapToDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoResponseDTO> obtenerPago(@PathVariable("id") Long id) {
        Pago pago = pagoService.obtenerPagoPorId(id);
        return ResponseEntity.ok(mapToDTO(pago));
    }

    private PagoResponseDTO mapToDTO(Pago pago) {
        String clienteNombre = null;
        String clienteDni = null;
        if (pago.getPrestamo() != null && pago.getPrestamo().getCliente() != null) {
            clienteNombre = pago.getPrestamo().getCliente().getNombreCompleto();
            clienteDni = pago.getPrestamo().getCliente().getDni();
        }

        Integer cuotaNumero = null;
        BigDecimal montoCapital = BigDecimal.ZERO;
        BigDecimal montoInteres = BigDecimal.ZERO;
        BigDecimal saldoRestante = BigDecimal.ZERO;

        if (cuotaRepository != null && pago.getId() != null) {
            List<Cuota> cuotas = cuotaRepository.findByPagoId(pago.getId());
            if (!cuotas.isEmpty()) {
                cuotaNumero = cuotas.get(0).getNumero();
                for (Cuota c : cuotas) {
                    if (c.getCapital() != null) montoCapital = montoCapital.add(c.getCapital());
                    if (c.getInteres() != null) montoInteres = montoInteres.add(c.getInteres());
                }
                saldoRestante = cuotas.get(cuotas.size() - 1).getSaldoCapital();
            }
        }

        return PagoResponseDTO.builder()
                .id(pago.getId())
                .prestamoId(pago.getPrestamo() != null ? pago.getPrestamo().getId() : null)
                .sucursalNombre(pago.getSucursal() != null ? pago.getSucursal().getNombre() : null)
                .asesorNombre(pago.getUsuario() != null ? pago.getUsuario().getNombre() : null)
                .fechaPago(pago.getFechaPago())
                .monto(pago.getMonto())
                .tipo(pago.getTipo() != null ? pago.getTipo().name() : null)
                .numeroComprobante(pago.getNumeroComprobante())
                .tipoComprobante(pago.getTipoComprobante() != null ? pago.getTipoComprobante() : "BOLETA")
                .clienteNombre(clienteNombre)
                .clienteDocumento(clienteDni)
                .cuotaNumero(cuotaNumero)
                .montoCapital(montoCapital)
                .montoInteres(montoInteres)
                .saldoRestante(saldoRestante)
                .build();
    }
}
