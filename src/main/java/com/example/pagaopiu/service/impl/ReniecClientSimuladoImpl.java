package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.repository.ReniecPadronRepository;
import com.example.pagaopiu.service.ReniecClient;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service("reniecClientSimulado")
@ConditionalOnProperty(name = "app.reniec.provider", havingValue = "simulado", matchIfMissing = true)
public class ReniecClientSimuladoImpl implements ReniecClient {

    private final ReniecPadronRepository reniecPadronRepository;

    public ReniecClientSimuladoImpl(ReniecPadronRepository reniecPadronRepository) {
        this.reniecPadronRepository = reniecPadronRepository;
    }

    @Override
    public Optional<ReniecPadron> buscarPorDni(String dni) {
        return reniecPadronRepository.findByDni(dni);
    }
}
