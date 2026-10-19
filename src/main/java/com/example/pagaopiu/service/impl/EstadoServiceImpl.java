package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.service.EstadoService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadoServiceImpl implements EstadoService {

    private final PrestamoRepository prestamoRepository;
    private final CuotaRepository cuotaRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    public EstadoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository,
            Clock clock) {
        this.prestamoRepository = prestamoRepository;
        this.cuotaRepository = cuotaRepository;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recalcularPrestamo(Prestamo prestamo) {
        if (prestamo == null) {
            return;
        }

        // EN_ESPERA y ANULADO no se alteran con recálculo de morosidad
        if (prestamo.getEstado() == EstadoPrestamo.EN_ESPERA || prestamo.getEstado() == EstadoPrestamo.ANULADO) {
            return;
        }

        // Si ya está CANCELADO, no cambia de estado
        if (prestamo.getEstado() == EstadoPrestamo.CANCELADO) {
            return;
        }

        // EXTRAJUDICIAL no revierte automáticamente solo
        if (prestamo.getEstado() == EstadoPrestamo.EXTRAJUDICIAL) {
            return;
        }

        List<Cuota> pendientes = cuotaRepository.findByPrestamoIdAndEstadoOrderByNumeroAsc(prestamo.getId(), EstadoCuota.PENDIENTE);

        // Si no quedan cuotas pendientes, el préstamo está cancelado
        if (pendientes.isEmpty()) {
            prestamo.setEstado(EstadoPrestamo.CANCELADO);
            if (prestamo.getFechaCancelacion() == null) {
                prestamo.setFechaCancelacion(LocalDate.now(clock));
            }
            prestamoRepository.save(prestamo);
            return;
        }

        LocalDate hoy = LocalDate.now(clock);
        List<Cuota> vencidas = pendientes.stream()
                .filter(c -> c.getFechaVencimiento().isBefore(hoy))
                .toList();

        if (!vencidas.isEmpty()) {
            Cuota cuotaMasAntigua = vencidas.get(0);
            long diasAtraso = ChronoUnit.DAYS.between(cuotaMasAntigua.getFechaVencimiento(), hoy);

            if (diasAtraso >= 365) {
                prestamo.setEstado(EstadoPrestamo.EXTRAJUDICIAL);
            } else {
                prestamo.setEstado(EstadoPrestamo.MOROSO);
            }
        } else {
            // No tiene cuotas vencidas: préstamo VIGENTE
            prestamo.setEstado(EstadoPrestamo.VIGENTE);
        }

        prestamoRepository.save(prestamo);
    }

    @Override
    @Transactional
    public void recalcularCliente(Cliente cliente) {
        if (cliente == null) {
            return;
        }

        // EXTRAJUDICIAL no revierte automáticamente
        if (cliente.getEstado() == EstadoCliente.EXTRAJUDICIAL) {
            return;
        }

        List<Prestamo> prestamos = prestamoRepository.findByClienteId(cliente.getId());
        if (prestamos.isEmpty()) {
            return;
        }

        for (Prestamo p : prestamos) {
            if (p.getEstado() != EstadoPrestamo.CANCELADO && p.getEstado() != EstadoPrestamo.EN_ESPERA && p.getEstado() != EstadoPrestamo.ANULADO) {
                recalcularPrestamo(p);
            }
        }

        List<Prestamo> prestamosActivos = prestamos.stream()
                .filter(p -> p.getEstado() != EstadoPrestamo.CANCELADO && p.getEstado() != EstadoPrestamo.EN_ESPERA && p.getEstado() != EstadoPrestamo.ANULADO)
                .toList();

        // El estado del cliente es el PEOR de sus préstamos activos: EXTRAJUDICIAL > MOROSO > ACTIVO
        boolean tieneExtrajudicial = prestamosActivos.stream()
                .anyMatch(p -> p.getEstado() == EstadoPrestamo.EXTRAJUDICIAL);

        if (tieneExtrajudicial) {
            cliente.setEstado(EstadoCliente.EXTRAJUDICIAL);
        } else {
            boolean tieneMoroso = prestamosActivos.stream()
                    .anyMatch(p -> p.getEstado() == EstadoPrestamo.MOROSO);

            if (tieneMoroso) {
                cliente.setEstado(EstadoCliente.MOROSO);
            } else {
                cliente.setEstado(EstadoCliente.ACTIVO);
            }
        }

        clienteRepository.save(cliente);
    }

    @Override
    @Scheduled(cron = "${app.cron.recalculo-estados:0 0 1 * * *}", zone = "America/Lima")
    @Transactional
    public void recalcularTodos() {
        // Recalcular todos los préstamos no cancelados
        List<Prestamo> todosPrestamos = prestamoRepository.findAll();
        for (Prestamo p : todosPrestamos) {
            if (p.getEstado() != EstadoPrestamo.CANCELADO && p.getEstado() != EstadoPrestamo.EN_ESPERA && p.getEstado() != EstadoPrestamo.ANULADO) {
                recalcularPrestamo(p);
            }
        }

        // Recalcular todos los clientes
        List<Cliente> todosClientes = clienteRepository.findAll();
        for (Cliente c : todosClientes) {
            recalcularCliente(c);
        }
    }
}
