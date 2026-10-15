package com.example.pagaopiu.exception;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public Object manejarNegocioException(NegocioException ex, HttpServletRequest request, Model model) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        model.addAttribute("error", ex.getMessage());

        if (uri != null && uri.contains("/prestamo")) {
            PrestamoRegistroDTO dto = new PrestamoRegistroDTO();
            dto.setDni(request.getParameter("dni"));
            dto.setNombreCliente(request.getParameter("nombreCliente"));

            String montoParam = request.getParameter("monto");
            if (montoParam != null && !montoParam.isBlank()) {
                try {
                    dto.setMonto(new BigDecimal(montoParam.trim()));
                } catch (Exception ignored) {
                }
            }

            String plazoParam = request.getParameter("plazoMeses");
            if (plazoParam != null && !plazoParam.isBlank()) {
                try {
                    dto.setPlazoMeses(Integer.parseInt(plazoParam.trim()));
                } catch (Exception ignored) {
                }
            }

            String fechaParam = request.getParameter("fechaPrestamo");
            if (fechaParam != null && !fechaParam.isBlank()) {
                try {
                    dto.setFechaPrestamo(LocalDate.parse(fechaParam.trim()));
                } catch (Exception ignored) {
                }
            }

            String fechaNacParam = request.getParameter("fechaNacimiento");
            if (fechaNacParam != null && !fechaNacParam.isBlank()) {
                try {
                    dto.setFechaNacimiento(LocalDate.parse(fechaNacParam.trim()));
                } catch (Exception ignored) {
                }
            }

            model.addAttribute("prestamo", dto);
            return "prestamo-registrar";
        }

        return "error";
    }
}
