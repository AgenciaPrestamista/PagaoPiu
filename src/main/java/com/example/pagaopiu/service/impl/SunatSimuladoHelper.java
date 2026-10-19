package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.dto.sunat.SunatRucDTO;

public final class SunatSimuladoHelper {

    private SunatSimuladoHelper() {}

    public static SunatRucDTO obtenerSimulado(String ruc) {
        if ("20987654321".equals(ruc)) {
            return SunatRucDTO.builder()
                    .ruc(ruc)
                    .razonSocial("COMERCIAL LOS ANDES S.A.C.")
                    .direccion("Av. Industrial 456, Trujillo")
                    .representanteNombre("CARLOS ALBERTO MENDOZA RUIZ")
                    .representanteDocumento("10000001")
                    .representanteCargo("GERENTE GENERAL")
                    .estado("ACTIVO")
                    .condicion("HABIDO")
                    .activo(true)
                    .habido(true)
                    .build();
        } else if ("20123456789".equals(ruc)) {
            return SunatRucDTO.builder()
                    .ruc(ruc)
                    .razonSocial("PRÉSTAMOS DEL NORTE S.A.C.")
                    .direccion("Av. Principal 123, Trujillo")
                    .representanteNombre("MARÍA ELENA VARGAS SOTO")
                    .representanteDocumento("10000002")
                    .representanteCargo("GERENTE GENERAL")
                    .estado("ACTIVO")
                    .condicion("HABIDO")
                    .activo(true)
                    .habido(true)
                    .build();
        } else if ("20552103816".equals(ruc)) {
            return SunatRucDTO.builder()
                    .ruc(ruc)
                    .razonSocial("AGROLIGHT PERU S.A.C.")
                    .direccion("PJ. JORGE BASADRE NRO. 158 URB. POP LA UNIVERSAL 2DA ET., LIMA - LIMA - SANTA ANITA")
                    .representanteNombre("PAIMO LOZANO JUAN")
                    .representanteDocumento("10293872")
                    .representanteCargo("GERENTE GENERAL")
                    .estado("ACTIVO")
                    .condicion("HABIDO")
                    .activo(true)
                    .habido(true)
                    .build();
        } else if ("20141878477".equals(ruc)) {
            return SunatRucDTO.builder()
                    .ruc(ruc)
                    .razonSocial("DISTRIBUIDORA Y COMERCIALIZADORA EL SOL S.A.C.")
                    .direccion("Av. España 1024, Trujillo")
                    .representanteNombre("JORGE LUIS ALVAREZ PAREDES")
                    .representanteDocumento("10847291")
                    .representanteCargo("GERENTE GENERAL")
                    .estado("ACTIVO")
                    .condicion("HABIDO")
                    .activo(true)
                    .habido(true)
                    .build();
        }

        return SunatRucDTO.builder()
                .ruc(ruc)
                .razonSocial("CORPORACIÓN EMPRESARIAL " + ruc.substring(0, Math.min(4, ruc.length())) + " S.A.C.")
                .direccion("Calle Los Empresarios 100, Trujillo")
                .representanteNombre("EDGAR RICARDO SILVA RAMÍREZ")
                .representanteDocumento("10394852")
                .representanteCargo("GERENTE GENERAL")
                .estado("ACTIVO")
                .condicion("HABIDO")
                .activo(true)
                .habido(true)
                .build();
    }
}
