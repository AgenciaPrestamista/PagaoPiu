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
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.service.CronogramaCalculator;
import com.example.pagaopiu.service.EstadoService;
import com.example.pagaopiu.service.PrestamoService;
import com.example.pagaopiu.service.ReniecClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            EstadoService estadoService) {
        this(prestamoRepository, cuotaRepository, clienteRepository, usuarioRepository, reniecClient, clienteRegistroHelper, clock, estadoService, new CronogramaCalculator());
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
        this(prestamoRepository, cuotaRepository, clienteRepository, usuarioRepository, reniecClient, clienteRegistroHelper, clock, null, cronogramaCalculator);
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
            CronogramaCalculator cronogramaCalculator) {
        this.prestamoRepository = prestamoRepository;
        this.cuotaRepository = cuotaRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.reniecClient = reniecClient;
        this.clienteRegistroHelper = clienteRegistroHelper;
        this.clock = clock;
        this.estadoService = estadoService;
        this.cronogramaCalculator = cronogramaCalculator;
    }

    @Override
    @Transactional
    public Prestamo registrarPrestamo(PrestamoRegistroDTO dto, String usernameAsesor) {
        // ── R1: Campos obligatorios ──
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

        // ── R2: DNI en padrón, vigente, no fallecido, nacionalidad peruana ──
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni(dni);
        if (padronOpt.isEmpty()) {
            throw new NegocioException("El DNI no se encuentra registrado en el padrón de RENIEC.");
        }
        ReniecPadron padron = padronOpt.get();

        if (!padron.isDniVigente()) {
            throw new NegocioException("El DNI no se encuentra vigente en RENIEC.");
        }
        if (padron.isFallecido()) {
            throw new NegocioException("La persona se encuentra registrada como fallecida en RENIEC.");
        }
        if (!"PERUANA".equalsIgnoreCase(padron.getNacionalidad())) {
            throw new NegocioException("La persona no posee nacionalidad peruana.");
        }

        // ── R3: Nombre exactamente igual al del padrón (sensible a mayúsculas y tildes, con NFC) ──
        String nombreDtoNormalizado = Normalizer.normalize(dto.getNombreCliente().trim(), Normalizer.Form.NFC);
        String nombrePadronNormalizado = Normalizer.normalize(padron.getNombreCompleto().trim(), Normalizer.Form.NFC);

        if (!nombreDtoNormalizado.equals(nombrePadronNormalizado)) {
            throw new NegocioException("El nombre no coincide exactamente con el registrado en RENIEC.");
        }

        // ── R4: Plazo entre 1 y 60 meses ──
        if (dto.getPlazoMeses() < 1 || dto.getPlazoMeses() > 60) {
            throw new NegocioException("El plazo debe estar entre 1 y 60 meses.");
        }

        // ── R5: Monto entre mínimo y 10000 ──
        if (dto.getMonto().compareTo(montoMinimo) < 0 || dto.getMonto().compareTo(montoMaximo) > 0) {
            throw new NegocioException(String.format("El monto debe estar entre S/ %.2f y S/ %.2f.", montoMinimo, montoMaximo));
        }

        // ── R8: Fecha <= hoy (usa Clock) ──
        LocalDate hoy = LocalDate.now(clock);
        if (dto.getFechaPrestamo().isAfter(hoy)) {
            throw new NegocioException("La fecha del préstamo no puede ser futura.");
        }

        // ── Validación rango de edad (18 a 80 años) ──
        if (dto.getFechaNacimiento() != null) {
            if (dto.getFechaNacimiento().isAfter(hoy)) {
                throw new NegocioException("La fecha de nacimiento no puede ser futura.");
            }
            int edad = Period.between(dto.getFechaNacimiento(), hoy).getYears();
            if (edad < 18) {
                throw new NegocioException("El cliente debe ser mayor de edad (mínimo 18 años) para ser registrado en el sistema.");
            }
            if (edad > 80) {
                throw new NegocioException("La edad máxima permitida para solicitar un préstamo es de 80 años (el cliente tiene " + edad + " años).");
            }
        }

        // ── Obtener o crear Cliente con bloqueo pesimista ──
        Cliente cliente;
        Optional<Cliente> optCliente = clienteRepository.findByDniWithLock(dni);
        if (optCliente.isPresent()) {
            cliente = optCliente.get();
        } else {
            try {
                cliente = clienteRegistroHelper.insertarNuevoCliente(dni, padron.getNombreCompleto());
                cliente = clienteRepository.findByDniWithLock(dni).orElse(cliente);
            } catch (DataIntegrityViolationException ex) {
                // En carrera concurrente, otra transacción insertó el cliente primero; reintentamos con bloqueo
                cliente = clienteRepository.findByDniWithLock(dni)
                        .orElseThrow(() -> new NegocioException("Error al bloquear el cliente tras inserción concurrente."));
            }
        }

        // ── R7: Cliente MOROSO o EXTRAJUDICIAL no puede pedir préstamo ──
        if (estadoService != null) {
            estadoService.recalcularCliente(cliente);
        }
        if (cliente.getEstado() == EstadoCliente.MOROSO || cliente.getEstado() == EstadoCliente.EXTRAJUDICIAL) {
            throw new NegocioException("El cliente tiene estado " + cliente.getEstado() + " y no puede solicitar préstamos.");
        }

        // ── R6: Tope máximo de deuda 10,000 (suma de capital de cuotas pendientes de préstamos no cancelados) ──
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

        // ── Obtener Asesor y Sucursal autenticados ──
        Usuario asesor = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario asesor no encontrado: " + usernameAsesor));

        if (asesor.getSucursal() == null) {
            throw new NegocioException("El asesor autenticado no tiene una sucursal asignada.");
        }

        // ── R9: Cálculo de cronograma francés y persistencia atómica ──
        BigDecimal tea = teaDefecto;
        BigDecimal tem = cronogramaCalculator.calcularTem(tea);
        BigDecimal cuotaFija = cronogramaCalculator.calcularCuotaFija(montoNuevo, tem, dto.getPlazoMeses());

        Prestamo prestamo = Prestamo.builder()
                .cliente(cliente)
                .sucursal(asesor.getSucursal())
                .asesor(asesor)
                .fechaPrestamo(dto.getFechaPrestamo())
                .capital(montoNuevo)
                .plazoMeses(dto.getPlazoMeses())
                .tea(tea)
                .tem(tem)
                .cuotaFija(cuotaFija)
                .estado(EstadoPrestamo.VIGENTE)
                .build();

        prestamo = prestamoRepository.save(prestamo);

        List<CuotaDetalle> detalles = cronogramaCalculator.generarCronograma(
                prestamo.getCapital(),
                prestamo.getPlazoMeses(),
                prestamo.getFechaPrestamo(),
                prestamo.getTea()
        );

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

        return prestamoRepository.findById(prestamo.getId()).orElse(prestamo);
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
}
