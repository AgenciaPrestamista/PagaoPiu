package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pagaopiu.dto.CrearUsuarioRequestDTO;
import com.example.pagaopiu.dto.UsuarioInfoDTO;
import com.example.pagaopiu.model.enums.Rol;
import com.example.pagaopiu.repository.SucursalRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class UsuarioAdminIntegrationTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private MockHttpSession crearSesion(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, "password123", userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    @DisplayName("Seguridad: Gerente puede acceder a /api/usuarios y listar las cuentas")
    void testGerenteAccesoAUsuarios() throws Exception {
        MockHttpSession sessionGerente = crearSesion("gerente");

        mockMvc.perform(get("/api/usuarios").session(sessionGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Seguridad: Asesor recibe 403 Forbidden al intentar acceder a /api/usuarios")
    void testAsesorRecibe403EnUsuarios() throws Exception {
        MockHttpSession sessionAsesor = crearSesion("asesor.trujillo");

        mockMvc.perform(get("/api/usuarios").session(sessionAsesor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Seguridad: Anónimo recibe 401 Unauthorized en /api/usuarios")
    void testAnonimoRecibe401EnUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Crear nuevo asesor con sucursal exitoso y valida hash de clave")
    void testCrearAsesorExitoso() {
        String testUser = "asesor.test." + System.currentTimeMillis();
        Long sucursalId = sucursalRepository.findAll().get(0).getId();

        CrearUsuarioRequestDTO dto = CrearUsuarioRequestDTO.builder()
                .username(testUser)
                .password("claveSegura123")
                .nombre("Asesor Prueba Integracion")
                .rol(Rol.ASESOR)
                .sucursalId(sucursalId)
                .build();

        UsuarioInfoDTO creado = usuarioService.crearUsuario(dto);

        assertThat(creado).isNotNull();
        assertThat(creado.getUsername()).isEqualTo(testUser);
        assertThat(creado.getRol()).isEqualTo("ASESOR");
        assertThat(creado.getActivo()).isTrue();
        assertThat(creado.getSucursalId()).isEqualTo(sucursalId);

        var usuarioBd = usuarioRepository.findByUsername(testUser).orElseThrow();
        assertThat(passwordEncoder.matches("claveSegura123", usuarioBd.getPasswordHash())).isTrue();

        // Limpiar
        usuarioRepository.delete(usuarioBd);
    }

    @Test
    @DisplayName("Validación: No permite crear usuario duplicado ni con contraseña menor a 6 caracteres")
    void testValidacionesCrearUsuario() {
        // Ya existe 'gerente'
        CrearUsuarioRequestDTO dtoDuplicado = CrearUsuarioRequestDTO.builder()
                .username("gerente")
                .password("clave123")
                .nombre("Otro Gerente")
                .rol(Rol.GERENTE)
                .build();

        assertThatThrownBy(() -> usuarioService.crearUsuario(dtoDuplicado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya existe");

        // Clave muy corta
        CrearUsuarioRequestDTO dtoClaveCorta = CrearUsuarioRequestDTO.builder()
                .username("usuario.corto")
                .password("123")
                .nombre("Usuario Corto")
                .rol(Rol.GERENTE)
                .build();

        assertThatThrownBy(() -> usuarioService.crearUsuario(dtoClaveCorta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 6 caracteres");
    }

    @Test
    @DisplayName("Seguridad: Gerente no puede desactivar su propia cuenta en sesión")
    void testGerenteNoPuedeDesactivarseASiMismo() {
        var gerente = usuarioRepository.findByUsername("gerente").orElseThrow();

        assertThatThrownBy(() -> usuarioService.cambiarEstadoActivo(gerente.getId(), false, "gerente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No puede desactivar su propia cuenta");
    }
}
