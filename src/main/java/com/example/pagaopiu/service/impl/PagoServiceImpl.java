package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Pago;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.model.enums.TipoPago;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.service.EstadoService;
import com.example.pagaopiu.service.PagoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PagoServiceImpl implements PagoService {

    private final PrestamoRepository prestamoRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    private final EstadoService estadoService;

    @org.springframework.beans.factory.annotation.Autowired
    public PagoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            PagoRepository pagoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock,
            EstadoService estadoService) {
        this.prestamoRepository = prestamoRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
        this.estadoService = estadoService;
    }

    public PagoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            PagoRepository pagoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock) {
        this(prestamoRepository, cuotaRepository, pagoRepository, usuarioRepository, clock, null);
    }

    @Override
    @Transactional
    public Pago pagarSiguienteCuota(Long prestamoId, String usernameAsesor) {
        // Bloqueo pesimista sobre el préstamo
        Prestamo prestamo = prestamoRepository.findByIdWithLock(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getEstado() == EstadoPrestamo.CANCELADO) {
            throw new NegocioException("El préstamo ya se encuentra cancelado.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.EN_ESPERA) {
            throw new NegocioException("El préstamo se encuentra en espera y aún no ha sido activado con su contrato firmado.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.ANULADO) {
            throw new NegocioException("El préstamo se encuentra anulado y no admite pagos.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.EXTRAJUDICIAL) {
            throw new NegocioException("El préstamo se encuentra en estado EXTRAJUDICIAL y no admite pagos.");
        }

        // R10: Buscar cuota PENDIENTE más antigua (menor número)
        Optional<Cuota> cuotaOpt = cuotaRepository.findFirstByPrestamoIdAndEstadoOrderByNumeroAsc(prestamoId, EstadoCuota.PENDIENTE);
        if (cuotaOpt.isEmpty()) {
            throw new NegocioException("El préstamo no tiene cuotas pendientes de pago.");
        }
        Cuota cuota = cuotaOpt.get();

        // R11: Sucursal y asesor del cobro tomados del usuario autenticado
        Usuario asesor = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario asesor no encontrado: " + usernameAsesor));

        Sucursal sucursalCobro = asesor.getSucursal();
        if (sucursalCobro == null) {
            throw new NegocioException("El asesor autenticado no tiene una sucursal asignada.");
        }

        LocalDate fechaPago = LocalDate.now(clock);

        // Crear registro de Pago
        Pago.PagoBuilder pagoBuilder = Pago.builder()
                .prestamo(prestamo)
                .sucursal(sucursalCobro)
                .usuario(asesor)
                .fechaPago(fechaPago)
                .monto(cuota.getMonto())
                .tipo(TipoPago.CUOTA);
        generarComprobante(prestamo, pagoBuilder);
        Pago pago = pagoRepository.save(pagoBuilder.build());

        // Actualizar cuota a PAGADA y asociarla al pago
        cuota.setEstado(EstadoCuota.PAGADA);
        cuota.setPago(pago);
        cuotaRepository.save(cuota);

        // Si ya no quedan cuotas pendientes, el préstamo pasa a CANCELADO
        List<Cuota> restantes = cuotaRepository.findByPrestamoIdAndEstadoOrderByNumeroAsc(prestamoId, EstadoCuota.PENDIENTE);
        if (restantes.isEmpty()) {
            prestamo.setEstado(EstadoPrestamo.CANCELADO);
            prestamo.setFechaCancelacion(fechaPago);
            prestamoRepository.save(prestamo);
        } else if (estadoService != null) {
            estadoService.recalcularPrestamo(prestamo);
        }

        if (estadoService != null) {
            estadoService.recalcularCliente(prestamo.getCliente());
        }

        return pagoRepository.findById(pago.getId()).orElse(pago);
    }

    @Override
    @Transactional
    public Pago cancelarAnticipado(Long prestamoId, String usernameAsesor) {
        // Bloqueo pesimista sobre el préstamo
        Prestamo prestamo = prestamoRepository.findByIdWithLock(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getEstado() == EstadoPrestamo.CANCELADO) {
            throw new NegocioException("El préstamo ya se encuentra cancelado.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.EN_ESPERA) {
            throw new NegocioException("El préstamo se encuentra en espera y aún no ha sido activado con su contrato firmado.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.ANULADO) {
            throw new NegocioException("El préstamo se encuentra anulado y no admite pagos.");
        }
        if (prestamo.getEstado() == EstadoPrestamo.EXTRAJUDICIAL) {
            throw new NegocioException("El préstamo se encuentra en estado EXTRAJUDICIAL y no admite pagos.");
        }

        // R12: Buscar todas las cuotas pendientes
        List<Cuota> cuotasPendientes = cuotaRepository.findByPrestamoIdAndEstadoOrderByNumeroAsc(prestamoId, EstadoCuota.PENDIENTE);
        if (cuotasPendientes.isEmpty()) {
            throw new NegocioException("El préstamo no tiene cuotas pendientes para cancelar.");
        }

        // Sumar montos de todas las cuotas pendientes
        BigDecimal totalCancelar = cuotasPendientes.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // R11: Sucursal y asesor del cobro tomados del usuario autenticado
        Usuario asesor = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario asesor no encontrado: " + usernameAsesor));

        Sucursal sucursalCobro = asesor.getSucursal();
        if (sucursalCobro == null) {
            throw new NegocioException("El asesor autenticado no tiene una sucursal asignada.");
        }

        LocalDate fechaPago = LocalDate.now(clock);

        // Crear pago único tipo CANCELACION_ANTICIPADA
        Pago.PagoBuilder pagoBuilder = Pago.builder()
                .prestamo(prestamo)
                .sucursal(sucursalCobro)
                .usuario(asesor)
                .fechaPago(fechaPago)
                .monto(totalCancelar)
                .tipo(TipoPago.CANCELACION_ANTICIPADA);
        generarComprobante(prestamo, pagoBuilder);
        Pago pago = pagoRepository.save(pagoBuilder.build());

        // Marcar todas las cuotas pendientes como PAGADA
        for (Cuota c : cuotasPendientes) {
            c.setEstado(EstadoCuota.PAGADA);
            c.setPago(pago);
        }
        cuotaRepository.saveAll(cuotasPendientes);

        // Préstamo pasa a CANCELADO con su fecha de cancelación
        prestamo.setEstado(EstadoPrestamo.CANCELADO);
        prestamo.setFechaCancelacion(fechaPago);
        prestamoRepository.save(prestamo);

        if (estadoService != null) {
            estadoService.recalcularCliente(prestamo.getCliente());
        }

        return pagoRepository.findById(pago.getId()).orElse(pago);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> obtenerPagosPorPrestamoId(Long prestamoId) {
        return pagoRepository.findByPrestamoId(prestamoId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pago obtenerPagoPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Pago no encontrado con id: " + id));
    }

    private void generarComprobante(Prestamo prestamo, Pago.PagoBuilder builder) {
        String dni = (prestamo.getCliente() != null && prestamo.getCliente().getDni() != null)
                ? prestamo.getCliente().getDni().trim()
                : "";
        boolean esEmpresa = dni.length() == 11;
        String tipo = esEmpresa ? "FACTURA" : "BOLETA";
        String numeroComprobante;
        try {
            Long seq = esEmpresa ? pagoRepository.getNextFacturaSequence() : pagoRepository.getNextBoletaSequence();
            if (seq != null) {
                numeroComprobante = String.format(esEmpresa ? "F001-%06d" : "B001-%06d", seq);
            } else {
                numeroComprobante = String.format(esEmpresa ? "F001-%06d" : "B001-%06d", System.currentTimeMillis() % 1000000);
            }
        } catch (Exception ex) {
            numeroComprobante = String.format(esEmpresa ? "F001-%06d" : "B001-%06d", System.currentTimeMillis() % 1000000);
        }
        builder.tipoComprobante(tipo).numeroComprobante(numeroComprobante);
    }
}
