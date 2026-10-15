package com.example.pagaopiu.config;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.service.EstadoService;
import com.example.pagaopiu.service.PagoService;
import com.example.pagaopiu.service.PrestamoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final PrestamoService prestamoService;
    private final PagoService pagoService;
    private final EstadoService estadoService;
    private final PrestamoRepository prestamoRepository;
    private final CuotaRepository cuotaRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    @Value("${app.demo.seed-enabled:true}")
    private boolean seedEnabled;

    public DemoDataInitializer(
            PrestamoService prestamoService,
            PagoService pagoService,
            EstadoService estadoService,
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository,
            Clock clock) {
        this.prestamoService = prestamoService;
        this.pagoService = pagoService;
        this.estadoService = estadoService;
        this.prestamoRepository = prestamoRepository;
        this.cuotaRepository = cuotaRepository;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }

        // Si ya existen préstamos registrados, no sembramos de nuevo
        if (prestamoRepository.count() > 0) {
            log.info("Base de datos ya contiene préstamos. Omitiendo siembra demo.");
            return;
        }

        log.info("Iniciando siembra de datos demo para evaluación...");
        LocalDate hoy = LocalDate.now(clock);

        try {
            // ── Escenario 1: Cliente con préstamo al día (VIGENTE, cuotas pagadas) ──
            Prestamo p1 = prestamoService.registrarPrestamo(
                    PrestamoRegistroDTO.builder()
                            .dni("27427864")
                            .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                            .monto(new BigDecimal("1000.00"))
                            .plazoMeses(12)
                            .fechaPrestamo(hoy.minusMonths(2))
                            .build(),
                    "asesor.trujillo"
            );
            pagoService.pagarSiguienteCuota(p1.getId(), "asesor.trujillo");
            pagoService.pagarSiguienteCuota(p1.getId(), "asesor.trujillo");

            // ── Escenario 2: Cliente moroso (cuota vencida < hoy) ──
            Prestamo p2 = prestamoService.registrarPrestamo(
                    PrestamoRegistroDTO.builder()
                            .dni("10000001")
                            .nombreCliente("Juan Carlos Pérez López")
                            .monto(new BigDecimal("1500.00"))
                            .plazoMeses(12)
                            .fechaPrestamo(hoy.minusMonths(1))
                            .build(),
                    "asesor.piura"
            );
            List<Cuota> cuotasP2 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p2.getId());
            Cuota cP2_1 = cuotasP2.get(0);
            cP2_1.setFechaVencimiento(hoy.minusDays(15));
            cuotaRepository.save(cP2_1);

            // ── Escenario 3: Cliente extrajudicial (cuota vencida con >= 365 días) ──
            Prestamo p3 = prestamoService.registrarPrestamo(
                    PrestamoRegistroDTO.builder()
                            .dni("10000002")
                            .nombreCliente("María Elena García Ríos")
                            .monto(new BigDecimal("2000.00"))
                            .plazoMeses(24)
                            .fechaPrestamo(hoy.minusYears(1).minusDays(30))
                            .build(),
                    "asesor.chiclayo"
            );
            List<Cuota> cuotasP3 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p3.getId());
            Cuota cP3_1 = cuotasP3.get(0);
            cP3_1.setFechaVencimiento(hoy.minusDays(380));
            cuotaRepository.save(cP3_1);

            // ── Escenario 4: Cliente al tope de 10,000 (no puede solicitar más crédito) ──
            prestamoService.registrarPrestamo(
                    PrestamoRegistroDTO.builder()
                            .dni("10000006")
                            .nombreCliente("César Abraham Vallejo Mendoza")
                            .monto(new BigDecimal("10000.00"))
                            .plazoMeses(24)
                            .fechaPrestamo(hoy)
                            .build(),
                    "asesor.chimbote"
            );

            // Recalcular estados
            estadoService.recalcularTodos();

            log.info("Siembra de datos demo completada con éxito:");
            log.info("  [1] Al día (VIGENTE): DNI 27427864 - CASTILLO TERRONES, JOSE PEDRO");
            log.info("  [2] Moroso: DNI 10000001 - Juan Carlos Pérez López");
            log.info("  [3] Extrajudicial: DNI 10000002 - María Elena García Ríos");
            log.info("  [4] Al tope (S/ 10,000): DNI 10000006 - César Abraham Vallejo Mendoza");

        } catch (Exception ex) {
            log.warn("No se completó la siembra demo: {}", ex.getMessage());
        }
    }
}
