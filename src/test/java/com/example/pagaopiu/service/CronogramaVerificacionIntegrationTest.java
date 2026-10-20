package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.controller.rest.ContratoRestController;
import com.example.pagaopiu.dto.ContratoDetalleResponseDTO;
import com.example.pagaopiu.dto.CuotaResponseDTO;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.security.UsuarioPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
class CronogramaVerificacionIntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private ContratoRestController contratoRestController;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private com.example.pagaopiu.TestDatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("VERIFICAR: La primera cuota vence un mes después de la fecha del contrato y las fechas del cronograma impreso son las mismas que devuelve el sistema")
    void testCronograma_primeraCuotaVenceUnMesDespuesYFechasCoinciden() {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("3000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();
        Long prestamoId = prestamo.getId();

        // 1. Obtener cuotas generadas en el sistema (BD)
        List<Cuota> cuotasEnDb = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamoId);
        assertThat(cuotasEnDb).hasSize(6);

        // Verificar que la cuota 1 vence exactamente un mes (30 días comerciales) después de la fecha de contrato
        Cuota cuota1Db = cuotasEnDb.get(0);
        assertThat(cuota1Db.getNumero()).isEqualTo(1);
        LocalDate fechaEsperadaCuota1 = hoy.plusDays(30);
        assertThat(cuota1Db.getFechaVencimiento())
                .as("La primera cuota debe vencer un mes después de la fecha del contrato")
                .isEqualTo(fechaEsperadaCuota1);

        // 2. Obtener cronograma desde el endpoint del contrato impreso / detalle
        Usuario asesor = usuarioRepository.findByUsername("asesor.trujillo").orElseThrow();
        UsuarioPrincipal principal = new UsuarioPrincipal(asesor);
        ResponseEntity<ContratoDetalleResponseDTO> detalleResp = contratoRestController.obtenerContratoPorId(prestamoId, principal);
        assertThat(detalleResp.getStatusCode().is2xxSuccessful()).isTrue();

        ContratoDetalleResponseDTO detalle = detalleResp.getBody();
        assertThat(detalle).isNotNull();
        List<CuotaResponseDTO> cuotasImpresas = detalle.getCuotas();
        assertThat(cuotasImpresas).hasSize(6);

        // 3. Verificar que las fechas del cronograma impreso sean idénticas a las devueltas por el sistema
        for (int i = 0; i < cuotasEnDb.size(); i++) {
            Cuota cuotaDb = cuotasEnDb.get(i);
            CuotaResponseDTO cuotaImpresa = cuotasImpresas.get(i);

            assertThat(cuotaImpresa.getNumero()).isEqualTo(cuotaDb.getNumero());
            assertThat(cuotaImpresa.getFechaVencimiento())
                    .as("La fecha de vencimiento de la cuota %d en el cronograma impreso debe coincidir con la del sistema", cuotaDb.getNumero())
                    .isEqualTo(cuotaDb.getFechaVencimiento());

            // Además, cada cuota k vence a los 30 * k días
            assertThat(cuotaImpresa.getFechaVencimiento()).isEqualTo(hoy.plusDays(30L * (i + 1)));
        }
    }
}
