package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pagaopiu.dto.caja.CajaEstadoResponseDTO;
import com.example.pagaopiu.dto.caja.IncidenciaHermesDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesRequestDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesResponseDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.enums.EstadoEntregaHermes;
import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
import com.example.pagaopiu.repository.CajaSucursalRepository;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.EntregaHermesRepository;
import com.example.pagaopiu.repository.IncidenciaHermesRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HermesCajaIntegrationTest {

    @Autowired
    private CajaHermesService cajaHermesService;

    @Autowired
    private CajaSucursalRepository cajaSucursalRepository;

    @Autowired
    private EntregaHermesRepository entregaHermesRepository;

    @Autowired
    private IncidenciaHermesRepository incidenciaHermesRepository;

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

    private Sucursal sucursalTrujillo;

    @BeforeEach
    void setUp() {
        incidenciaHermesRepository.deleteAll();
        entregaHermesRepository.deleteAll();
        cajaSucursalRepository.deleteAll();
        cuotaRepository.deleteAll();
        pagoRepository.deleteAll();
        prestamoRepository.deleteAll();
        clienteRepository.deleteAll();

        sucursalTrujillo = usuarioRepository.findByUsername("asesor.trujillo")
                .orElseThrow()
                .getSucursal();
    }

    @Test
    @DisplayName("Entrega completa: 50,000 apto y 0 observado abre caja con 50,000 y conforme")
    void testEntregaCompleta() {
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-001")
                .montoApto(new BigDecimal("50000.00"))
                .montoObservado(BigDecimal.ZERO)
                .build();

        RecepcionHermesResponseDTO resp = cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo");

        assertThat(resp).isNotNull();
        assertThat(resp.getEstado()).isEqualTo(EstadoEntregaHermes.RECIBIDA_CONFORME);
        assertThat(resp.getSaldoCajaDisponible()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(resp.isIncidenciaGenerada()).isFalse();

        // Verificar estado de caja
        CajaEstadoResponseDTO estadoCaja = cajaHermesService.obtenerEstadoCaja(sucursalTrujillo.getId(), "asesor.trujillo");
        assertThat(estadoCaja.isAbierta()).isTrue();
        assertThat(estadoCaja.getSaldoDisponible()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(estadoCaja.getMontoObservadoAcumulado()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Entrega con 40,000 aptos y 10,000 observados abre la caja con 40,000 y crea incidencia ABIERTA")
    void testEntregaConObservados_abreCajaYCreaIncidencia() {
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-002")
                .montoApto(new BigDecimal("40000.00"))
                .montoObservado(new BigDecimal("10000.00"))
                .motivo("Fajo de billetes húmedos y deteriorados")
                .build();

        RecepcionHermesResponseDTO resp = cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo");

        assertThat(resp).isNotNull();
        assertThat(resp.getEstado()).isEqualTo(EstadoEntregaHermes.RECIBIDA_CON_OBSERVACIONES);
        assertThat(resp.getSaldoCajaDisponible()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(resp.isIncidenciaGenerada()).isTrue();
        assertThat(resp.getIncidenciaId()).isNotNull();

        // La caja abre con el monto apto (40,000); los 10,000 observados quedan fuera del saldo
        CajaEstadoResponseDTO estadoCaja = cajaHermesService.obtenerEstadoCaja(sucursalTrujillo.getId(), "asesor.trujillo");
        assertThat(estadoCaja.isAbierta()).isTrue();
        assertThat(estadoCaja.getSaldoDisponible()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(estadoCaja.getMontoObservadoAcumulado()).isEqualByComparingTo(new BigDecimal("10000.00"));

        // Verificar incidencia abierta
        List<IncidenciaHermesDTO> incidencias = cajaHermesService.listarIncidencias(EstadoIncidenciaHermes.ABIERTA, sucursalTrujillo.getId(), "gerente");
        assertThat(incidencias).hasSize(1);
        IncidenciaHermesDTO inc = incidencias.get(0);
        assertThat(inc.getId()).isEqualTo(resp.getIncidenciaId());
        assertThat(inc.getEstado()).isEqualTo("ABIERTA");
        assertThat(inc.getMontoEsperado()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(inc.getMontoApto()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(inc.getMontoObservado()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(inc.getMotivo()).isEqualTo("Fajo de billetes húmedos y deteriorados");
        assertThat(inc.getUsuarioReporta()).isEqualTo("Asesor Trujillo");
    }

    @Test
    @DisplayName("Diferencia sin motivo se rechaza")
    void testDiferenciaSinMotivo_rechaza() {
        // Diferencia: 45,000 + 0 = 45,000 != 50,000 esperado, pero sin motivo
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-003")
                .montoApto(new BigDecimal("45000.00"))
                .montoObservado(BigDecimal.ZERO)
                .motivo("   ")
                .build();

        assertThatThrownBy(() -> cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("El gerente cierra la incidencia con resolución escrita; una incidencia cerrada no se modifica")
    void testGerenteCierraIncidencia_noSeModifica() {
        // 1. Crear incidencia abierta
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-004")
                .montoApto(new BigDecimal("45000.00"))
                .montoObservado(new BigDecimal("5000.00"))
                .motivo("Billetes rotos")
                .build();
        RecepcionHermesResponseDTO recepcion = cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo");
        Long incId = recepcion.getIncidenciaId();

        // 2. El gerente cierra la incidencia
        String resolucion = "Hermes repuso el monto observado en fecha de hoy";
        IncidenciaHermesDTO cerrada = cajaHermesService.cerrarIncidencia(incId, resolucion, "gerente");

        assertThat(cerrada.getEstado()).isEqualTo("CERRADA");
        assertThat(cerrada.getResolucion()).isEqualTo(resolucion);
        assertThat(cerrada.getUsuarioCierre()).isEqualTo("Gerente General");
        assertThat(cerrada.getFechaCierre()).isNotNull();

        // 3. Una incidencia cerrada no se puede volver a cerrar ni modificar
        assertThatThrownBy(() -> cajaHermesService.cerrarIncidencia(incId, "Nueva resolucion", "gerente"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ya se encuentra cerrada y no puede ser modificada");
    }

    @Test
    @DisplayName("Un asesor NO puede cerrar incidencias")
    void testAsesorNoPuedeCerrarIncidencias() {
        // 1. Crear incidencia abierta
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-005")
                .montoApto(new BigDecimal("48000.00"))
                .montoObservado(new BigDecimal("2000.00"))
                .motivo("Dudosos")
                .build();
        RecepcionHermesResponseDTO recepcion = cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo");
        Long incId = recepcion.getIncidenciaId();

        // 2. Asesor intenta cerrar la incidencia -> Debe ser rechazado
        assertThatThrownBy(() -> cajaHermesService.cerrarIncidencia(incId, "Intento de asesor", "asesor.trujillo"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Solo el Gerente General tiene autorización para cerrar incidencias");
    }

    @Test
    @DisplayName("Regla de rechazar desembolsos si el saldo en caja no alcanza se mantiene")
    void testDesembolsoRechazadoSiSaldoNoAlcanza() {
        // Abrir caja con 40,000 aptos (ej. 10,000 observados)
        RecepcionHermesRequestDTO request = RecepcionHermesRequestDTO.builder()
                .codigoRemesa("HERMES-TRU-006")
                .montoApto(new BigDecimal("40000.00"))
                .montoObservado(new BigDecimal("10000.00"))
                .motivo("Billetes observados")
                .build();
        cajaHermesService.registrarRecepcionHermes(request, "asesor.trujillo");

        // Intentar desembolsar 45,000 (superior al saldo disponible de 40,000)
        assertThatThrownBy(() -> cajaHermesService.verificarYDescontarSaldoCaja(sucursalTrujillo.getId(), new BigDecimal("45000.00")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Saldo insuficiente en la caja de la sucursal para realizar el desembolso");

        // Desembolso válido de 5,000: debe descontarse correctamente dejando 35,000
        cajaHermesService.verificarYDescontarSaldoCaja(sucursalTrujillo.getId(), new BigDecimal("5000.00"));

        CajaEstadoResponseDTO estado = cajaHermesService.obtenerEstadoCaja(sucursalTrujillo.getId(), "asesor.trujillo");
        assertThat(estado.getSaldoDisponible()).isEqualByComparingTo(new BigDecimal("35000.00"));
    }
}
