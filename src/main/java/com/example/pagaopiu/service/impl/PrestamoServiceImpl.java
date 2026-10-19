package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.CuotaDetalle;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.model.enums.Rol;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.service.ContratoFileStorageService;
import com.example.pagaopiu.service.CronogramaCalculator;
import com.example.pagaopiu.service.EstadoService;
import com.example.pagaopiu.service.PrestamoService;
import com.example.pagaopiu.service.ReniecClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PrestamoServiceImpl implements PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final CuotaRepository cuotaRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReniecClient reniecClient;
    private final ClienteRegistroHelper clienteRegistroHelper;
    private final Clock clock;
    private final CronogramaCalculator cronogramaCalculator;
    private final ContratoFileStorageService contratoFileStorageService;

    @Value("${app.prestamo.monto-minimo:1.00}")
    private BigDecimal montoMinimo = new BigDecimal("1.00");

    @Value("${app.prestamo.monto-maximo:10000.00}")
    private BigDecimal montoMaximo = new BigDecimal("10000.00");

    @Value("${app.prestamo.tea:0.30}")
    private BigDecimal teaDefecto = new BigDecimal("0.30");

    private final EstadoService estadoService;

    @org.springframework.beans.factory.annotation.Autowired
    public PrestamoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            ReniecClient reniecClient,
            ClienteRegistroHelper clienteRegistroHelper,
            Clock clock,
            EstadoService estadoService,
            ContratoFileStorageService contratoFileStorageService) {
        this(prestamoRepository, cuotaRepository, clienteRepository, usuarioRepository, reniecClient, clienteRegistroHelper, clock, estadoService, new CronogramaCalculator(), contratoFileStorageService);
    }

    public PrestamoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            ReniecClient reniecClient,
            ClienteRegistroHelper clienteRegistroHelper,
            Clock clock,
            CronogramaCalculator cronogramaCalculator) {
        this(prestamoRepository, cuotaRepository, clienteRepository, usuarioRepository, reniecClient, clienteRegistroHelper, clock, null, cronogramaCalculator, null);
    }

    public PrestamoServiceImpl(
            PrestamoRepository prestamoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            ReniecClient reniecClient,
            ClienteRegistroHelper clienteRegistroHelper,
            Clock clock,
            EstadoService estadoService,
            CronogramaCalculator cronogramaCalculator,
            ContratoFileStorageService contratoFileStorageService) {
        this.prestamoRepository = prestamoRepository;
        this.cuotaRepository = cuotaRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.reniecClient = reniecClient;
        this.clienteRegistroHelper = clienteRegistroHelper;
        this.clock = clock;
        this.estadoService = estadoService;
        this.cronogramaCalculator = cronogramaCalculator;
        this.contratoFileStorageService = contratoFileStorageService;
    }

    private record ContextoPrestamo(Cliente cliente, Usuario asesor, BigDecimal monto, BigDecimal tea, BigDecimal tem, BigDecimal cuotaFija, List<CuotaDetalle> cronograma, LocalDate fechaPrestamo, Integer plazoMeses) {}

    private ContextoPrestamo validarSolicitudYBloquearCliente(PrestamoRegistroDTO dto, String usernameAsesor, boolean permitirEnEsperaExistente) {
        if (dto == null) {
            throw new NegocioException("Los datos de la solicitud son obligatorios.");
        }
        if (dto.getDni() == null || dto.getDni().trim().isEmpty()) {
            throw new NegocioException("El DNI es obligatorio.");
        }
        if (dto.getNombreCliente() == null || dto.getNombreCliente().trim().isEmpty()) {
            throw new NegocioException("El nombre del cliente es obligatorio.");
        }
        if (dto.getPlazoMeses() == null) {
            throw new NegocioException("El plazo en meses es obligatorio.");
        }
        if (dto.getMonto() == null) {
            throw new NegocioException("El monto del préstamo es obligatorio.");
        }
        if (dto.getFechaPrestamo() == null) {
            throw new NegocioException("La fecha del préstamo es obligatoria.");
        }

        String dni = dto.getDni().trim();
        boolean esRuc = dni.length() == 11;
        ReniecPadron padron = null;

        if (esRuc) {
            if (!dni.matches("\\d{11}")) {
                throw new NegocioException("El RUC debe contener exactamente 11 dígitos numéricos.");
            }
        } else {
            Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni(dni);
            if (padronOpt.isEmpty()) {
                throw new NegocioException("El DNI no se encuentra registrado en el padrón de RENIEC.");
            }
            padron = padronOpt.get();

            if (!padron.isDniVigente()) {
                throw new NegocioException("El DNI no se encuentra vigente en RENIEC.");
            }
            if (padron.isFallecido()) {
                throw new NegocioException("La persona se encuentra registrada como fallecida en RENIEC.");
            }
            if (!"PERUANA".equalsIgnoreCase(padron.getNacionalidad())) {
                throw new NegocioException("La persona no posee nacionalidad peruana.");
            }

            String nombreDtoNormalizado = Normalizer.normalize(dto.getNombreCliente().trim(), Normalizer.Form.NFC);
            String nombrePadronNormalizado = Normalizer.normalize(padron.getNombreCompleto().trim(), Normalizer.Form.NFC);
            if (!nombreDtoNormalizado.equals(nombrePadronNormalizado)) {
                throw new NegocioException("El nombre no coincide exactamente con el registrado en RENIEC.");
            }
        }

        if (dto.getPlazoMeses() < 1 || dto.getPlazoMeses() > 60) {
            throw new NegocioException("El plazo debe estar entre 1 y 60 meses.");
        }

        if (dto.getMonto().compareTo(montoMinimo) < 0 || dto.getMonto().compareTo(montoMaximo) > 0) {
            throw new NegocioException(String.format("El monto debe estar entre S/ %.2f y S/ %.2f.", montoMinimo, montoMaximo));
        }

        LocalDate hoy = LocalDate.now(clock);
        if (dto.getFechaPrestamo().isAfter(hoy)) {
            throw new NegocioException("La fecha del préstamo no puede ser futura.");
        }

        if (dto.getFechaNacimiento() != null) {
            if (dto.getFechaNacimiento().isAfter(hoy)) {
                throw new NegocioException(esRuc ? "La fecha de constitución no puede ser futura." : "La fecha de nacimiento no puede ser futura.");
            }
            if (!esRuc) {
                int edad = Period.between(dto.getFechaNacimiento(), hoy).getYears();
                if (edad < 18) {
                    throw new NegocioException("El cliente debe ser mayor de edad (mínimo 18 años) para ser registrado en el sistema.");
                }
                if (edad > 80) {
                    throw new NegocioException("La edad máxima permitida para solicitar un préstamo es de 80 años (el cliente tiene " + edad + " años).");
                }
            }
        }

        Cliente cliente;
        Optional<Cliente> optCliente = clienteRepository.findByDniWithLock(dni);
        if (optCliente.isPresent()) {
            cliente = optCliente.get();
        } else {
            String nombreAGuardar = esRuc ? dto.getNombreCliente().trim() : padron.getNombreCompleto();
            try {
                cliente = clienteRegistroHelper.insertarNuevoCliente(dni, nombreAGuardar);
                cliente = clienteRepository.findByDniWithLock(dni).orElse(cliente);
            } catch (DataIntegrityViolationException ex) {
                cliente = clienteRepository.findByDniWithLock(dni)
                        .orElseThrow(() -> new NegocioException("Error al bloquear el cliente tras inserción concurrente."));
            }
        }

        // Auto-corrección: Anular contratos EN_ESPERA de este cliente que hayan vencido (fechaPrestamo < hoy)
        List<Prestamo> enEsperaCliente = prestamoRepository.findByClienteId(cliente.getId()).stream()
                .filter(p -> p.getEstado() == EstadoPrestamo.EN_ESPERA && p.getFechaPrestamo().isBefore(hoy))
                .toList();
        for (Prestamo pVencido : enEsperaCliente) {
            pVencido.setEstado(EstadoPrestamo.ANULADO);
            pVencido.setMotivoAnulacion("Vencimiento automático: contrato no formalizado en la fecha de emisión (" + pVencido.getFechaPrestamo() + ")");
            prestamoRepository.save(pVencido);
        }

        if (estadoService != null) {
            estadoService.recalcularCliente(cliente);
        }
        if (cliente.getEstado() == EstadoCliente.MOROSO || cliente.getEstado() == EstadoCliente.EXTRAJUDICIAL) {
            throw new NegocioException("El cliente tiene estado " + cliente.getEstado() + " y no puede solicitar préstamos.");
        }

        if (!permitirEnEsperaExistente && prestamoRepository.existsByClienteIdAndEstado(cliente.getId(), EstadoPrestamo.EN_ESPERA)) {
            throw new NegocioException("El cliente ya tiene un contrato pendiente de firma en estado EN_ESPERA. Debe formalizarlo o cancelarlo antes de generar uno nuevo.");
        }

        BigDecimal saldoPendiente = prestamoRepository.findSaldoCapitalPendienteByClienteId(cliente.getId());
        if (saldoPendiente == null) {
            saldoPendiente = BigDecimal.ZERO;
        }

        BigDecimal montoNuevo = dto.getMonto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal nuevoSaldoTotal = saldoPendiente.add(montoNuevo);
        if (nuevoSaldoTotal.compareTo(montoMaximo) > 0) {
            BigDecimal cupoRestante = montoMaximo.subtract(saldoPendiente);
            if (cupoRestante.compareTo(BigDecimal.ZERO) < 0) {
                cupoRestante = BigDecimal.ZERO;
            }
            throw new NegocioException(String.format(
                    "El cliente excede el tope de deuda de S/ %.2f. Saldo pendiente actual: S/ %.2f. Cupo restante disponible: S/ %.2f.",
                    montoMaximo, saldoPendiente, cupoRestante));
        }

        Usuario asesor = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario asesor no encontrado: " + usernameAsesor));
        if (asesor.getSucursal() == null) {
            throw new NegocioException("El asesor autenticado no tiene una sucursal asignada.");
        }

        BigDecimal tea = teaDefecto;
        BigDecimal tem = cronogramaCalculator.calcularTem(tea);
        BigDecimal cuotaFija = cronogramaCalculator.calcularCuotaFija(montoNuevo, tem, dto.getPlazoMeses());
        List<CuotaDetalle> detalles = cronogramaCalculator.generarCronograma(montoNuevo, dto.getPlazoMeses(), dto.getFechaPrestamo(), tea);

        return new ContextoPrestamo(cliente, asesor, montoNuevo, tea, tem, cuotaFija, detalles, dto.getFechaPrestamo(), dto.getPlazoMeses());
    }

    private String generarNumeroContratoCorrelativo() {
        Long seq = prestamoRepository.getNextNumeroContratoSeq();
        int anio = LocalDate.now(clock).getYear();
        return String.format("CTR-%d-%06d", anio, seq);
    }

     @Override
     @Transactional
     public Prestamo registrarPrestamo(PrestamoRegistroDTO dto, String usernameAsesor) {
        ContextoPrestamo ctx = validarSolicitudYBloquearCliente(dto, usernameAsesor, true);
        String numeroContrato = generarNumeroContratoCorrelativo();

        Prestamo prestamo = Prestamo.builder()
                .cliente(ctx.cliente())
                .sucursal(ctx.asesor().getSucursal())
                .asesor(ctx.asesor())
                .fechaPrestamo(ctx.fechaPrestamo())
                .capital(ctx.monto())
                .plazoMeses(ctx.plazoMeses())
                .tea(ctx.tea())
                .tem(ctx.tem())
                .cuotaFija(ctx.cuotaFija())
                .estado(EstadoPrestamo.VIGENTE)
                .impreso(true)
                .fechaImpresion(LocalDateTime.now(clock))
                .numeroContrato(numeroContrato)
                .build();

        prestamo = prestamoRepository.save(prestamo);
        guardarCuotas(prestamo, ctx.cronograma());

        if (estadoService != null) {
            estadoService.recalcularCliente(ctx.cliente());
        }
        return prestamoRepository.findById(prestamo.getId()).orElse(prestamo);
     }

    @Override
    @Transactional
    public Prestamo generarContrato(PrestamoRegistroDTO dto, String usernameAsesor) {
        LocalDate hoy = LocalDate.now(clock);
        if (dto != null && dto.getFechaPrestamo() != null && !dto.getFechaPrestamo().isEqual(hoy)) {
            throw new NegocioException("La fecha de emisión del contrato debe ser exactamente la fecha actual del sistema.");
        }

        ContextoPrestamo ctx = validarSolicitudYBloquearCliente(dto, usernameAsesor, false);
        String numeroContrato = generarNumeroContratoCorrelativo();

        Prestamo prestamo = Prestamo.builder()
                .cliente(ctx.cliente())
                .sucursal(ctx.asesor().getSucursal())
                .asesor(ctx.asesor())
                .fechaPrestamo(ctx.fechaPrestamo())
                .capital(ctx.monto())
                .plazoMeses(ctx.plazoMeses())
                .tea(ctx.tea())
                .tem(ctx.tem())
                .cuotaFija(ctx.cuotaFija())
                .estado(EstadoPrestamo.EN_ESPERA)
                .impreso(false)
                .numeroContrato(numeroContrato)
                .build();

        prestamo = prestamoRepository.save(prestamo);
        guardarCuotas(prestamo, ctx.cronograma());
        return prestamoRepository.findById(prestamo.getId()).orElse(prestamo);
    }

    @Override
    @Transactional
    public Prestamo marcarImpreso(Long prestamoId, String usernameAsesor) {
        Prestamo prestamo = prestamoRepository.findByIdWithLock(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getEstado() != EstadoPrestamo.EN_ESPERA) {
            throw new NegocioException("Solo se puede imprimir o reimprimir un contrato en estado EN_ESPERA.");
        }
        validarSucursalAsesor(prestamo, usernameAsesor);

        prestamo.setImpreso(true);
        prestamo.setFechaImpresion(LocalDateTime.now(clock));
        return prestamoRepository.save(prestamo);
    }

    @Override
    @Transactional
    public Prestamo cancelarPrestamo(Long prestamoId, String motivo, String usernameAsesor) {
        Prestamo prestamo = prestamoRepository.findByIdWithLock(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getEstado() != EstadoPrestamo.EN_ESPERA) {
            throw new NegocioException("Solo se pueden cancelar préstamos en estado EN_ESPERA.");
        }
        validarSucursalAsesor(prestamo, usernameAsesor);

        if (!Boolean.TRUE.equals(prestamo.getImpreso())) {
            cuotaRepository.deleteAll(cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamoId));
            prestamoRepository.delete(prestamo);
            return null;
        }

        if (motivo == null || motivo.trim().isEmpty()) {
            throw new NegocioException("El motivo de anulación es obligatorio tras haber impreso el contrato.");
        }

        prestamo.setEstado(EstadoPrestamo.ANULADO);
        prestamo.setMotivoAnulacion(motivo.trim());
        return prestamoRepository.save(prestamo);
    }

    @Override
    @Transactional
    public Prestamo subirContratoFirmado(Long prestamoId, String numeroContratoIngresado, MultipartFile archivo, String usernameAsesor) {
        if (numeroContratoIngresado == null || numeroContratoIngresado.trim().isEmpty()) {
            throw new NegocioException("Debe ingresar el número de contrato impreso.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("Debe adjuntar el archivo del contrato firmado.");
        }

        Prestamo prestamo = prestamoRepository.findByIdWithLock(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getEstado() != EstadoPrestamo.EN_ESPERA) {
            throw new NegocioException("El préstamo no se encuentra en estado EN_ESPERA.");
        }
        if (!Boolean.TRUE.equals(prestamo.getImpreso())) {
            throw new NegocioException("El contrato debe ser impreso antes de subirlo firmado.");
        }

        LocalDate hoy = LocalDate.now(clock);
        if (prestamo.getFechaPrestamo().isBefore(hoy)) {
            throw new NegocioException("El contrato ha vencido porque no fue formalizado en la fecha de su emisión (" + prestamo.getFechaPrestamo() + "). Debe generarse un nuevo contrato.");
        }

        Usuario asesor = validarSucursalAsesor(prestamo, usernameAsesor);

        String numNormalizado = numeroContratoIngresado.trim().toUpperCase();
        if (prestamo.getNumeroContrato() == null || !prestamo.getNumeroContrato().equalsIgnoreCase(numNormalizado)) {
            throw new NegocioException(String.format("El número de contrato ingresado (%s) no coincide con el asignado al préstamo (%s).",
                    numNormalizado, prestamo.getNumeroContrato()));
        }

        ContratoFileStorageService.ContratoFileResult guardado = contratoFileStorageService.almacenarArchivo(archivo);

        if (prestamoRepository.existsByContratoHash(guardado.hashSha256())) {
            throw new NegocioException("El archivo ya fue utilizado como contrato firmado en otro préstamo.");
        }

        prestamo.setEstado(EstadoPrestamo.VIGENTE);
        prestamo.setContratoNombreOriginal(guardado.nombreOriginal());
        prestamo.setContratoRuta(guardado.rutaRelativa());
        prestamo.setContratoHash(guardado.hashSha256());
        prestamo.setContratoContentType(guardado.contentType());
        prestamo.setContratoSubidoPor(asesor);
        prestamo.setContratoFechaSubida(LocalDateTime.now(clock));

        try {
            prestamo = prestamoRepository.saveAndFlush(prestamo);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uq_prestamo_contrato_hash")) {
                throw new NegocioException("El archivo ya fue utilizado como contrato firmado en otro préstamo.");
            }
            throw ex;
        }

        if (estadoService != null) {
            estadoService.recalcularCliente(prestamo.getCliente());
        }

        return prestamo;
    }

    @Override
    @Transactional(readOnly = true)
    public Resource descargarContratoFirmado(Long prestamoId, String username) {
        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + prestamoId));

        if (prestamo.getContratoRuta() == null || prestamo.getContratoRuta().isBlank()) {
            throw new NegocioException("El préstamo no registra un contrato firmado cargado.");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + username));

        // El GERENTE tiene alcance global corporativo (su sucursal_id es null).
        // El ASESOR solo puede descargar expedientes de su sucursal asignada.
        if (usuario.getRol() == Rol.ASESOR) {
            if (usuario.getSucursal() == null || !usuario.getSucursal().getId().equals(prestamo.getSucursal().getId())) {
                throw new NegocioException("No tiene permisos para descargar contratos de una sucursal distinta a la suya.");
            }
        }

        return contratoFileStorageService.cargarComoRecurso(prestamo.getContratoRuta());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prestamo> listarContratosEnEspera(String usernameAsesor) {
        Usuario usuario = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + usernameAsesor));

        if (usuario.getRol() == Rol.GERENTE || usuario.getSucursal() == null) {
            return prestamoRepository.findByEstadoOrderByIdDesc(EstadoPrestamo.EN_ESPERA);
        }
        return prestamoRepository.findBySucursalIdAndEstadoOrderByIdDesc(usuario.getSucursal().getId(), EstadoPrestamo.EN_ESPERA);
    }

    @Override
    @Scheduled(cron = "${app.cron.vencimiento-contratos:0 5 0 * * *}", zone = "America/Lima")
    @Transactional
    public void vencerContratosEnEspera() {
        LocalDate hoy = LocalDate.now(clock);
        List<Prestamo> contratosVencidos = prestamoRepository.findByEstadoAndFechaPrestamoBefore(EstadoPrestamo.EN_ESPERA, hoy);
        for (Prestamo v : contratosVencidos) {
            Prestamo p = prestamoRepository.findByIdWithLock(v.getId()).orElse(null);
            if (p != null && p.getEstado() == EstadoPrestamo.EN_ESPERA && p.getFechaPrestamo().isBefore(hoy)) {
                p.setEstado(EstadoPrestamo.ANULADO);
                p.setMotivoAnulacion("Vencimiento automático: contrato no formalizado en la fecha de emisión (" + p.getFechaPrestamo() + ")");
                prestamoRepository.save(p);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void alIniciarAplicacion() {
        vencerContratosEnEspera();
    }

    private Usuario validarSucursalAsesor(Prestamo prestamo, String usernameAsesor) {
        Usuario asesor = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario asesor no encontrado: " + usernameAsesor));
        // El GERENTE tiene alcance global corporativo (su sucursal_id es null).
        // Los ASESORES están asignados a una sucursal específica y solo pueden operar contratos de su agencia.
        if (asesor.getRol() == Rol.ASESOR) {
            if (asesor.getSucursal() == null || !asesor.getSucursal().getId().equals(prestamo.getSucursal().getId())) {
                throw new NegocioException("Solo los asesores de la sucursal del préstamo (" + prestamo.getSucursal().getNombre() + ") pueden operar sobre este contrato.");
            }
        }
        return asesor;
    }

    private void guardarCuotas(Prestamo prestamo, List<CuotaDetalle> detalles) {
        List<Cuota> cuotas = new ArrayList<>(detalles.size());
        for (CuotaDetalle det : detalles) {
            Cuota cuota = Cuota.builder()
                    .prestamo(prestamo)
                    .numero(det.numero())
                    .fechaVencimiento(det.fechaVencimiento())
                    .capital(det.capital())
                    .interes(det.interes())
                    .monto(det.monto())
                    .saldoCapital(det.saldoCapital())
                    .estado(EstadoCuota.PENDIENTE)
                    .build();
            cuotas.add(cuota);
        }
        cuotaRepository.saveAll(cuotas);
    }

    @Override
    @Transactional(readOnly = true)
    public Prestamo obtenerPrestamoPorId(Long id) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado con id: " + id));
        if (prestamo.getCliente() != null) {
            org.hibernate.Hibernate.initialize(prestamo.getCliente());
        }
        if (prestamo.getSucursal() != null) {
            org.hibernate.Hibernate.initialize(prestamo.getSucursal());
        }
        if (prestamo.getAsesor() != null) {
            org.hibernate.Hibernate.initialize(prestamo.getAsesor());
        }
        return prestamo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuota> obtenerCuotasPorPrestamoId(Long prestamoId) {
        return cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prestamo> obtenerPrestamosPorClienteDni(String dni) {
        if (dni == null || dni.isBlank()) {
            return java.util.Collections.emptyList();
        }
        List<Prestamo> lista = prestamoRepository.findByClienteDni(dni.trim());
        for (Prestamo p : lista) {
            if (p.getCliente() != null) org.hibernate.Hibernate.initialize(p.getCliente());
            if (p.getSucursal() != null) org.hibernate.Hibernate.initialize(p.getSucursal());
            if (p.getAsesor() != null) org.hibernate.Hibernate.initialize(p.getAsesor());
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prestamo> listarPrestamosRecientes() {
        List<Prestamo> lista = prestamoRepository.findAllOrderByFechaDesc();
        for (Prestamo p : lista) {
            if (p.getCliente() != null) org.hibernate.Hibernate.initialize(p.getCliente());
            if (p.getSucursal() != null) org.hibernate.Hibernate.initialize(p.getSucursal());
            if (p.getAsesor() != null) org.hibernate.Hibernate.initialize(p.getAsesor());
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public Prestamo obtenerContratoPorId(Long id, String username) {
        Prestamo prestamo = obtenerPrestamoPorId(id);
        validarSucursalAsesor(prestamo, username);
        return prestamo;
    }
}
