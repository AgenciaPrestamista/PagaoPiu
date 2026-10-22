package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.caja.CajaEstadoResponseDTO;
import com.example.pagaopiu.dto.caja.IncidenciaHermesDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesRequestDTO;
import com.example.pagaopiu.dto.caja.RecepcionHermesResponseDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.CajaSucursal;
import com.example.pagaopiu.model.entity.EntregaHermes;
import com.example.pagaopiu.model.entity.IncidenciaHermes;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.entity.Usuario;
import com.example.pagaopiu.model.enums.EstadoEntregaHermes;
import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
import com.example.pagaopiu.model.enums.Rol;
import com.example.pagaopiu.repository.CajaSucursalRepository;
import com.example.pagaopiu.repository.EntregaHermesRepository;
import com.example.pagaopiu.repository.IncidenciaHermesRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.service.CajaHermesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CajaHermesServiceImpl implements CajaHermesService {

    public static final BigDecimal MONTO_ESPERADO_HERMES = new BigDecimal("50000.00");

    private final CajaSucursalRepository cajaSucursalRepository;
    private final EntregaHermesRepository entregaHermesRepository;
    private final IncidenciaHermesRepository incidenciaHermesRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final Clock clock;
    private final PagoRepository pagoRepository;

    public CajaHermesServiceImpl(
            PagoRepository pagoRepository,
            CajaSucursalRepository cajaSucursalRepository,
            EntregaHermesRepository entregaHermesRepository,
            IncidenciaHermesRepository incidenciaHermesRepository,
            UsuarioRepository usuarioRepository,
            SucursalRepository sucursalRepository,
            Clock clock) {
        this.pagoRepository = pagoRepository;
        this.cajaSucursalRepository = cajaSucursalRepository;
        this.entregaHermesRepository = entregaHermesRepository;
        this.incidenciaHermesRepository = incidenciaHermesRepository;
        this.usuarioRepository = usuarioRepository;
        this.sucursalRepository = sucursalRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RecepcionHermesResponseDTO registrarRecepcionHermes(RecepcionHermesRequestDTO dto, String usernameAsesor) {
        return recibirEntregaHermes(dto, usernameAsesor);
    }

    @Override
    @Transactional
    public RecepcionHermesResponseDTO recibirEntregaHermes(RecepcionHermesRequestDTO dto, String usernameAsesor) {
        if (dto == null) {
            throw new NegocioException("Los datos de la entrega son obligatorios.");
        }
        if (dto.getCodigoRemesa() == null || dto.getCodigoRemesa().trim().isEmpty()) {
            throw new NegocioException("El código de remesa de un solo uso es obligatorio.");
        }
        String codigo = dto.getCodigoRemesa().trim();
        if (entregaHermesRepository.existsByCodigoRemesa(codigo)) {
            throw new NegocioException("El código de remesa ya ha sido utilizado previamente.");
        }

        if (dto.getMontoApto() == null || dto.getMontoApto().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El monto recibido apto no puede ser nulo ni negativo.");
        }
        BigDecimal montoApto = dto.getMontoApto().setScale(2, RoundingMode.HALF_UP);

        BigDecimal montoObservado = dto.getMontoObservado() != null
                ? dto.getMontoObservado().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (montoObservado.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El monto observado no puede ser negativo.");
        }

        BigDecimal montoTotal = montoApto.add(montoObservado);
        boolean hayDiferencia = (montoTotal.compareTo(MONTO_ESPERADO_HERMES) != 0);
        boolean hayObservado = (montoObservado.compareTo(BigDecimal.ZERO) > 0);

        if (hayDiferencia || hayObservado) {
            if (dto.getMotivo() == null || dto.getMotivo().trim().isEmpty()) {
                throw new NegocioException("Debe ingresar un motivo obligatorio por la diferencia o monto observado en la entrega.");
            }
        }

        Usuario usuario = usuarioRepository.findByUsername(usernameAsesor)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + usernameAsesor));
        Sucursal sucursal = usuario.getSucursal();
        if (sucursal == null) {
            throw new NegocioException("El usuario no tiene una sucursal asignada para recepcionar remesas.");
        }

        LocalDate hoy = LocalDate.now(clock);
        LocalDateTime ahora = LocalDateTime.now(clock);

        EstadoEntregaHermes estadoEntrega = (hayDiferencia || hayObservado)
                ? EstadoEntregaHermes.RECIBIDA_CON_OBSERVACIONES
                : EstadoEntregaHermes.RECIBIDA_CONFORME;

        // Apertura o actualización de Caja: solo el monto apto ingresa al saldo disponible
        CajaSucursal caja = cajaSucursalRepository.findBySucursalIdAndFechaWithLock(sucursal.getId(), hoy)
                .orElseGet(() -> CajaSucursal.builder()
                        .sucursal(sucursal)
                        .fecha(hoy)
                        .saldoDisponible(BigDecimal.ZERO)
                        .montoObservadoAcumulado(BigDecimal.ZERO)
                        .abierta(false)
                        .build());

        if (caja.isCerrada()) {
            throw new NegocioException("La caja de hoy ya fue cerrada; no se pueden registrar nuevas remesas.");
        }

        caja.setSaldoDisponible(caja.getSaldoDisponible().add(montoApto));
        caja.setMontoObservadoAcumulado(caja.getMontoObservadoAcumulado().add(montoObservado));
        caja.setAbierta(true);
        if (caja.getUsuarioApertura() == null) {
            caja.setUsuarioApertura(usuario);
            caja.setFechaApertura(ahora);
        }
        caja = cajaSucursalRepository.save(caja);

        // Registro de la Entrega Hermes
        EntregaHermes entrega = EntregaHermes.builder()
                .caja(caja)
                .sucursal(sucursal)
                .usuario(usuario)
                .fecha(hoy)
                .codigoRemesa(codigo)
                .montoEsperado(MONTO_ESPERADO_HERMES)
                .montoApto(montoApto)
                .montoObservado(montoObservado)
                .motivo(dto.getMotivo() != null ? dto.getMotivo().trim() : null)
                .estado(estadoEntrega)
                .fechaRecepcion(ahora)
                .build();
        entrega = entregaHermesRepository.save(entrega);

        Long incidenciaId = null;
        if (hayDiferencia || hayObservado) {
            BigDecimal diferencia = MONTO_ESPERADO_HERMES.subtract(montoTotal);
            IncidenciaHermes incidencia = IncidenciaHermes.builder()
                    .entrega(entrega)
                    .sucursal(sucursal)
                    .usuarioReporta(usuario)
                    .fecha(hoy)
                    .montoEsperado(MONTO_ESPERADO_HERMES)
                    .montoApto(montoApto)
                    .montoObservado(montoObservado)
                    .diferencia(diferencia)
                    .motivo(dto.getMotivo().trim())
                    .estado(EstadoIncidenciaHermes.ABIERTA)
                    .build();
            incidencia = incidenciaHermesRepository.save(incidencia);
            incidenciaId = incidencia.getId();
        }

        return RecepcionHermesResponseDTO.builder()
                .entregaId(entrega.getId())
                .estado(estadoEntrega)
                .montoEsperado(MONTO_ESPERADO_HERMES)
                .montoApto(montoApto)
                .montoObservado(montoObservado)
                .saldoCajaDisponible(caja.getSaldoDisponible())
                .incidenciaGenerada(incidenciaId != null)
                .incidenciaId(incidenciaId)
                .mensaje(estadoEntrega == EstadoEntregaHermes.RECIBIDA_CONFORME
                        ? "Remesa Hermes recibida conforme. Caja abierta con saldo disponible de S/ " + montoApto
                        : "Remesa recibida con observaciones. Caja abierta con S/ " + montoApto + " e incidencia ABIERTA registrada.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CajaEstadoResponseDTO obtenerEstadoCaja(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + username));
        if (usuario.getSucursal() == null) {
            return CajaEstadoResponseDTO.builder()
                    .sucursalId(null)
                    .sucursalNombre("Gerencia General (Global)")
                    .fecha(LocalDate.now(clock))
                    .saldoDisponible(BigDecimal.ZERO)
                    .montoObservadoAcumulado(BigDecimal.ZERO)
                    .abierta(true)
                    .usuarioApertura(null)
                    .build();
        }

        LocalDate hoy = LocalDate.now(clock);
        Optional<CajaSucursal> cajaOpt = cajaSucursalRepository.findBySucursalIdAndFecha(usuario.getSucursal().getId(), hoy);

        if (cajaOpt.isEmpty()) {
            return CajaEstadoResponseDTO.builder()
                    .sucursalId(usuario.getSucursal().getId())
                    .sucursalNombre(usuario.getSucursal().getNombre())
                    .fecha(hoy)
                    .saldoDisponible(BigDecimal.ZERO)
                    .montoObservadoAcumulado(BigDecimal.ZERO)
                    .abierta(false)
                    .usuarioApertura(null)
                    .build();
        }

        return aEstadoDTO(cajaOpt.get());
    }

    @Override
    @Transactional(readOnly = true)
    public CajaEstadoResponseDTO obtenerEstadoCaja(Long sucursalId, String username) {
        if (sucursalId == null) {
            return obtenerEstadoCaja(username);
        }
        LocalDate hoy = LocalDate.now(clock);
        Optional<CajaSucursal> cajaOpt = cajaSucursalRepository.findBySucursalIdAndFecha(sucursalId, hoy);
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new NegocioException("Sucursal no encontrada con ID: " + sucursalId));

        if (cajaOpt.isEmpty()) {
            return CajaEstadoResponseDTO.builder()
                    .sucursalId(sucursal.getId())
                    .sucursalNombre(sucursal.getNombre())
                    .fecha(hoy)
                    .saldoDisponible(BigDecimal.ZERO)
                    .montoObservadoAcumulado(BigDecimal.ZERO)
                    .abierta(false)
                    .usuarioApertura(null)
                    .build();
        }

        return aEstadoDTO(cajaOpt.get());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidenciaHermesDTO> listarIncidencias(EstadoIncidenciaHermes estado, Long sucursalId, String username) {
        return incidenciaHermesRepository.filtrarIncidencias(estado, sucursalId).stream()
                .map(this::mapIncidenciaToDTO)
                .toList();
    }

    @Override
    @Transactional
    public IncidenciaHermesDTO cerrarIncidencia(Long incidenciaId, String resolucion, String usernameGerente) {
        Usuario usuario = usuarioRepository.findByUsername(usernameGerente)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + usernameGerente));
        if (usuario.getRol() != Rol.GERENTE) {
            throw new NegocioException("Solo el Gerente General tiene autorización para cerrar incidencias.");
        }

        if (resolucion == null || resolucion.trim().isEmpty()) {
            throw new NegocioException("Debe ingresar una resolución escrita para cerrar la incidencia.");
        }

        IncidenciaHermes incidencia = incidenciaHermesRepository.findById(incidenciaId)
                .orElseThrow(() -> new NegocioException("Incidencia no encontrada con ID: " + incidenciaId));

        if (incidencia.getEstado() == EstadoIncidenciaHermes.CERRADA) {
            throw new NegocioException("La incidencia ya se encuentra cerrada y no puede ser modificada.");
        }

        incidencia.setEstado(EstadoIncidenciaHermes.CERRADA);
        incidencia.setResolucion(resolucion.trim());
        incidencia.setUsuarioCierre(usuario);
        incidencia.setFechaCierre(LocalDateTime.now(clock));

        incidencia = incidenciaHermesRepository.save(incidencia);
        return mapIncidenciaToDTO(incidencia);
    }

    @Override
    @Transactional
    public void verificarYDescontarSaldoCaja(Long sucursalId, BigDecimal monto) {
        if (sucursalId == null || monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate hoy = LocalDate.now(clock);
        Optional<CajaSucursal> cajaOpt = cajaSucursalRepository.findBySucursalIdAndFechaWithLock(sucursalId, hoy);
        if (cajaOpt.isPresent()) {
            CajaSucursal caja = cajaOpt.get();
            if (caja.isCerrada()) {
                throw new NegocioException("La caja de la sucursal ya fue cerrada hoy; no se pueden realizar desembolsos.");
            }
            if (caja.isAbierta()) {
                if (caja.getSaldoDisponible().compareTo(monto) < 0) {
                    throw new NegocioException(String.format(
                            "Saldo insuficiente en la caja de la sucursal para realizar el desembolso (Disponible: S/ %.2f, Requerido: S/ %.2f).",
                            caja.getSaldoDisponible(), monto));
                }
                caja.setSaldoDisponible(caja.getSaldoDisponible().subtract(monto));
                cajaSucursalRepository.save(caja);
            }
        }
    }

    @Override
    @Transactional
    public CajaEstadoResponseDTO cerrarCaja(Long sucursalId, String observacion, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + username));

        Long idSucursal;
        if (usuario.getRol() == Rol.GERENTE) {
            idSucursal = sucursalId != null ? sucursalId
                    : (usuario.getSucursal() != null ? usuario.getSucursal().getId() : null);
        } else {
            if (usuario.getSucursal() == null) {
                throw new NegocioException("El usuario no tiene una sucursal asignada para cerrar caja.");
            }
            idSucursal = usuario.getSucursal().getId();
        }
        if (idSucursal == null) {
            throw new NegocioException("Debe indicar la sucursal cuya caja se desea cerrar.");
        }

        LocalDate hoy = LocalDate.now(clock);
        CajaSucursal caja = cajaSucursalRepository.findBySucursalIdAndFechaWithLock(idSucursal, hoy)
                .orElseThrow(() -> new NegocioException("La caja de hoy no fue abierta; no hay nada que cerrar."));
        if (caja.isCerrada()) {
            throw new NegocioException("La caja de hoy ya fue cerrada.");
        }
        if (!caja.isAbierta()) {
            throw new NegocioException("La caja de hoy no está abierta.");
        }

        BigDecimal totalCobrado = pagoRepository.sumMontoBySucursalIdAndFecha(idSucursal, hoy);
        caja.setCerrada(true);
        caja.setAbierta(false);
        caja.setUsuarioCierre(usuario);
        caja.setFechaCierre(LocalDateTime.now(clock));
        caja.setSaldoCierre(caja.getSaldoDisponible());
        caja.setTotalCobradoCierre(totalCobrado);
        caja.setObservacionCierre(observacion != null && !observacion.isBlank() ? observacion.trim() : null);
        return aEstadoDTO(cajaSucursalRepository.save(caja));
    }

    private CajaEstadoResponseDTO aEstadoDTO(CajaSucursal c) {
        BigDecimal cobradoHoy = c.isCerrada() && c.getTotalCobradoCierre() != null
                ? c.getTotalCobradoCierre()
                : pagoRepository.sumMontoBySucursalIdAndFecha(c.getSucursal().getId(), c.getFecha());
        return CajaEstadoResponseDTO.builder()
                .sucursalId(c.getSucursal().getId())
                .sucursalNombre(c.getSucursal().getNombre())
                .fecha(c.getFecha())
                .saldoDisponible(c.getSaldoDisponible())
                .montoObservadoAcumulado(c.getMontoObservadoAcumulado())
                .abierta(c.isAbierta())
                .usuarioApertura(c.getUsuarioApertura() != null ? c.getUsuarioApertura().getNombre() : null)
                .cerrada(c.isCerrada())
                .usuarioCierre(c.getUsuarioCierre() != null ? c.getUsuarioCierre().getNombre() : null)
                .fechaCierre(c.getFechaCierre())
                .saldoCierre(c.getSaldoCierre())
                .totalCobradoCierre(c.getTotalCobradoCierre())
                .totalCobradoHoy(cobradoHoy)
                .observacionCierre(c.getObservacionCierre())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CajaEstadoResponseDTO> listarCierres(LocalDate desde, LocalDate hasta, Long sucursalId, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado: " + username));
        if (usuario.getRol() != Rol.GERENTE) {
            throw new NegocioException("Solo el Gerente General puede consultar los cierres de caja.");
        }
        LocalDate fin = hasta != null ? hasta : LocalDate.now(clock);
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new NegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        return cajaSucursalRepository.findByRangoFechas(inicio, fin).stream()
                .filter(c -> sucursalId == null || c.getSucursal().getId().equals(sucursalId))
                .map(this::aEstadoDTO)
                .toList();
    }

    private IncidenciaHermesDTO mapIncidenciaToDTO(IncidenciaHermes i) {
        return IncidenciaHermesDTO.builder()
                .id(i.getId())
                .entregaId(i.getEntrega() != null ? i.getEntrega().getId() : null)
                .sucursalId(i.getSucursal() != null ? i.getSucursal().getId() : null)
                .sucursalNombre(i.getSucursal() != null ? i.getSucursal().getNombre() : null)
                .usuarioReporta(i.getUsuarioReporta() != null ? i.getUsuarioReporta().getNombre() : null)
                .fecha(i.getFecha())
                .montoEsperado(i.getMontoEsperado())
                .montoApto(i.getMontoApto())
                .montoObservado(i.getMontoObservado())
                .diferencia(i.getDiferencia())
                .motivo(i.getMotivo())
                .estado(i.getEstado().name())
                .resolucion(i.getResolucion())
                .usuarioCierre(i.getUsuarioCierre() != null ? i.getUsuarioCierre().getNombre() : null)
                .fechaCierre(i.getFechaCierre())
                .build();
    }
}
