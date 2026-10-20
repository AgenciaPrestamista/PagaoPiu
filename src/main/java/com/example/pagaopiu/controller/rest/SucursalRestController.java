package com.example.pagaopiu.controller.rest;

import com.example.pagaopiu.dto.SucursalDTO;
import com.example.pagaopiu.service.UsuarioService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sucursales")
public class SucursalRestController {

    private final UsuarioService usuarioService;

    public SucursalRestController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<SucursalDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listarSucursales());
    }
}
