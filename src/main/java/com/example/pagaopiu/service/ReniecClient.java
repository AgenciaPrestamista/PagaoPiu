package com.example.pagaopiu.service;

import com.example.pagaopiu.model.entity.ReniecPadron;
import java.util.Optional;

public interface ReniecClient {

    Optional<ReniecPadron> buscarPorDni(String dni);
}
