package com.example.pagaopiu.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PrestamoRepositoryIntegrationTest {

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Cliente cliente;
    private Sucursal sucursal;
    private Usuario asesor;

    @BeforeEach
    void setUp() {
        // DNI único para el test que no colisiona con el padrón demo y se aísla con @Transactional
        String dniUnico = "88" + (System.currentTimeMillis() % 1000000);
        cliente = clienteRepository.save(Cliente.builder()
                .dni(dniUnico)
                .nombreCompleto("CLIENTE PRUEBA REPOSITORIO")
                .estado(EstadoCliente.ACTIVO)
                .build());

        sucursal = sucursalRepository.findAll().get(0);
        asesor = usuarioRepository.findByUsername("asesor.trujillo").orElseThrow();
    }

    @Test
    @DisplayName("Regla R6: EN_ESPERA sí cuenta para el saldo pendiente y ANULADO no cuenta")
    void testSaldoCapitalPendiente_cuentaEnEsperaYExcluyeAnulado() {
        // Préstamo 1: EN_ESPERA por S/ 4,000 con cuotas PENDIENTE
        Prestamo pEnEspera = prestamoRepository.save(Prestamo.builder()
                .cliente(cliente)
                .sucursal(sucursal)
                .asesor(asesor)
                .fechaPrestamo(LocalDate.now())
                .capital(new BigDecimal("4000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("383.16"))
                .estado(EstadoPrestamo.EN_ESPERA)
                .numeroContrato("CTR-2026-990001")
                .build());

        cuotaRepository.save(Cuota.builder()
                .prestamo(pEnEspera)
                .numero(1)
                .fechaVencimiento(LocalDate.now().plusMonths(1))
                .capital(new BigDecimal("4000.00"))
                .interes(new BigDecimal("88.42"))
                .monto(new BigDecimal("4088.42"))
                .saldoCapital(BigDecimal.ZERO)
                .estado(EstadoCuota.PENDIENTE)
                .build());

        // Préstamo 2: ANULADO por S/ 3,000 con motivo obligatorio
        Prestamo pAnulado = prestamoRepository.save(Prestamo.builder()
                .cliente(cliente)
                .sucursal(sucursal)
                .asesor(asesor)
                .fechaPrestamo(LocalDate.now())
                .capital(new BigDecimal("3000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("287.37"))
                .estado(EstadoPrestamo.ANULADO)
                .numeroContrato("CTR-2026-990002")
                .motivoAnulacion("Cliente desistió tras impresión del contrato")
                .build());

        cuotaRepository.save(Cuota.builder()
                .prestamo(pAnulado)
                .numero(1)
                .fechaVencimiento(LocalDate.now().plusMonths(1))
                .capital(new BigDecimal("3000.00"))
                .interes(new BigDecimal("66.31"))
                .monto(new BigDecimal("3066.31"))
                .saldoCapital(BigDecimal.ZERO)
                .estado(EstadoCuota.PENDIENTE)
                .build());

        BigDecimal saldoPendiente = prestamoRepository.findSaldoCapitalPendienteByClienteId(cliente.getId());

        // Debe sumar únicamente los S/ 4,000 de EN_ESPERA e ignorar los S/ 3,000 de ANULADO
        assertThat(saldoPendiente).isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    @DisplayName("Panel Gerente: countActivosBySucursalId cuenta solo VIGENTE, MOROSO y EXTRAJUDICIAL, excluyendo EN_ESPERA y ANULADO")
    void testCountActivosBySucursalId_excluyeEnEsperaYAnulado() {
        long conteoAntes = prestamoRepository.countActivosBySucursalId(sucursal.getId());

        // Creamos 6 préstamos: 3 que deben contar (VIGENTE, MOROSO, EXTRAJUDICIAL) y 3 que no (EN_ESPERA, ANULADO, CANCELADO)
        crearPrestamo(EstadoPrestamo.VIGENTE, "CTR-2026-990011", null);
        crearPrestamo(EstadoPrestamo.MOROSO, "CTR-2026-990012", null);
        crearPrestamo(EstadoPrestamo.EXTRAJUDICIAL, "CTR-2026-990013", null);
        crearPrestamo(EstadoPrestamo.EN_ESPERA, "CTR-2026-990014", null);
        crearPrestamo(EstadoPrestamo.ANULADO, "CTR-2026-990015", "Anulación por error en cuotas");
        crearPrestamo(EstadoPrestamo.CANCELADO, "CTR-2026-990016", null);

        long conteoDespues = prestamoRepository.countActivosBySucursalId(sucursal.getId());

        // La diferencia neta debe ser exactamente 3
        assertThat(conteoDespues - conteoAntes).isEqualTo(3);
    }

    private Prestamo crearPrestamo(EstadoPrestamo estado, String numeroContrato, String motivoAnulacion) {
        return prestamoRepository.save(Prestamo.builder()
                .cliente(cliente)
                .sucursal(sucursal)
                .asesor(asesor)
                .fechaPrestamo(LocalDate.now())
                .capital(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("95.79"))
                .estado(estado)
                .numeroContrato(numeroContrato)
                .motivoAnulacion(motivoAnulacion)
                .build());
    }
}
