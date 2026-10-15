package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import java.util.List;

public interface PrestamoService {

    /**
     * Registra un nuevo préstamo y genera su cronograma de cuotas según las reglas R1 a R9.
     */
    Prestamo registrarPrestamo(PrestamoRegistroDTO dto, String usernameAsesor);

    /**
     * Obtiene los datos de un préstamo por su identificador.
     */
    Prestamo obtenerPrestamoPorId(Long id);

    /**
     * Obtiene las cuotas de un préstamo ordenadas por número ascendente.
     */
    List<Cuota> obtenerCuotasPorPrestamoId(Long prestamoId);

    /**
     * Obtiene todos los préstamos de un cliente dado su número de DNI.
     */
    List<Prestamo> obtenerPrestamosPorClienteDni(String dni);

    /**
     * Lista los préstamos más recientes registrados en el sistema.
     */
    List<Prestamo> listarPrestamosRecientes();
}
