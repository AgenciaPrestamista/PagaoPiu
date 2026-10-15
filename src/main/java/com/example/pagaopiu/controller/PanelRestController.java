package com.example.pagaopiu.controller;

import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.service.PanelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/panel")
public class PanelRestController {

    private final PanelService panelService;

    public PanelRestController(PanelService panelService) {
        this.panelService = panelService;
    }

    @GetMapping
    public ResponseEntity<PanelResponseDTO> obtenerPanel() {
        PanelResponseDTO panel = panelService.obtenerMetricasPanel();
        return ResponseEntity.ok(panel);
    }
}
