package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.repository.ClienteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClienteRegistroHelper {

    private final ClienteRepository clienteRepository;

    public ClienteRegistroHelper(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cliente insertarNuevoCliente(String dni, String nombreCompleto) {
        Cliente nuevo = Cliente.builder()
                .dni(dni)
                .nombreCompleto(nombreCompleto)
                .estado(EstadoCliente.ACTIVO)
                .build();
        return clienteRepository.saveAndFlush(nuevo);
    }
}
