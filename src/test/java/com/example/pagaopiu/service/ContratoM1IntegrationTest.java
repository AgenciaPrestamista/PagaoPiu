package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class ContratoM1IntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private ContratoDocumentoService contratoDocumentoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private Clock clock;

    @MockitoBean
    private SunatClient sunatClient;

    private Sucursal sucursalTrujillo;
    private Usuario asesorTrujillo;

    @BeforeEach
    void setUp() {
        sucursalTrujillo = sucursalRepository.findByNombre("Trujillo")
                .orElseGet(() -> sucursalRepository.findAll().stream().findFirst().orElseThrow());
        asesorTrujillo = usuarioRepository.findByUsername("asesor.trujillo")
                .orElseGet(() -> usuarioRepository.findAll().stream().findFirst().orElseThrow());
    }

    @Test
    @DisplayName("Golden test: el texto generado del contrato coincide 100% palabra por palabra con Contrato.docx")
    void testGoldenContractMatch() throws Exception {
        // 1. Obtener texto golden de referencia
        String goldenRaw;
        try (InputStream is = getClass().getResourceAsStream("/contratos/contrato_golden.txt")) {
            assertThat(is).as("El archivo /contratos/contrato_golden.txt debe existir").isNotNull();
            goldenRaw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 2. Preparar un préstamo de prueba para DNI
        Cliente cliente = clienteRepository.findByDni("10000007").orElseGet(() -> {
            Cliente nuevo = Cliente.builder()
                    .dni("10000007")
                    .nombreCompleto("Lucía Fernanda Sánchez Chávez")
                    .estado(EstadoCliente.ACTIVO)
                    .build();
            return clienteRepository.save(nuevo);
        });

        Prestamo prestamo = Prestamo.builder()
                .numeroContrato("CTR-2026-TESTGOLDEN")
                .cliente(cliente)
                .sucursal(sucursalTrujillo)
                .asesor(asesorTrujillo)
                .capital(new BigDecimal("3000.00"))
                .plazoMeses(12)
                .tea(new BigDecimal("0.3000"))
                .tem(new BigDecimal("0.0221"))
                .cuotaFija(new BigDecimal("287.52"))
                .fechaPrestamo(LocalDate.of(2026, 10, 19))
                .estado(EstadoPrestamo.EN_ESPERA)
                .impreso(false)
                .build();

        // 3. Generar texto con el servicio del sistema
        ContratoDocumentoService.ContratoTextoDTO generado = contratoDocumentoService.generarTextoContrato(prestamo);
        String textoCompletoGenerado = generado.textoContrato() + "\n\n" + generado.bloqueFirmas();

        // 4. Poblar marcadores en el texto golden
        String fechaEsperada = prestamo.getFechaPrestamo().format(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "PE"))
        );
        String fragmentoClienteEsperado = "EL CLIENTE Lucía Fernanda Sánchez Chávez, con DNI Nº 10000007.";
        String goldenReemplazado = goldenRaw
                .replace("{{NUMERO_CONTRATO}}", "CTR-2026-TESTGOLDEN")
                .replace("{{EMISOR_RAZON_SOCIAL}}", contratoDocumentoService.getEmisorRazonSocial())
                .replace("{{EMISOR_RUC}}", "10190981245")
                .replace("{{EMISOR_REPRESENTANTE}}", "Edward Fernando Castillo Robles")
                .replace("{{EMISOR_PARTIDA}}", "Oficina Registral – Sede Nº V Trujillo")
                .replace("{{FRAGMENTO_CLIENTE}}", fragmentoClienteEsperado)
                .replace("{{CIUDAD_CONTRATO}}", sucursalTrujillo.getNombre())
                .replace("{{FECHA_EMISION_FORMATEADA}}", fechaEsperada);

        // 5. Comparar normalizando espacios en blanco
        String normalizadoGenerado = textoCompletoGenerado.replaceAll("\\s+", " ").trim();
        String normalizadoGolden = goldenReemplazado.replaceAll("\\s+", " ").trim();

        assertThat(normalizadoGenerado).isEqualTo(normalizadoGolden);
    }

    @Test
    @DisplayName("Snapshot inmutable RUC 20: persiste columnas y lecturas posteriores no consultan SUNAT")
    void testSnapshotInmutableRuc20() {
        LocalDate hoy = LocalDate.now(clock);
        String ruc = "20987654321";
        String razonSocial = "COMERCIAL LOS ANDES S.A.C.";
        String repNombreOriginal = "CARLOS ALBERTO MENDOZA RUIZ";
        String repDniOriginal = "10000001";
        String repCargoOriginal = "GERENTE GENERAL";

        // Simular respuesta inicial de SUNAT
        SunatRucDTO dtoOriginal = SunatRucDTO.builder()
                .ruc(ruc)
                .razonSocial(razonSocial)
                .direccion("Av. Industrial 456, Trujillo")
                .representanteNombre(repNombreOriginal)
                .representanteDocumento(repDniOriginal)
                .representanteCargo(repCargoOriginal)
                .estado("ACTIVO")
                .condicion("HABIDO")
                .activo(true)
                .habido(true)
                .build();
        when(sunatClient.buscarPorRuc(ruc)).thenReturn(Optional.of(dtoOriginal));

        PrestamoRegistroDTO registroDTO = PrestamoRegistroDTO.builder()
                .dni(ruc)
                .nombreCliente(razonSocial)
                .monto(new BigDecimal("5000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamoGenerado = prestamoService.generarContrato(registroDTO, "asesor.trujillo");
        assertThat(prestamoGenerado).isNotNull();

        // Verificar columnas snapshot en base de datos
        Prestamo enDb = prestamoRepository.findById(prestamoGenerado.getId()).orElseThrow();
        assertThat(enDb.getEmpresaRuc()).isEqualTo(ruc);
        assertThat(enDb.getEmpresaRazonSocial()).isEqualTo(razonSocial);
        assertThat(enDb.getEmpresaRepresentanteNombre()).isEqualTo(repNombreOriginal);
        assertThat(enDb.getEmpresaRepresentanteDocumento()).isEqualTo(repDniOriginal);
        assertThat(enDb.getEmpresaRepresentanteCargo()).isEqualTo(repCargoOriginal);

        // CAMBIAR de verdad la respuesta simulada de SUNAT o hacer que falle
        SunatRucDTO dtoModificado = SunatRucDTO.builder()
                .ruc(ruc)
                .razonSocial("NOMBRE CAMBIADO EN SUNAT S.A.C.")
                .direccion("Direccion Cambiada")
                .representanteNombre("MARIA ELENA VARGAS SOTO")
                .representanteDocumento("99999999")
                .representanteCargo("ADMINISTRADOR")
                .estado("ACTIVO")
                .condicion("HABIDO")
                .activo(true)
                .habido(true)
                .build();
        when(sunatClient.buscarPorRuc(ruc)).thenReturn(Optional.of(dtoModificado));

        // Al generar el texto del contrato sobre el préstamo persistido, NO debe cambiar (usa snapshot inmutable)
        ContratoDocumentoService.ContratoTextoDTO textoDTO = contratoDocumentoService.generarTextoContrato(enDb);
        assertThat(textoDTO.textoContrato()).contains(razonSocial);
        assertThat(textoDTO.textoContrato()).contains(repNombreOriginal);
        assertThat(textoDTO.textoContrato()).contains(repDniOriginal);
        assertThat(textoDTO.textoContrato()).doesNotContain("MARIA ELENA VARGAS SOTO");
        assertThat(textoDTO.textoContrato()).doesNotContain("NOMBRE CAMBIADO EN SUNAT");
    }

    @Test
    @DisplayName("RUC 20 sin snapshot: debe lanzar NegocioException clara")
    void testRuc20SinSnapshot_lanzaNegocioException() {
        Cliente clienteEmpresa = clienteRepository.findByDni("20111222333").orElseGet(() -> {
            Cliente nuevo = Cliente.builder()
                    .dni("20111222333")
                    .nombreCompleto("EMPRESA SIN SNAPSHOT S.A.C.")
                    .estado(EstadoCliente.ACTIVO)
                    .build();
            return clienteRepository.save(nuevo);
        });

        // Préstamo creado con RUC 20 pero sin campos de representante legal (ej. pre-V7)
        Prestamo prestamoSinSnapshot = Prestamo.builder()
                .numeroContrato("CTR-2026-SINSNAP")
                .cliente(clienteEmpresa)
                .sucursal(sucursalTrujillo)
                .asesor(asesorTrujillo)
                .capital(new BigDecimal("4000.00"))
                .plazoMeses(6)
                .tea(new BigDecimal("0.3000"))
                .tem(new BigDecimal("0.0221"))
                .cuotaFija(new BigDecimal("719.53"))
                .fechaPrestamo(LocalDate.now(clock))
                .estado(EstadoPrestamo.EN_ESPERA)
                .impreso(false)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                contratoDocumentoService.generarTextoContrato(prestamoSinSnapshot)
        );

        assertThat(ex.getMessage()).isEqualTo(
                "Este contrato no tiene registrados los datos del representante legal; debe generarse un contrato nuevo."
        );
    }

    @Test
    @DisplayName("SUNAT: falla si la empresa figura inactiva o no habida")
    void testSunatNoActivoOHabido_lanzaNegocioException() {
        LocalDate hoy = LocalDate.now(clock);
        String ruc = "20123456780";
        SunatRucDTO inactivo = SunatRucDTO.builder()
                .ruc(ruc)
                .razonSocial("EMPRESA DEUDORA S.A.C.")
                .estado("BAJA DEFINITIVA")
                .condicion("NO HABIDO")
                .activo(false)
                .habido(false)
                .representanteNombre("JUAN PEREZ")
                .representanteDocumento("10203040")
                .representanteCargo("GERENTE GENERAL")
                .build();
        when(sunatClient.buscarPorRuc(ruc)).thenReturn(Optional.of(inactivo));

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(ruc)
                .nombreCliente("EMPRESA DEUDORA S.A.C.")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.generarContrato(dto, "asesor.trujillo")
        );

        assertThat(ex.getMessage()).contains("en SUNAT (debe estar ACTIVO)");
    }

    @Test
    @DisplayName("SUNAT: falla si la empresa no cuenta con representante legal completo")
    void testSunatRepresentanteIncompleto_lanzaNegocioException() {
        LocalDate hoy = LocalDate.now(clock);
        String ruc = "20123456781";
        SunatRucDTO sinRep = SunatRucDTO.builder()
                .ruc(ruc)
                .razonSocial("EMPRESA SIN REP S.A.C.")
                .estado("ACTIVO")
                .condicion("HABIDO")
                .activo(true)
                .habido(true)
                .representanteNombre("") // Falta representante
                .representanteDocumento("")
                .representanteCargo("")
                .build();
        when(sunatClient.buscarPorRuc(ruc)).thenReturn(Optional.of(sinRep));

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(ruc)
                .nombreCliente("EMPRESA SIN REP S.A.C.")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.generarContrato(dto, "asesor.trujillo")
        );

        assertThat(ex.getMessage()).isEqualTo("La empresa no cuenta con información completa del representante legal en SUNAT.");
    }

    @Test
    @DisplayName("RUC con prefijo inválido (no 10 ni 20) debe ser rechazado")
    void testRucPrefijoInvalido_lanzaNegocioException() {
        LocalDate hoy = LocalDate.now(clock);
        String rucInvalido = "15987654321";

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(rucInvalido)
                .nombreCliente("CLIENTE INVALIDO")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.generarContrato(dto, "asesor.trujillo")
        );

        assertThat(ex.getMessage()).isEqualTo(
                "El RUC debe comenzar con 10 (persona natural con negocio) o 20 (persona jurídica/empresa)."
        );
    }

    @Test
    @DisplayName("RUC 10: persona natural con negocio no genera snapshot de empresa y deriva DNI en encabezado")
    void testRuc10PersonaNaturalConNegocio() {
        LocalDate hoy = LocalDate.now(clock);
        String ruc10 = "10456789012";
        String nombre = "Pedro Alva Mendoza";

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(ruc10)
                .nombreCliente(nombre)
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();

        // Verificar que NO se pobló snapshot de empresa
        Prestamo enDb = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        assertThat(enDb.getEmpresaRazonSocial()).isNull();
        assertThat(enDb.getEmpresaRepresentanteNombre()).isNull();

        // Verificar encabezado generado
        ContratoDocumentoService.ContratoTextoDTO textoDTO = contratoDocumentoService.generarTextoContrato(enDb);
        String dniDerivado = "45678901";
        assertThat(textoDTO.textoContrato()).contains(
                "EL CLIENTE Pedro Alva Mendoza, identificado con DNI Nº " + dniDerivado + " y con RUC Nº " + ruc10 + "."
        );
    }

    @Test
    @DisplayName("DNI 8 dígitos: encabezado de contrato para persona natural")
    void testDni8DigitosEncabezado() {
        LocalDate hoy = LocalDate.now(clock);
        String dni = "10000008";
        String nombre = "Ángel Gabriel Peña Morales";

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(dni)
                .nombreCliente(nombre)
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");
        Prestamo enDb = prestamoRepository.findById(prestamo.getId()).orElseThrow();

        ContratoDocumentoService.ContratoTextoDTO textoDTO = contratoDocumentoService.generarTextoContrato(enDb);
        assertThat(textoDTO.textoContrato()).contains(
                "EL CLIENTE Ángel Gabriel Peña Morales, con DNI Nº 10000008."
        );
    }

    @Test
    @DisplayName("Saldo inicial encadenado: cuota 1 saldoInicial == capital, y cuota[i].saldoInicial == cuota[i-1].saldoCapital")
    void testSaldoInicialEncadenado() {
        LocalDate hoy = LocalDate.now(clock);
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000009")
                .nombreCliente("Rosa María Flores Benítez")
                .monto(new BigDecimal("3000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        Prestamo prestamo = prestamoService.generarContrato(dto, "asesor.trujillo");
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());

        assertThat(cuotas).hasSize(6);

        BigDecimal saldoEsperado = prestamo.getCapital();
        for (int i = 0; i < cuotas.size(); i++) {
            Cuota cuota = cuotas.get(i);
            BigDecimal saldoInicialCuota = cuota.getSaldoCapital().add(cuota.getCapital());
            assertThat(saldoInicialCuota).isEqualByComparingTo(saldoEsperado);
            saldoEsperado = cuota.getSaldoCapital();
        }

        // El saldo final de la última cuota debe ser 0.00
        assertThat(cuotas.get(cuotas.size() - 1).getSaldoCapital())
                .isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
