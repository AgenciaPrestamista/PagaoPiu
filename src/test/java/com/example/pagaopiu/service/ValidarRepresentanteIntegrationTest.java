package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.controller.rest.ClienteRestController;
import com.example.pagaopiu.dto.sunat.SunatRucDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
class ValidarRepresentanteIntegrationTest {

    private static final String RUC_LOS_ANDES = "20987654321";
    private static final String DNI_REPRESENTANTE = "10000001"; // Juan Carlos Pérez López (vigente en RENIEC)
    private static final String DNI_AJENO = "10000002"; // Existe en RENIEC pero no es representante de Los Andes

    @Autowired
    private ClienteRestController clienteRestController;

    private Map<String, Object> validar(String ruc, String dni) {
        ResponseEntity<Map<String, Object>> resp = clienteRestController.validarRepresentante(ruc, dni);
        return resp.getBody();
    }

    @Test
    @DisplayName("RUC 20: un DNI que es representante legal de la empresa es válido")
    void dniDeRepresentanteEsValido() {
        Map<String, Object> body = validar(RUC_LOS_ANDES, DNI_REPRESENTANTE);
        assertThat(body.get("valido")).isEqualTo(true);
    }

    @Test
    @DisplayName("RUC 20: un DNI de otra persona se rechaza sin revelar quiénes son los representantes")
    void dniAjenoSeRechaza() {
        Map<String, Object> body = validar(RUC_LOS_ANDES, DNI_AJENO);
        assertThat(body.get("valido")).isEqualTo(false);
        assertThat(body.get("mensaje").toString()).contains("no figura como representante legal");
        assertThat(body).doesNotContainKeys("representanteNombre", "representanteDocumento");
        assertThat(body.toString()).doesNotContain(DNI_REPRESENTANTE).doesNotContain("Pérez");
    }

    @Test
    @DisplayName("RUC 10: solo es válido el DNI derivado del propio RUC")
    void ruc10ExigeDniDelTitular() {
        assertThat(validar("10123456789", "12345678").get("valido")).isEqualTo(true);
        assertThat(validar("10123456789", "87654321").get("valido")).isEqualTo(false);
    }

    @Test
    @DisplayName("Formato inválido responde 400")
    void formatoInvalido() {
        assertThat(clienteRestController.validarRepresentante("123", "10000001").getStatusCode().value()).isEqualTo(400);
        assertThat(clienteRestController.validarRepresentante(RUC_LOS_ANDES, "12").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("Con varios representantes en SUNAT, cualquiera de ellos es aceptado")
    void variosRepresentantesSeAceptanTodos() {
        SunatRucDTO sunat = SunatRucDTO.builder()
                .ruc("20111111111")
                .representanteNombre("PRINCIPAL")
                .representanteDocumento("11111111")
                .representanteCargo("GERENTE GENERAL")
                .representantes(List.of(
                        new SunatRucDTO.Representante("PRINCIPAL", "11111111", "GERENTE GENERAL"),
                        new SunatRucDTO.Representante("SEGUNDO", "22222222", "APODERADO")))
                .build();

        assertThat(sunat.buscarRepresentantePorDocumento("11111111")).isPresent();
        assertThat(sunat.buscarRepresentantePorDocumento("22222222"))
                .get().extracting(SunatRucDTO.Representante::getCargo).isEqualTo("APODERADO");
        assertThat(sunat.buscarRepresentantePorDocumento("33333333")).isEmpty();
        assertThat(sunat.buscarRepresentantePorDocumento(" ")).isEmpty();
    }

    @Test
    @DisplayName("Sin lista explícita, el representante principal es el único válido")
    void sinListaUsaElPrincipal() {
        SunatRucDTO sunat = SunatRucDTO.builder()
                .ruc("20111111111")
                .representanteNombre("PRINCIPAL")
                .representanteDocumento("11111111")
                .representanteCargo("GERENTE GENERAL")
                .build();

        assertThat(sunat.buscarRepresentantePorDocumento("11111111")).isPresent();
        assertThat(sunat.buscarRepresentantePorDocumento("22222222")).isEmpty();
    }
}
