package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface PrestamoService {

    /**
     * Registra un nuevo préstamo activado directamente como VIGENTE (método interno/tests).
     */
    Prestamo registrarPrestamo(PrestamoRegistroDTO dto, String usernameAsesor);

    /**
     * Genera el contrato imprimible en estado EN_ESPERA con número correlativo único.
     */
    Prestamo generarContrato(PrestamoRegistroDTO dto, String usernameAsesor);

    /**
     * Registra la impresión o reimpresión del contrato mientras esté en EN_ESPERA.
     */
    Prestamo marcarImpreso(Long prestamoId, String usernameAsesor);

    /**
     * Cancela el préstamo: si no fue impreso se descarta (se elimina); si fue impreso pasa a ANULADO con motivo obligatorio.
     */
    Prestamo cancelarPrestamo(Long prestamoId, String motivo, String usernameAsesor);

    /**
     * Sube el contrato firmado, valida coincidencia de número, magic bytes y hash único, y activa el préstamo a VIGENTE.
     */
    Prestamo subirContratoFirmado(Long prestamoId, String numeroContratoIngresado, MultipartFile archivo, String usernameAsesor);

    /**
     * Descarga el archivo del contrato firmado con validación de sucursal.
     */
    Resource descargarContratoFirmado(Long prestamoId, String username);

    /**
     * Lista contratos en espera de formalización (por sucursal para asesor o global para gerente).
     */
    List<Prestamo> listarContratosEnEspera(String usernameAsesor);

    /**
     * Proceso programado que anula los contratos EN_ESPERA no firmados en el día de su emisión.
     */
    void vencerContratosEnEspera();

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

    /**
     * Obtiene el préstamo y valida que el usuario tenga acceso según su sucursal (ASESOR: misma sucursal, GERENTE: global).
     */
    Prestamo obtenerContratoPorId(Long id, String username);
}
