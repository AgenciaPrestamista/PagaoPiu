package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.controller.rest.ClienteRestController;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Pago;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
class RucPrestamoIntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private ClienteRestController clienteRestController;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private Clock clock;

    private static final String RUC_LOS_ANDES = "20987654321";
    private static final String RAZON_SOCIAL = "COMERCIAL LOS ANDES S.A.C.";

    @BeforeEach
    void setUp() {
        cuotaRepository.deleteAll();
        pagoRepository.deleteAll();
        prestamoRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    @Test
    @DisplayName("Debe consultar cliente por RUC de 11 dígitos y obtener razón social")
    void testConsultaClientePorRuc() {
        ResponseEntity<?> resp = clienteRestController.buscar(RUC_LOS_ANDES);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("dni")).isEqualTo(RUC_LOS_ANDES);
        assertThat(body.get("nombreCompleto")).isEqualTo(RAZON_SOCIAL);
        assertThat(body.get("elegible")).isEqualTo(true);
    }

    @Test
    @DisplayName("Debe registrar préstamo formalizado a empresa con RUC y emitir Factura Electrónica (F001)")
    void testPrestamoYFacturaPorRuc() {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(RUC_LOS_ANDES)
                .nombreCliente(RAZON_SOCIAL)
                .fechaNacimiento(LocalDate.of(2010, 5, 20)) // Fecha de constitución
                .monto(new BigDecimal("5000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamo = prestamoService.registrarPrestamo(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getCliente().getDni()).isEqualTo(RUC_LOS_ANDES);
        assertThat(prestamo.getCliente().getNombreCompleto()).isEqualTo(RAZON_SOCIAL);
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);

        Pago pago = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");
        assertThat(pago).isNotNull();
        assertThat(pago.getTipoComprobante()).isEqualTo("FACTURA");
        assertThat(pago.getNumeroComprobante()).startsWith("F001-");
    }
}
