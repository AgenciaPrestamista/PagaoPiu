package com.example.pagaopiu.service.impl;

import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.service.ContratoDocumentoService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class ContratoDocumentoServiceImpl implements ContratoDocumentoService {

    private final String emisorRazonSocial;
    private final String emisorRuc;
    private final String emisorRepresentante;
    private final String emisorPartidaRegistral;
    private final String emisorCargoRepresentante;
    private final String firmaGerentePath;

    public ContratoDocumentoServiceImpl(
            @Value("${app.emisor.razon-social:Prestamos Castillo}") String emisorRazonSocial,
            @Value("${app.emisor.ruc:10190981245}") String emisorRuc,
            @Value("${app.emisor.representante:Edward Fernando Castillo Robles}") String emisorRepresentante,
            @Value("${app.emisor.partida-registral:Oficina Registral – Sede Nº V Trujillo}") String emisorPartidaRegistral,
            @Value("${app.emisor.cargo-representante:Gerente General}") String emisorCargoRepresentante,
            @Value("${app.contrato.firma-gerente-path:}") String firmaGerentePath) {
        this.emisorRazonSocial = emisorRazonSocial;
        this.emisorRuc = emisorRuc;
        this.emisorRepresentante = emisorRepresentante;
        this.emisorPartidaRegistral = emisorPartidaRegistral;
        this.emisorCargoRepresentante = emisorCargoRepresentante;
        this.firmaGerentePath = firmaGerentePath;
    }

    @Override
    public String getEmisorRazonSocial() {
        return emisorRazonSocial;
    }

    @Override
    public Path validarYObtenerRutaFirma() {
        if (firmaGerentePath == null || firmaGerentePath.trim().isEmpty()) {
            return null;
        }
        try {
            Path path = Paths.get(firmaGerentePath.trim());
            if (!Files.exists(path) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                return null;
            }
            long size = Files.size(path);
            if (size <= 0 || size > 1024 * 1024) { // Máximo 1 MB
                return null;
            }
            byte[] header = new byte[8];
            try (InputStream is = Files.newInputStream(path)) {
                int read = is.read(header);
                if (read < 3) {
                    return null;
                }
                boolean isPng = read >= 8
                        && header[0] == (byte) 0x89
                        && header[1] == (byte) 0x50
                        && header[2] == (byte) 0x4E
                        && header[3] == (byte) 0x47
                        && header[4] == (byte) 0x0D
                        && header[5] == (byte) 0x0A
                        && header[6] == (byte) 0x1A
                        && header[7] == (byte) 0x0A;
                boolean isJpg = header[0] == (byte) 0xFF
                        && header[1] == (byte) 0xD8
                        && header[2] == (byte) 0xFF;
                if (!isPng && !isJpg) {
                    return null;
                }
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Resource obtenerRecursoFirmaGerente() {
        Path path = validarYObtenerRutaFirma();
        if (path == null) {
            return null;
        }
        try {
            return new UrlResource(path.toUri());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean tieneFirmaGerente() {
        return validarYObtenerRutaFirma() != null;
    }

    @Override
    public String obtenerContentTypeFirma() {
        Path path = validarYObtenerRutaFirma();
        if (path == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
    }

    @Override
    public ContratoTextoDTO generarTextoContrato(Prestamo prestamo) {
        if (prestamo == null) {
            throw new IllegalArgumentException("El préstamo no puede ser nulo.");
        }
        if (prestamo.getSucursal() == null) {
            throw new IllegalStateException("El préstamo debe contar obligatoriamente con una sucursal asignada.");
        }

        String ciudad = prestamo.getSucursal().getNombre();
        LocalDate fechaEmision = prestamo.getFechaPrestamo();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "PE"));
        String fechaFormateada = fechaEmision.format(formatter);

        String fragmentoCliente;
        String dniDoc = (prestamo.getCliente() != null && prestamo.getCliente().getDni() != null)
                ? prestamo.getCliente().getDni().trim() : "";

        if (dniDoc.length() == 11 && dniDoc.startsWith("20")) {
            // Empresa / Persona Jurídica (RUC 20) -> Exige snapshot completo sin datos inventados
            if (prestamo.getEmpresaRazonSocial() == null || prestamo.getEmpresaRazonSocial().isBlank()
                    || prestamo.getEmpresaRepresentanteNombre() == null || prestamo.getEmpresaRepresentanteNombre().isBlank()
                    || prestamo.getEmpresaRepresentanteDocumento() == null || prestamo.getEmpresaRepresentanteDocumento().isBlank()
                    || prestamo.getEmpresaRepresentanteCargo() == null || prestamo.getEmpresaRepresentanteCargo().isBlank()) {
                throw new NegocioException("Este contrato no tiene registrados los datos del representante legal; debe generarse un contrato nuevo.");
            }

            String razonSocial = prestamo.getEmpresaRazonSocial().trim();
            String ruc = prestamo.getEmpresaRuc() != null ? prestamo.getEmpresaRuc().trim() : dniDoc;
            String cargo = prestamo.getEmpresaRepresentanteCargo().trim();
            String repNombre = prestamo.getEmpresaRepresentanteNombre().trim();
            String repDoc = prestamo.getEmpresaRepresentanteDocumento().trim();

            fragmentoCliente = String.format(
                    "EL CLIENTE %s, con RUC Nº %s, debidamente representada por su %s, %s, identificado con DNI Nº %s.",
                    razonSocial, ruc, cargo, repNombre, repDoc
            );
        } else if (dniDoc.length() == 11 && dniDoc.startsWith("10")) {
            // Persona Natural con Negocio (RUC 10) -> DNI derivado de dígitos 3 a 10
            String dniDerivado = dniDoc.substring(2, 10);
            String nombre = prestamo.getCliente() != null ? prestamo.getCliente().getNombreCompleto() : "";
            fragmentoCliente = String.format(
                    "EL CLIENTE %s, identificado con DNI Nº %s y con RUC Nº %s.",
                    nombre, dniDerivado, dniDoc
            );
        } else {
            // Persona Natural con DNI
            String nombre = prestamo.getCliente() != null ? prestamo.getCliente().getNombreCompleto() : "";
            fragmentoCliente = String.format(
                    "EL CLIENTE %s, con DNI Nº %s.",
                    nombre, dniDoc
            );
        }

        String plantilla;
        try (InputStream is = getClass().getResourceAsStream("/contratos/contrato_mutuo_template.txt")) {
            if (is == null) {
                throw new IllegalStateException("No se encontró el recurso de plantilla /contratos/contrato_mutuo_template.txt");
            }
            plantilla = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error al leer la plantilla de contrato de mutuo", e);
        }

        String separador = "<!-- BLOQUE_FIRMAS -->";
        String parteCuerpo = plantilla;
        String parteFirmas = "";
        if (plantilla.contains(separador)) {
            String[] partes = plantilla.split(separador, 2);
            parteCuerpo = partes[0].trim();
            parteFirmas = partes[1].trim();
        }

        String numeroContrato = prestamo.getNumeroContrato() != null ? prestamo.getNumeroContrato() : "";

        parteCuerpo = parteCuerpo
                .replace("{{NUMERO_CONTRATO}}", numeroContrato)
                .replace("{{EMISOR_RAZON_SOCIAL}}", emisorRazonSocial)
                .replace("{{EMISOR_RUC}}", emisorRuc)
                .replace("{{EMISOR_REPRESENTANTE}}", emisorRepresentante)
                .replace("{{EMISOR_PARTIDA}}", emisorPartidaRegistral)
                .replace("{{FRAGMENTO_CLIENTE}}", fragmentoCliente);

        parteFirmas = parteFirmas
                .replace("{{CIUDAD_CONTRATO}}", ciudad)
                .replace("{{FECHA_EMISION_FORMATEADA}}", fechaFormateada);

        return new ContratoTextoDTO(parteCuerpo, parteFirmas, ciudad, fechaFormateada, emisorRazonSocial);
    }
}
