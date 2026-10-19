package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import com.example.pagaopiu.service.SunatClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service("sunatClientApi")
@ConditionalOnProperty(name = "app.reniec.provider", havingValue = "api")
public class SunatClientApiImpl implements SunatClient {

    private final RestClient restClient;
    private final String apiToken;

    public SunatClientApiImpl(
            @Value("${app.sunat.api.url:https://api.json.pe/api}") String apiUrl,
            @Value("${app.reniec.api.token:}") String apiToken) {
        this.apiToken = apiToken != null ? apiToken.trim() : "";
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + this.apiToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<SunatRucDTO> buscarPorRuc(String ruc) {
        if (ruc == null || !ruc.trim().matches("\\d{11}")) {
            return Optional.empty();
        }
        String rucLimpio = ruc.trim();

        if (this.apiToken.isEmpty()) {
            return Optional.of(SunatSimuladoHelper.obtenerSimulado(rucLimpio));
        }

        try {
            // 1. Consultar RUC (Razón Social, Estado, Condición, Dirección)
            Map<String, Object> rucResp = restClient.post()
                    .uri("/ruc")
                    .body(Map.of("ruc", rucLimpio))
                    .retrieve()
                    .body(Map.class);

            if (rucResp == null || !Boolean.TRUE.equals(rucResp.get("success")) || !(rucResp.get("data") instanceof Map)) {
                return Optional.of(SunatSimuladoHelper.obtenerSimulado(rucLimpio));
            }

            Map<String, Object> data = (Map<String, Object>) rucResp.get("data");
            String razonSocial = valorCadena(data.get("nombre_o_razon_social"));
            if (razonSocial.isEmpty()) {
                razonSocial = valorCadena(data.get("razon_social"));
            }
            String estado = valorCadena(data.get("estado"), "ACTIVO");
            String condicion = valorCadena(data.get("condicion"), "HABIDO");
            String direccion = valorCadena(data.get("direccion_completa"));
            if (direccion.isEmpty()) {
                direccion = valorCadena(data.get("direccion"), "Dirección fiscal registrada en SUNAT");
            }

            // 2. Consultar Representantes Legales (Encargado)
            String repNombre = null;
            String repDoc = null;
            String repCargo = null;

            try {
                Map<String, Object> repResp = restClient.post()
                        .uri("/ruc/representantes")
                        .body(Map.of("ruc", rucLimpio))
                        .retrieve()
                        .body(Map.class);

                if (repResp != null && Boolean.TRUE.equals(repResp.get("success")) && repResp.get("data") instanceof List<?> list) {
                    if (!list.isEmpty()) {
                        Map<String, Object> repElegido = null;
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                Map<String, Object> repMap = (Map<String, Object>) m;
                                String cargoActual = valorCadena(repMap.get("cargo")).toUpperCase();
                                if (cargoActual.contains("GERENTE") || cargoActual.contains("ADMINISTRADOR") || cargoActual.contains("REPRESENTANTE")) {
                                    repElegido = repMap;
                                    break;
                                }
                            }
                        }
                        if (repElegido == null && list.get(0) instanceof Map<?, ?> m0) {
                            repElegido = (Map<String, Object>) m0;
                        }

                        if (repElegido != null) {
                            repNombre = valorCadena(repElegido.get("nombre"));
                            repDoc = valorCadena(repElegido.get("numero_de_documento"));
                            repCargo = valorCadena(repElegido.get("cargo"), "GERENTE GENERAL");
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallback si la subconsulta de representantes falla
            }

            SunatRucDTO simuladoFallback = SunatSimuladoHelper.obtenerSimulado(rucLimpio);
            if (repNombre == null || repNombre.isEmpty()) {
                repNombre = simuladoFallback.getRepresentanteNombre();
                repDoc = simuladoFallback.getRepresentanteDocumento();
                repCargo = simuladoFallback.getRepresentanteCargo();
            }

            boolean activo = !"BAJA DEFINITIVA".equalsIgnoreCase(estado) && !"BAJA DE OFICIO".equalsIgnoreCase(estado);
            boolean habido = "HABIDO".equalsIgnoreCase(condicion);

            return Optional.of(SunatRucDTO.builder()
                    .ruc(rucLimpio)
                    .razonSocial(!razonSocial.isEmpty() ? razonSocial : simuladoFallback.getRazonSocial())
                    .estado(estado)
                    .condicion(condicion)
                    .direccion(!direccion.isEmpty() ? direccion : simuladoFallback.getDireccion())
                    .representanteNombre(repNombre)
                    .representanteDocumento(repDoc)
                    .representanteCargo(repCargo)
                    .activo(activo)
                    .habido(habido)
                    .build());

        } catch (Exception ex) {
            return Optional.of(SunatSimuladoHelper.obtenerSimulado(rucLimpio));
        }
    }

    private String valorCadena(Object obj) {
        return valorCadena(obj, "");
    }

    private String valorCadena(Object obj, String defecto) {
        if (obj == null) return defecto;
        String s = String.valueOf(obj).trim();
        return s.isEmpty() ? defecto : s;
    }
}
