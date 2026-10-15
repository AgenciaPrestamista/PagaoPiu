package com.example.pagaopiu.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String inicio(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            boolean esGerente = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE"));
            if (esGerente) {
                return "redirect:/panel";
            }
            return "redirect:/cliente/buscar";
        }
        return "redirect:/login";
    }
}
