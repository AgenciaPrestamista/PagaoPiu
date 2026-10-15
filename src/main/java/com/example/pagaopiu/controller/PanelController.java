package com.example.pagaopiu.controller;

import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.service.PanelService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/panel")
public class PanelController {

    private final PanelService panelService;

    public PanelController(PanelService panelService) {
        this.panelService = panelService;
    }

    @GetMapping
    public String verPanel(Model model) {
        PanelResponseDTO panel = panelService.obtenerMetricasPanel();
        model.addAttribute("panel", panel);
        return "panel";
    }
}
