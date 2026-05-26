package com.ebp08.gestion_financiera_backend.entity;

import java.time.LocalDateTime;

import com.ebp08.gestion_financiera_backend.enums.TipoAlerta;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alerta")

@Data
@AllArgsConstructor
@NoArgsConstructor

public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "presupuesto_id")
    private Presupuesto presupuesto;

    @Enumerated(EnumType.STRING)
    private TipoAlerta tipo;

    private String mensaje;

    private LocalDateTime fecha;

}
