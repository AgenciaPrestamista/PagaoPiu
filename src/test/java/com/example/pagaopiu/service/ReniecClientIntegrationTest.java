package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.model.entity.ReniecPadron;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReniecClientIntegrationTest {

    @Autowired
    private ReniecClient reniecClient;

    @Test
    @DisplayName("R2: DNI de persona válida existe con vigencia, con vida y nacionalidad peruana")
    void testBuscarPersonaValida() {
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni("27427864");
        assertThat(padronOpt).isPresent();
        ReniecPadron padron = padronOpt.get();
        assertThat(padron.getNombreCompleto()).isEqualTo("CASTILLO TERRONES, JOSE PEDRO");
        assertThat(padron.getNacionalidad()).isEqualTo("PERUANA");
        assertThat(padron.isDniVigente()).isTrue();
        assertThat(padron.isFallecido()).isFalse();
    }

    @Test
    @DisplayName("R2: DNI de persona fallecida tiene fallecido = true")
    void testBuscarPersonaFallecida() {
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni("99999901");
        assertThat(padronOpt).isPresent();
        assertThat(padronOpt.get().isFallecido()).isTrue();
    }

    @Test
    @DisplayName("R2: DNI no vigente tiene dni_vigente = false")
    void testBuscarDniNoVigente() {
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni("99999902");
        assertThat(padronOpt).isPresent();
        assertThat(padronOpt.get().isDniVigente()).isFalse();
    }

    @Test
    @DisplayName("R2: Extranjero tiene nacionalidad no peruana")
    void testBuscarExtranjero() {
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni("99999903");
        assertThat(padronOpt).isPresent();
        assertThat(padronOpt.get().getNacionalidad()).isEqualTo("ESTADOUNIDENSE");
    }

    @Test
    @DisplayName("DNI inexistente en padrón devuelve Optional vacío")
    void testBuscarDniInexistente() {
        Optional<ReniecPadron> padronOpt = reniecClient.buscarPorDni("00000000");
        assertThat(padronOpt).isEmpty();
    }
}
