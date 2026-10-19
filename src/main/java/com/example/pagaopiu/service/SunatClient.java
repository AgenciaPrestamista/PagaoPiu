package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import java.util.Optional;

public interface SunatClient {

    Optional<SunatRucDTO> buscarPorRuc(String ruc);
}
