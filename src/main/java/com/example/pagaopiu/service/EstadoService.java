package com.example.pagaopiu.service;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Prestamo;

public interface EstadoService {

    /**
     * Recalcula el estado de un préstamo (VIGENTE, MOROSO, EXTRAJUDICIAL).
     * Si ya es CANCELADO o EXTRAJUDICIAL, se respetan las reglas de no reversión automática.
     */
    void recalcularPrestamo(Prestamo prestamo);

    /**
     * Recalcula el estado del cliente según el peor estado de sus préstamos activos.
     * EXTRAJUDICIAL > MOROSO > ACTIVO.
     */
    void recalcularCliente(Cliente cliente);

    /**
     * Ejecuta el recálculo masivo de todos los préstamos no cancelados y de todos los clientes.
     */
    void recalcularTodos();
}
