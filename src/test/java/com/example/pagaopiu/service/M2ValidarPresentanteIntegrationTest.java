package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pagaopiu.controller.rest.ClienteRestController;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
class M2ValidarPresentanteIntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

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

    @Autowired
    private com.example.pagaopiu.TestDatabaseCleaner databaseCleaner;

    private static final String RUC_LOS_ANDES = "20987654321";
    private static final String RAZON_SOCIAL_LOS_ANDES = "COMERCIAL LOS ANDES S.A.C.";
    private static final String DNI_REP_LOS_ANDES = "10000001"; // Juan Carlos Pérez López (Vigente en RENIEC)

    private static final String RUC_INV_NORTENAS = "20999999902";
    private static final String RAZON_SOCIAL_INV_NORTENAS = "INVERSIONES NORTEÑAS S.A.C.";
    private static final String DNI_REP_VENCIDO = "99999902"; // Ana DNI Vencido Ejemplo (DNI no vigente en RENIEC)

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("Búsqueda por RUC en ClienteRestController: NO devuelve nombre ni DNI del representante, solo su cargo requerido")
    void testBusquedaRuc_noDevuelveNombreNiDniRepresentante() {
        ResponseEntity<?> resp = clienteRestController.buscar(RUC_LOS_ANDES);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("dni")).isEqualTo(RUC_LOS_ANDES);
        assertThat(body.get("nombreCompleto")).isEqualTo(RAZON_SOCIAL_LOS_ANDES);
        // Exigencia M2: NO debe devolver nombre ni DNI del representante
        assertThat(body.get("representanteNombre")).isNull();
        assertThat(body.get("representanteDocumento")).isNull();
        // Solo debe devolver su cargo requerido
        assertThat(body.get("representanteCargo")).isEqualTo("GERENTE GENERAL");
    }

    @Test
    @DisplayName("RUC 20: DNI presentado coincide con el representante legal en SUNAT y pasa")
    void testRuc20_dniCoincide_pasa() {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(RUC_LOS_ANDES)
                .nombreCliente(RAZON_SOCIAL_LOS_ANDES)
                .fechaNacimiento(LocalDate.of(2015, 6, 10))
                .monto(new BigDecimal("3500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .dniPresentado(DNI_REP_LOS_ANDES) // Coincide con SUNAT y RENIEC
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");

        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.EN_ESPERA);
        assertThat(prestamo.getEmpresaRuc()).isEqualTo(RUC_LOS_ANDES);
        assertThat(prestamo.getEmpresaRepresentanteDocumento()).isEqualTo(DNI_REP_LOS_ANDES);
        assertThat(prestamo.getEmpresaRepresentanteNombre()).isEqualTo("Juan Carlos Pérez López");
    }

    @Test
    @DisplayName("RUC 20: DNI presentado NO coincide con el representante legal en SUNAT y rechaza con mensaje exacto")
    void testRuc20_dniNoCoincide_rechaza() {
        LocalDate hoy = LocalDate.now(clock);

        // Persona que se presenta con DNI 10000002 (María Elena García Ríos), que no es el representante registrado en SUNAT (10000001)
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(RUC_LOS_ANDES)
                .nombreCliente(RAZON_SOCIAL_LOS_ANDES)
                .fechaNacimiento(LocalDate.of(2015, 6, 10))
                .monto(new BigDecimal("3500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .dniPresentado("10000002")
                .build();

        assertThatThrownBy(() -> prestamoService.generarContrato(dto, "asesor.trujillo"))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La persona que se presenta no figura como representante legal de esta empresa.");
    }

    @Test
    @DisplayName("RUC 10: DNI presentado distinto al derivado de los dígitos 3 a 10 rechaza")
    void testRuc10_dniDistinto_rechaza() {
        LocalDate hoy = LocalDate.now(clock);
        // RUC 10: 10 + 10000001 + 5 -> DNI derivado de dígitos 3 a 10 es "10000001"
        String ruc10 = "10100000015";

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(ruc10)
                .nombreCliente("JUAN CARLOS PÉREZ LÓPEZ")
                .fechaNacimiento(LocalDate.of(1985, 4, 12))
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .dniPresentado("10000002") // Distinto a "10000001"
                .build();

        assertThatThrownBy(() -> prestamoService.generarContrato(dto, "asesor.trujillo"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no coincide con el DNI derivado del RUC 10");
    }

    @Test
    @DisplayName("RUC 20: Representante legal con DNI no vigente en RENIEC rechaza")
    void testRuc20_representanteDniNoVigente_rechaza() {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(RUC_INV_NORTENAS)
                .nombreCliente(RAZON_SOCIAL_INV_NORTENAS)
                .fechaNacimiento(LocalDate.of(2018, 3, 15))
                .monto(new BigDecimal("4000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .dniPresentado(DNI_REP_VENCIDO) // DNI 99999902 tiene dni_vigente = false
                .build();

        assertThatThrownBy(() -> prestamoService.generarContrato(dto, "asesor.trujillo"))
                .isInstanceOf(NegocioException.class)
                .hasMessage("El DNI del representante legal no se encuentra vigente en RENIEC.");
    }
}
