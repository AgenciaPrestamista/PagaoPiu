package com.example.pagaopiu.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reniec_padron")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReniecPadron {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dni", length = 8, nullable = false)
    private String dni;

    @Column(name = "nombre_completo", nullable = false, length = 300)
    private String nombreCompleto;

    @Column(nullable = false, length = 50)
    private String nacionalidad;

    @Column(name = "dni_vigente", nullable = false)
    private boolean dniVigente;

    @Column(nullable = false)
    private boolean fallecido;
}
