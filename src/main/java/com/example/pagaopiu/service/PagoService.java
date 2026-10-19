package com.example.pagaopiu.service;

import com.example.pagaopiu.model.entity.Pago;
import java.util.List;

public interface PagoService {

    /**
     * R10: Paga la siguiente cuota PENDIENTE más antigua del préstamo.
     * R11: La sucursal del pago se toma del asesor autenticado.
     * Si es la última cuota pendiente, el préstamo pasa a CANCELADO.
     */
    Pago pagarSiguienteCuota(Long prestamoId, String usernameAsesor);

    /**
     * R12: Cancela anticipadamente el préstamo pagando la suma de todas las cuotas pendientes.
     * R11: La sucursal del pago se toma del asesor autenticado.
     * Todas las cuotas pasan a PAGADA y el préstamo a CANCELADO.
     */
    Pago cancelarAnticipado(Long prestamoId, String usernameAsesor);

    /**
     * Obtiene el listado de pagos registrados para un préstamo.
     */
    List<Pago> obtenerPagosPorPrestamoId(Long prestamoId);

    /**
     * Obtiene un pago por su ID con sus relaciones cargadas.
     */
    Pago obtenerPagoPorId(Long id);
}
