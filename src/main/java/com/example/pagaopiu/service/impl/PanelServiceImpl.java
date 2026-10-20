package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.dto.PanelSucursalDTO;
import com.example.pagaopiu.model.entity.Sucursal;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.service.PanelService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PanelServiceImpl implements PanelService {

    private final SucursalRepository sucursalRepository;
    private final PrestamoRepository prestamoRepository;
    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final ClienteRepository clienteRepository;

    public PanelServiceImpl(
            SucursalRepository sucursalRepository,
            PrestamoRepository prestamoRepository,
            PagoRepository pagoRepository,
            CuotaRepository cuotaRepository,
            ClienteRepository clienteRepository) {
        this.sucursalRepository = sucursalRepository;
        this.prestamoRepository = prestamoRepository;
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.clienteRepository = clienteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PanelResponseDTO obtenerMetricasPanel() {
        List<Sucursal> sucursales = sucursalRepository.findAll().stream()
                .sorted(Comparator.comparing(Sucursal::getId))
                .toList();

        List<PanelSucursalDTO> metricasSucursales = new ArrayList<>();

        long sumActivos = 0;
        long sumVigentes = 0;
        long sumMorosos = 0;
        long sumExtrajudiciales = 0;
        BigDecimal sumCobrado = BigDecimal.ZERO;
        BigDecimal sumIntereses = BigDecimal.ZERO;
        BigDecimal sumColocado = BigDecimal.ZERO;

        for (Sucursal s : sucursales) {
            long activos = prestamoRepository.countActivosBySucursalId(s.getId());
            long vigentes = prestamoRepository.countBySucursalIdAndEstado(s.getId(), EstadoPrestamo.VIGENTE);
            long morosos = prestamoRepository.countBySucursalIdAndEstado(s.getId(), EstadoPrestamo.MOROSO);
            long extra = prestamoRepository.countBySucursalIdAndEstado(s.getId(), EstadoPrestamo.EXTRAJUDICIAL);

            BigDecimal colocado = prestamoRepository.sumCapitalColocadoBySucursalId(s.getId());
            if (colocado == null) {
                colocado = BigDecimal.ZERO;
            }
            colocado = colocado.setScale(2, RoundingMode.HALF_UP);

            long clientesSucursal = prestamoRepository.countClientesBySucursalId(s.getId());

            BigDecimal cobrado = pagoRepository.sumMontoBySucursalId(s.getId());
            if (cobrado == null) {
                cobrado = BigDecimal.ZERO;
            }
            cobrado = cobrado.setScale(2, RoundingMode.HALF_UP);

            BigDecimal intereses = cuotaRepository.sumInteresCobradoBySucursalId(s.getId());
            if (intereses == null) {
                intereses = BigDecimal.ZERO;
            }
            intereses = intereses.setScale(2, RoundingMode.HALF_UP);

            metricasSucursales.add(PanelSucursalDTO.builder()
                    .sucursalId(s.getId())
                    .sucursalNombre(s.getNombre())
                    .sucursalCodigo(s.getNombre().length() >= 3 ? s.getNombre().substring(0, 3).toUpperCase() : s.getNombre().toUpperCase())
                    .prestamosActivos(activos)
                    .prestamosVigentes(vigentes)
                    .prestamosMorosos(morosos)
                    .prestamosExtrajudiciales(extra)
                    .montoTotalColocado(colocado)
                    .cantidadClientes(clientesSucursal)
                    .montoTotalCobrado(cobrado)
                    .interesesCobrados(intereses)
                    .build());

            sumActivos += activos;
            sumVigentes += vigentes;
            sumMorosos += morosos;
            sumExtrajudiciales += extra;
            sumCobrado = sumCobrado.add(cobrado);
            sumIntereses = sumIntereses.add(intereses);
            sumColocado = sumColocado.add(colocado);
        }

        long clientesMorosos = clienteRepository.countByEstado(EstadoCliente.MOROSO);
        long clientesExtrajudiciales = clienteRepository.countByEstado(EstadoCliente.EXTRAJUDICIAL);
        long totalClientesUnicos = prestamoRepository.countTotalClientes();

        return PanelResponseDTO.builder()
                .clientesMorosos(clientesMorosos)
                .clientesExtrajudiciales(clientesExtrajudiciales)
                .sucursales(metricasSucursales)
                .totalPrestamosActivos(sumActivos)
                .totalPrestamosVigentes(sumVigentes)
                .totalPrestamosMorosos(sumMorosos)
                .totalPrestamosExtrajudiciales(sumExtrajudiciales)
                .totalMontoColocado(sumColocado.setScale(2, RoundingMode.HALF_UP))
                .totalClientes(totalClientesUnicos)
                .totalMontoCobrado(sumCobrado.setScale(2, RoundingMode.HALF_UP))
                .totalInteresesCobrados(sumIntereses.setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}
