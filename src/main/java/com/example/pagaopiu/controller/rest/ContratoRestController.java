package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.CancelarContratoRequestDTO;
import com.example.pagaopiu.dto.ContratoDetalleResponseDTO;
import com.example.pagaopiu.dto.ContratoResponseDTO;
import com.example.pagaopiu.dto.CuotaResponseDTO;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.security.UsuarioPrincipal;
import com.example.pagaopiu.service.ContratoDocumentoService;
import com.example.pagaopiu.service.PrestamoService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contratos")
public class ContratoRestController {

    private final PrestamoService prestamoService;
    private final ContratoDocumentoService contratoDocumentoService;

    public ContratoRestController(PrestamoService prestamoService, ContratoDocumentoService contratoDocumentoService) {
        this.prestamoService = prestamoService;
        this.contratoDocumentoService = contratoDocumentoService;
    }

    @PostMapping("/generar")
    public ResponseEntity<ContratoResponseDTO> generarContrato(
            @RequestBody PrestamoRegistroDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Prestamo prestamo = prestamoService.generarContrato(dto, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponseDTO(prestamo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContratoDetalleResponseDTO> obtenerContratoPorId(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Prestamo prestamo = prestamoService.obtenerContratoPorId(id, principal.getUsername());
        List<Cuota> cuotas = prestamoService.obtenerCuotasPorPrestamoId(id);
        return ResponseEntity.ok(mapToDetalleDTO(prestamo, cuotas));
    }

    @PostMapping("/{id}/imprimir")
    public ResponseEntity<ContratoResponseDTO> marcarImpreso(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Prestamo prestamo = prestamoService.marcarImpreso(id, principal.getUsername());
        return ResponseEntity.ok(mapToResponseDTO(prestamo));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarContrato(
            @PathVariable("id") Long id,
            @RequestBody(required = false) CancelarContratoRequestDTO body,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        String motivo = (body != null) ? body.getMotivo() : null;
        Prestamo prestamo = prestamoService.cancelarPrestamo(id, motivo, principal.getUsername());
        if (prestamo == null) {
            return ResponseEntity.ok(Map.of("mensaje", "Borrador de contrato descartado correctamente."));
        }
        return ResponseEntity.ok(mapToResponseDTO(prestamo));
    }

    @PostMapping(value = "/{id}/subir-firmado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContratoResponseDTO> subirContratoFirmado(
            @PathVariable("id") Long id,
            @RequestParam("numeroContrato") String numeroContrato,
            @RequestParam("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Prestamo prestamo = prestamoService.subirContratoFirmado(id, numeroContrato, archivo, principal.getUsername());
        return ResponseEntity.ok(mapToResponseDTO(prestamo));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargarContratoFirmado(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Resource recurso = prestamoService.descargarContratoFirmado(id, principal.getUsername());
        Prestamo prestamo = prestamoService.obtenerContratoPorId(id, principal.getUsername());

        String nombreOriginal = prestamo.getContratoNombreOriginal();
        String nombreDescarga = (nombreOriginal != null && !nombreOriginal.isBlank())
                ? nombreOriginal
                : "contrato_firmado.pdf";

        String contentType = (prestamo.getContratoContentType() != null && !prestamo.getContratoContentType().isBlank())
                ? prestamo.getContratoContentType()
                : "application/octet-stream";

        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(nombreDescarga, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(contentType))
                .body(recurso);
    }

    @GetMapping("/firma-gerente")
    public ResponseEntity<Resource> obtenerFirmaGerente() {
        if (!contratoDocumentoService.tieneFirmaGerente()) {
            return ResponseEntity.notFound().build();
        }
        Resource recurso = contratoDocumentoService.obtenerRecursoFirmaGerente();
        String contentType = contratoDocumentoService.obtenerContentTypeFirma();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(recurso);
    }

    @GetMapping("/en-espera")
    public ResponseEntity<List<ContratoResponseDTO>> listarContratosEnEspera(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<Prestamo> prestamos = prestamoService.listarContratosEnEspera(principal.getUsername());
        List<ContratoResponseDTO> dtos = prestamos.stream()
                .map(this::mapToResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private ContratoResponseDTO mapToResponseDTO(Prestamo prestamo) {
        return ContratoResponseDTO.builder()
                .id(prestamo.getId())
                .numeroContrato(prestamo.getNumeroContrato())
                .clienteDni(prestamo.getCliente() != null ? prestamo.getCliente().getDni() : null)
                .clienteNombre(prestamo.getCliente() != null ? prestamo.getCliente().getNombreCompleto() : null)
                .sucursalId(prestamo.getSucursal() != null ? prestamo.getSucursal().getId() : null)
                .sucursalNombre(prestamo.getSucursal() != null ? prestamo.getSucursal().getNombre() : null)
                .asesorNombre(prestamo.getAsesor() != null ? prestamo.getAsesor().getNombre() : null)
                .capital(prestamo.getCapital())
                .plazoMeses(prestamo.getPlazoMeses())
                .tea(prestamo.getTea())
                .cuotaFija(prestamo.getCuotaFija())
                .fechaPrestamo(prestamo.getFechaPrestamo())
                .estado(prestamo.getEstado() != null ? prestamo.getEstado().name() : null)
                .impreso(prestamo.getImpreso())
                .fechaImpresion(prestamo.getFechaImpresion())
                .motivoAnulacion(prestamo.getMotivoAnulacion())
                .tieneContratoFirmado(prestamo.getContratoRuta() != null && !prestamo.getContratoRuta().isBlank())
                .contratoNombreOriginal(prestamo.getContratoNombreOriginal())
                .fechaSubidaContrato(prestamo.getContratoFechaSubida())
                .build();
    }

    private ContratoDetalleResponseDTO mapToDetalleDTO(Prestamo prestamo, List<Cuota> cuotas) {
        List<CuotaResponseDTO> cuotasDTO = cuotas.stream()
                .map(c -> {
                    BigDecimal saldoInicial = (c.getSaldoCapital() != null && c.getCapital() != null)
                            ? c.getSaldoCapital().add(c.getCapital())
                            : null;
                    return CuotaResponseDTO.builder()
                            .id(c.getId())
                            .numero(c.getNumero())
                            .fechaVencimiento(c.getFechaVencimiento())
                            .capital(c.getCapital())
                            .interes(c.getInteres())
                            .monto(c.getMonto())
                            .saldoCapital(c.getSaldoCapital())
                            .saldoInicial(saldoInicial)
                            .estado(c.getEstado().name())
                            .pagoId(c.getPago() != null ? c.getPago().getId() : null)
                            .build();
                })
                .toList();

        ContratoDocumentoService.ContratoTextoDTO textoDTO = contratoDocumentoService.generarTextoContrato(prestamo);
        BigDecimal temPorcentajeCuatroDecimales = prestamo.getTem() != null
                ? prestamo.getTem().multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP)
                : null;

        return ContratoDetalleResponseDTO.builder()
                .id(prestamo.getId())
                .numeroContrato(prestamo.getNumeroContrato())
                .clienteDni(prestamo.getCliente() != null ? prestamo.getCliente().getDni() : null)
                .clienteNombre(prestamo.getCliente() != null ? prestamo.getCliente().getNombreCompleto() : null)
                .clienteEstado(prestamo.getCliente() != null && prestamo.getCliente().getEstado() != null
                        ? prestamo.getCliente().getEstado().name() : null)
                .sucursalId(prestamo.getSucursal() != null ? prestamo.getSucursal().getId() : null)
                .sucursalNombre(prestamo.getSucursal() != null ? prestamo.getSucursal().getNombre() : null)
                .asesorNombre(prestamo.getAsesor() != null ? prestamo.getAsesor().getNombre() : null)
                .capital(prestamo.getCapital())
                .plazoMeses(prestamo.getPlazoMeses())
                .tea(prestamo.getTea())
                .tem(prestamo.getTem())
                .cuotaFija(prestamo.getCuotaFija())
                .fechaPrestamo(prestamo.getFechaPrestamo())
                .estado(prestamo.getEstado() != null ? prestamo.getEstado().name() : null)
                .impreso(prestamo.getImpreso())
                .fechaImpresion(prestamo.getFechaImpresion())
                .motivoAnulacion(prestamo.getMotivoAnulacion())
                .tieneContratoFirmado(prestamo.getContratoRuta() != null && !prestamo.getContratoRuta().isBlank())
                .contratoNombreOriginal(prestamo.getContratoNombreOriginal())
                .fechaSubidaContrato(prestamo.getContratoFechaSubida())
                .cuotas(cuotasDTO)
                .textoContrato(textoDTO.textoContrato())
                .bloqueFirmas(textoDTO.bloqueFirmas())
                .tieneFirmaGerente(contratoDocumentoService.tieneFirmaGerente())
                .temPorcentajeCuatroDecimales(temPorcentajeCuatroDecimales)
                .ciudadContrato(textoDTO.ciudad())
                .fechaEmisionFormateada(textoDTO.fechaEmisionFormateada())
                .emisorRazonSocial(textoDTO.emisorRazonSocial())
                .empresaRazonSocial(prestamo.getEmpresaRazonSocial())
                .empresaRuc(prestamo.getEmpresaRuc())
                .empresaRepresentanteNombre(prestamo.getEmpresaRepresentanteNombre())
                .empresaRepresentanteDocumento(prestamo.getEmpresaRepresentanteDocumento())
                .empresaRepresentanteCargo(prestamo.getEmpresaRepresentanteCargo())
                .build();
    }
}
