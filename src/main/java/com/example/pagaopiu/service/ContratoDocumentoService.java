package com.example.pagaopiu.service;

import com.example.pagaopiu.model.entity.Prestamo;
import java.nio.file.Path;
import org.springframework.core.io.Resource;

public interface ContratoDocumentoService {

    ContratoTextoDTO generarTextoContrato(Prestamo prestamo);

    Path validarYObtenerRutaFirma();

    Resource obtenerRecursoFirmaGerente();

    boolean tieneFirmaGerente();

    String obtenerContentTypeFirma();

    String getEmisorRazonSocial();

    record ContratoTextoDTO(
            String textoContrato,
            String bloqueFirmas,
            String ciudad,
            String fechaEmisionFormateada,
            String emisorRazonSocial
    ) {}
}
