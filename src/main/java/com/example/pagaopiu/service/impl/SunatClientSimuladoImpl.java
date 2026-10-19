package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import com.example.pagaopiu.service.SunatClient;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service("sunatClientSimulado")
@ConditionalOnProperty(name = "app.reniec.provider", havingValue = "simulado", matchIfMissing = true)
public class SunatClientSimuladoImpl implements SunatClient {

    @Override
    public Optional<SunatRucDTO> buscarPorRuc(String ruc) {
        if (ruc == null || !ruc.trim().matches("\\d{11}")) {
            return Optional.empty();
        }
        return Optional.of(SunatSimuladoHelper.obtenerSimulado(ruc.trim()));
    }
}
