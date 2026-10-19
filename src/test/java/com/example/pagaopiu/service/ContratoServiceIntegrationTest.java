package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
class ContratoServiceIntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private EstadoService estadoService;

    @Autowired
    private ContratoFileStorageService contratoFileStorageService;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private static final byte[] PDF_VALIDO = "%PDF-1.4\n%Contenido simulado para firma de contrato\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Generar contrato: crea préstamo en EN_ESPERA, impreso=false y correlativo único")
    void testGenerarContrato_creaEnEspera() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000007")
                .nombreCliente("Lucía Fernanda Sánchez Chávez")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(12)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");

        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.EN_ESPERA);
        assertThat(prestamo.getImpreso()).isFalse();
        assertThat(prestamo.getNumeroContrato()).matches("^CTR-\\d{4}-\\d{6}$");

        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        assertThat(cuotas).hasSize(12);
    }

    @Test
    @DisplayName("Regla de negocio: Solo se permite un contrato en EN_ESPERA por cliente")
    void testGenerarContrato_unSoloEnEsperaPorCliente() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000008")
                .nombreCliente("Ángel Gabriel Peña Morales")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        prestamoService.generarContrato(dto, "asesor.trujillo");

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.generarContrato(dto, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("ya tiene un contrato pendiente de firma");
    }

    @Test
    @DisplayName("Vencimiento: generarContrato exige que fechaPrestamo sea exactamente hoy")
    void testGenerarContrato_exigeFechaHoy() {
        PrestamoRegistroDTO dtoAyer = PrestamoRegistroDTO.builder()
                .dni("10000009")
                .nombreCliente("Rosa María Flores Benítez")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock).minusDays(1))
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.generarContrato(dtoAyer, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("debe ser exactamente la fecha actual");
    }

    @Test
    @DisplayName("Imprimir y reimprimir: actualiza estado impreso a true y conserva el mismo número")
    void testMarcarImpreso_permiteReimprimir() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000010")
                .nombreCliente("Óscar Iván Gutiérrez Quiroz")
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(12)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");
        String numeroOriginal = prestamo.getNumeroContrato();

        Prestamo impreso1 = prestamoService.marcarImpreso(prestamo.getId(), "asesor.trujillo");
        assertThat(impreso1.getImpreso()).isTrue();
        assertThat(impreso1.getFechaImpresion()).isNotNull();
        assertThat(impreso1.getNumeroContrato()).isEqualTo(numeroOriginal);

        Prestamo reimpreso = prestamoService.marcarImpreso(prestamo.getId(), "asesor.trujillo");
        assertThat(reimpreso.getImpreso()).isTrue();
        assertThat(reimpreso.getNumeroContrato()).isEqualTo(numeroOriginal);
    }

    @Test
    @DisplayName("Cancelar antes de imprimir: descarta el borrador y no queda préstamo en BD")
    void testCancelar_antesDeImprimir_descartaBorrador() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000011")
                .nombreCliente("Inés Del Carmen Ortiz Ramos")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");
        Long id = p.getId();

        Prestamo resultado = prestamoService.cancelarPrestamo(id, null, "asesor.trujillo");
        assertThat(resultado).isNull();
        assertThat(prestamoRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Cancelar después de imprimir: pasa a ANULADO con motivo obligatorio")
    void testCancelar_despuesDeImprimir_pasaAAnuladoConMotivo() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000012")
                .nombreCliente("Héctor Manuel Aguirre Mendoza")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");
        prestamoService.marcarImpreso(p.getId(), "asesor.trujillo");

        assertThrows(NegocioException.class, () ->
                prestamoService.cancelarPrestamo(p.getId(), "   ", "asesor.trujillo"));

        Prestamo anulado = prestamoService.cancelarPrestamo(p.getId(), "Cliente cambió de parecer", "asesor.trujillo");
        assertThat(anulado.getEstado()).isEqualTo(EstadoPrestamo.ANULADO);
        assertThat(anulado.getMotivoAnulacion()).isEqualTo("Cliente cambió de parecer");
    }

    @Test
    @DisplayName("Subida de contrato firmado: activa préstamo a VIGENTE y recalcula cliente")
    void testSubirContratoFirmado_activacionExitosa() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000013")
                .nombreCliente("Sofía Alejandra Paz Soldán")
                .monto(new BigDecimal("1200.00"))
                .plazoMeses(12)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");
        prestamoService.marcarImpreso(p.getId(), "asesor.trujillo");

        MockMultipartFile archivoPdf = new MockMultipartFile("archivo", "firmado.pdf", "application/pdf", PDF_VALIDO);
        Prestamo activado = prestamoService.subirContratoFirmado(p.getId(), "  " + p.getNumeroContrato().toLowerCase() + "  ", archivoPdf, "asesor.trujillo");

        assertThat(activado.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(activado.getContratoHash()).isNotNull();
        assertThat(activado.getContratoRuta()).isNotNull();
        assertThat(activado.getContratoSubidoPor()).isNotNull();

        Cliente c = clienteRepository.findByDni("10000013").orElseThrow();
        assertThat(c.getEstado()).isEqualTo(EstadoCliente.ACTIVO);
    }

    @Test
    @DisplayName("Subida rechazada si el contrato no fue impreso")
    void testSubirContrato_rechazaSinHaberImpreso() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000014")
                .nombreCliente("Matías Alonso Vera Cárdenas")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");

        MockMultipartFile archivoPdf = new MockMultipartFile("archivo", "firmado.pdf", "application/pdf", PDF_VALIDO);
        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.subirContratoFirmado(p.getId(), p.getNumeroContrato(), archivoPdf, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("debe ser impreso antes");
    }

    @Test
    @DisplayName("Subida rechazada si el contrato venció (fecha anterior a hoy)")
    void testSubirContrato_rechazaContratoVencido() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000015")
                .nombreCliente("Clara Isabel Castañeda Ruíz")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");
        prestamoService.marcarImpreso(p.getId(), "asesor.trujillo");

        p.setFechaPrestamo(LocalDate.now(clock).minusDays(1));
        prestamoRepository.save(p);

        MockMultipartFile archivoPdf = new MockMultipartFile("archivo", "firmado.pdf", "application/pdf", PDF_VALIDO);
        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.subirContratoFirmado(p.getId(), p.getNumeroContrato(), archivoPdf, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("ha vencido porque no fue formalizado");
    }

    @Test
    @DisplayName("Restricción de sucursal: asesor de otra agencia no puede operar el contrato")
    void testSubirContrato_restriccionSucursal() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000016")
                .nombreCliente("Raúl Ernesto Cáceres Polo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.marcarImpreso(p.getId(), "asesor.chiclayo"));
        assertThat(ex.getMessage()).contains("Solo los asesores de la sucursal del préstamo");
    }

    @Test
    @DisplayName("Hash duplicado: rechaza si el mismo archivo ya se usó en otro préstamo")
    void testSubirContrato_rechazaHashDuplicado() {
        PrestamoRegistroDTO dto1 = PrestamoRegistroDTO.builder()
                .dni("10000017")
                .nombreCliente("Valeria Nicole Campos Guzmán")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p1 = prestamoService.generarContrato(dto1, "asesor.trujillo");
        prestamoService.marcarImpreso(p1.getId(), "asesor.trujillo");

        MockMultipartFile archivoPdf = new MockMultipartFile("archivo", "firmado1.pdf", "application/pdf", PDF_VALIDO);
        prestamoService.subirContratoFirmado(p1.getId(), p1.getNumeroContrato(), archivoPdf, "asesor.trujillo");

        PrestamoRegistroDTO dto2 = PrestamoRegistroDTO.builder()
                .dni("10000016")
                .nombreCliente("Raúl Ernesto Cáceres Polo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p2 = prestamoService.generarContrato(dto2, "asesor.trujillo");
        prestamoService.marcarImpreso(p2.getId(), "asesor.trujillo");

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.subirContratoFirmado(p2.getId(), p2.getNumeroContrato(), archivoPdf, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("ya fue utilizado como contrato firmado");
    }

    @Test
    @DisplayName("Cobranza: rechaza cobros en préstamos EN_ESPERA y ANULADO")
    void testCobranza_rechazaEnEsperaYAnulado() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000015")
                .nombreCliente("Clara Isabel Castañeda Ruíz")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        Prestamo p = prestamoService.generarContrato(dto, "asesor.trujillo");

        // 1. Caso EN_ESPERA
        NegocioException exEspera1 = assertThrows(NegocioException.class, () ->
                pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo"));
        assertThat(exEspera1.getMessage()).contains("se encuentra en espera");

        NegocioException exEspera2 = assertThrows(NegocioException.class, () ->
                pagoService.cancelarAnticipado(p.getId(), "asesor.trujillo"));
        assertThat(exEspera2.getMessage()).contains("se encuentra en espera");

        // 2. Pasar a ANULADO
        prestamoService.marcarImpreso(p.getId(), "asesor.trujillo");
        prestamoService.cancelarPrestamo(p.getId(), "Cliente desistió formalmente", "asesor.trujillo");

        // Caso ANULADO
        NegocioException exAnulado1 = assertThrows(NegocioException.class, () ->
                pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo"));
        assertThat(exAnulado1.getMessage()).contains("se encuentra anulado");

        NegocioException exAnulado2 = assertThrows(NegocioException.class, () ->
                pagoService.cancelarAnticipado(p.getId(), "asesor.trujillo"));
        assertThat(exAnulado2.getMessage()).contains("se encuentra anulado");
    }

    @Test
    @DisplayName("Job de vencimiento: anula contratos en EN_ESPERA emitidos en días anteriores")
    void testJobVencimiento_anulaContratosDeDiasAnteriores() {
        Cliente c = clienteRepository.findByDni("10000014").orElseGet(() ->
                clienteRepository.save(Cliente.builder()
                        .dni("10000014")
                        .nombreCompleto("Matías Alonso Vera Cárdenas")
                        .estado(EstadoCliente.ACTIVO)
                        .build()));

        Prestamo pAyer = prestamoRepository.save(Prestamo.builder()
                .cliente(c)
                .sucursal(sucursalRepository.findAll().get(0))
                .asesor(usuarioRepository.findByUsername("asesor.trujillo").orElseThrow())
                .fechaPrestamo(LocalDate.now(clock).minusDays(1))
                .capital(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("95.79"))
                .estado(EstadoPrestamo.EN_ESPERA)
                .numeroContrato("CTR-2026-880002")
                .impreso(true)
                .build());

        prestamoService.vencerContratosEnEspera();

        Prestamo actualizado = prestamoRepository.findById(pAyer.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPrestamo.ANULADO);
        assertThat(actualizado.getMotivoAnulacion()).contains("Vencimiento automático");
    }

    @Test
    @DisplayName("Auto-corrección: cliente con EN_ESPERA de ayer genera contrato nuevo hoy y el viejo queda ANULADO")
    void testAutoCorreccion_enEsperaDeAyerSeAnulaYPermiteNuevo() {
        Cliente c = clienteRepository.findByDni("10000013").orElseGet(() ->
                clienteRepository.save(Cliente.builder()
                        .dni("10000013")
                        .nombreCompleto("Sofía Alejandra Paz Soldán")
                        .estado(EstadoCliente.ACTIVO)
                        .build()));

        Prestamo viejo = prestamoRepository.save(Prestamo.builder()
                .cliente(c)
                .sucursal(sucursalRepository.findAll().get(0))
                .asesor(usuarioRepository.findByUsername("asesor.trujillo").orElseThrow())
                .fechaPrestamo(LocalDate.now(clock).minusDays(1))
                .capital(new BigDecimal("1500.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("143.68"))
                .estado(EstadoPrestamo.EN_ESPERA)
                .numeroContrato("CTR-2026-880003")
                .impreso(true)
                .build());

        PrestamoRegistroDTO dtoHoy = PrestamoRegistroDTO.builder()
                .dni("10000013")
                .nombreCliente("Sofía Alejandra Paz Soldán")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(12)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo nuevo = prestamoService.generarContrato(dtoHoy, "asesor.trujillo");
        assertThat(nuevo).isNotNull();
        assertThat(nuevo.getEstado()).isEqualTo(EstadoPrestamo.EN_ESPERA);

        Prestamo viejoActualizado = prestamoRepository.findById(viejo.getId()).orElseThrow();
        assertThat(viejoActualizado.getEstado()).isEqualTo(EstadoPrestamo.ANULADO);
        assertThat(viejoActualizado.getMotivoAnulacion()).contains("Vencimiento automático");
    }

    @Test
    @DisplayName("EstadoService: cliente MOROSO que cancela su único préstamo vuelve a ACTIVO")
    void testRecalcularCliente_morosoQueCancelaVuelveAActivo() {
        Cliente c = clienteRepository.save(Cliente.builder()
                .dni("88990011")
                .nombreCompleto("CLIENTE PRUEBA MOROSO")
                .estado(EstadoCliente.MOROSO)
                .build());

        Prestamo p = prestamoRepository.save(Prestamo.builder()
                .cliente(c)
                .sucursal(sucursalRepository.findAll().get(0))
                .asesor(usuarioRepository.findByUsername("asesor.trujillo").orElseThrow())
                .fechaPrestamo(LocalDate.now(clock).minusMonths(2))
                .capital(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.30"))
                .tem(new BigDecimal("0.0221044506"))
                .cuotaFija(new BigDecimal("95.79"))
                .estado(EstadoPrestamo.CANCELADO)
                .numeroContrato("CTR-2026-880001")
                .impreso(true)
                .build());

        estadoService.recalcularCliente(c);

        Cliente actualizado = clienteRepository.findById(c.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoCliente.ACTIVO);
    }

    @Test
    @DisplayName("Rollback transaccional: elimina el archivo físico escrito en disco")
    void testRollback_eliminaArchivoFisico() {
        MockMultipartFile pdf = new MockMultipartFile("archivo", "test_rollback.pdf", "application/pdf", PDF_VALIDO);
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        final AtomicReference<String> rutaRef = new AtomicReference<>();
        tt.execute(status -> {
            ContratoFileStorageService.ContratoFileResult res = contratoFileStorageService.almacenarArchivo(pdf);
            rutaRef.set(res.rutaRelativa());
            status.setRollbackOnly();
            return null;
        });

        String nombreFisico = rutaRef.get();
        assertThat(nombreFisico).isNotNull();

        assertThrows(NegocioException.class, () ->
                contratoFileStorageService.cargarComoRecurso(nombreFisico));
    }
}