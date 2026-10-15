package com.example.pagaopiu.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.model.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Usuario gerente se carga con ROLE_GERENTE y sin sucursal asignada")
    void testCargaGerente() {
        UserDetails details = userDetailsService.loadUserByUsername("gerente");
        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo("gerente");
        assertThat(passwordEncoder.matches("password123", details.getPassword())).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_GERENTE");

        UsuarioPrincipal principal = (UsuarioPrincipal) details;
        assertThat(principal.getUsuario().getRol()).isEqualTo(Rol.GERENTE);
        assertThat(principal.getSucursal()).isNull();
    }

    @Test
    @DisplayName("Usuario asesor se carga con ROLE_ASESOR y con sucursal asignada")
    void testCargaAsesorChimbote() {
        UserDetails details = userDetailsService.loadUserByUsername("asesor.chimbote");
        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo("asesor.chimbote");
        assertThat(passwordEncoder.matches("password123", details.getPassword())).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ASESOR");

        UsuarioPrincipal principal = (UsuarioPrincipal) details;
        assertThat(principal.getUsuario().getRol()).isEqualTo(Rol.ASESOR);
        assertThat(principal.getSucursal()).isNotNull();
        assertThat(principal.getSucursal().getNombre()).isEqualTo("Chimbote");
    }

    @Autowired
    private com.example.pagaopiu.controller.rest.AuthRestController authRestController;

    @Autowired
    private com.example.pagaopiu.repository.UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Cambio de contraseña rechaza contraseña actual incorrecta o longitud menor a 6")
    void testCambiarPasswordValidaciones() {
        UserDetails details = userDetailsService.loadUserByUsername("gerente");
        UsuarioPrincipal principal = (UsuarioPrincipal) details;

        // Longitud menor a 6
        var dtoCorta = new com.example.pagaopiu.dto.CambiarPasswordRequestDTO("password123", "123", "123");
        var resCorta = authRestController.cambiarPassword(dtoCorta, principal);
        assertThat(resCorta.getStatusCode().value()).isEqualTo(400);

        // Confirmación no coincide
        var dtoNoCoincide = new com.example.pagaopiu.dto.CambiarPasswordRequestDTO("password123", "nuevaClave123", "otraClave456");
        var resNoCoincide = authRestController.cambiarPassword(dtoNoCoincide, principal);
        assertThat(resNoCoincide.getStatusCode().value()).isEqualTo(400);

        // Contraseña actual errónea
        var dtoClaveErronea = new com.example.pagaopiu.dto.CambiarPasswordRequestDTO("claveIncorrecta", "nuevaClave123", "nuevaClave123");
        var resClaveErronea = authRestController.cambiarPassword(dtoClaveErronea, principal);
        assertThat(resClaveErronea.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("Cambio de contraseña exitoso actualiza el hash en la base de datos")
    void testCambiarPasswordExitoso() {
        UserDetails details = userDetailsService.loadUserByUsername("gerente");
        UsuarioPrincipal principal = (UsuarioPrincipal) details;

        var dtoOk = new com.example.pagaopiu.dto.CambiarPasswordRequestDTO("password123", "nuevaSegura2026", "nuevaSegura2026");
        var resOk = authRestController.cambiarPassword(dtoOk, principal);
        assertThat(resOk.getStatusCode().value()).isEqualTo(200);

        var usuarioActualizado = usuarioRepository.findByUsername("gerente").orElseThrow();
        assertThat(passwordEncoder.matches("nuevaSegura2026", usuarioActualizado.getPasswordHash())).isTrue();

        // Restaurar contraseña original para no afectar otros tests
        usuarioActualizado.setPasswordHash(passwordEncoder.encode("password123"));
        usuarioRepository.save(usuarioActualizado);
    }
}
