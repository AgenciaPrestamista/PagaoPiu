package com.example.pagaopiu.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.pagaopiu.exception.NegocioException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContratoFileStorageService {

    private final Path storageDirectory;
    private final long maxSizeBytes;
    private final String storageProvider;
    private final Cloudinary cloudinary;

    public ContratoFileStorageService(
            @Value("${app.contrato.storage-dir:./uploads/contratos}") String storageDir,
            @Value("${app.contrato.max-size-bytes:5242880}") long maxSizeBytes,
            @Value("${app.contrato.storage-provider:local}") String storageProvider,
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret) {
        Path dir;
        try {
            dir = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
        } catch (Exception e) {
            dir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "uploads", "contratos").toAbsolutePath().normalize();
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {
            }
        }
        this.storageDirectory = dir;
        this.maxSizeBytes = maxSizeBytes;
        this.storageProvider = storageProvider != null ? storageProvider.trim().toLowerCase() : "local";
        if (cloudName != null && !cloudName.isBlank() && apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true));
        } else {
            this.cloudinary = null;
        }
    }

    public record ContratoFileResult(String nombreOriginal, String rutaRelativa, String hashSha256, String contentType) {}

    public ContratoFileResult almacenarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("El archivo del contrato firmado es obligatorio.");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new NegocioException("El archivo excede el tamaño máximo permitido de 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new NegocioException("Error al leer el contenido del archivo: " + e.getMessage());
        }

        String contentType = detectarContentTypePorMagicBytes(bytes);
        String hashSha256 = calcularSha256(bytes);

        String extension = switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new NegocioException("Tipo de archivo no permitido.");
        };

        String originalName = sanitizarNombreOriginal(file.getOriginalFilename(), extension);
        String rutaAlmacenamiento;

        if ("cloudinary".equalsIgnoreCase(this.storageProvider)) {
            if (this.cloudinary == null) {
                throw new NegocioException("El proveedor Cloudinary no está configurado adecuadamente con API Key y Secret.");
            }
            try {
                String publicId = UUID.randomUUID().toString();
                Map<?, ?> uploadResult = this.cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                        "resource_type", "auto",
                        "folder", "pagaopiu/contratos",
                        "public_id", publicId));
                rutaAlmacenamiento = (String) uploadResult.get("secure_url");
            } catch (Exception e) {
                throw new NegocioException("Error al almacenar el contrato en Cloudinary: " + e.getMessage());
            }
        } else {
            String nombreFisico = UUID.randomUUID() + extension;
            Path destino = storageDirectory.resolve(nombreFisico).normalize();

            if (!destino.startsWith(storageDirectory)) {
                throw new NegocioException("Ruta de destino inválida para el archivo.");
            }

            try {
                Files.write(destino, bytes);
            } catch (IOException e) {
                throw new NegocioException("Error al persistir el archivo en disco: " + e.getMessage());
            }

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                Files.deleteIfExists(destino);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                });
            }
            rutaAlmacenamiento = nombreFisico;
        }

        return new ContratoFileResult(originalName, rutaAlmacenamiento, hashSha256, contentType);
    }

    public Resource cargarComoRecurso(String nombreFisico) {
        try {
            if (nombreFisico != null && (nombreFisico.startsWith("http://") || nombreFisico.startsWith("https://"))) {
                return new UrlResource(URI.create(nombreFisico));
            }
            Path archivo = storageDirectory.resolve(nombreFisico).normalize();
            if (!archivo.startsWith(storageDirectory) || !Files.exists(archivo)) {
                throw new NegocioException("El archivo del contrato no existe en el almacenamiento.");
            }
            return new UrlResource(archivo.toUri());
        } catch (Exception e) {
            throw new NegocioException("Error al acceder al archivo del contrato: " + e.getMessage());
        }
    }

    private String sanitizarNombreOriginal(String originalFilename, String extensionFallback) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "contrato_firmado" + extensionFallback;
        }
        String base = Paths.get(originalFilename).getFileName().toString().trim();
        if (base.length() > 255) {
            int extIdx = base.lastIndexOf('.');
            String ext = (extIdx != -1) ? base.substring(extIdx) : extensionFallback;
            int maxBase = 255 - ext.length();
            base = base.substring(0, Math.max(1, maxBase)) + ext;
        }
        return base;
    }

    private String detectarContentTypePorMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new NegocioException("El archivo subido está corrupto o vacío.");
        }
        // PDF: %PDF- (0x25 0x50 0x44 0x46)
        if (bytes.length >= 5 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46 && bytes[4] == 0x2D) {
            return "application/pdf";
        }
        // JPEG: FF D8 FF
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }

        throw new NegocioException("Formato de archivo no válido. Solo se admiten archivos genuinos en formato PDF, JPG o PNG.");
    }

    private String calcularSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new NegocioException("Algoritmo de cálculo de hash no disponible: " + e.getMessage());
        }
    }
}
