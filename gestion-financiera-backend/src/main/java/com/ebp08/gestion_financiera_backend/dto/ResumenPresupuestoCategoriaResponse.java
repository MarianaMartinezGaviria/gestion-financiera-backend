package com.ebp08.gestion_financiera_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenPresupuestoCategoriaResponse {

    private Long idPresupuesto;
    private Long idCategoria;
    private String nombreCategoria;
    private BigDecimal montoLimite;
    private BigDecimal gastado;
    private BigDecimal disponible;
    private BigDecimal porcentajeUso;
    private LocalDateTime fechaLimite;

    public static ResumenPresupuestoCategoriaResponse de(Long idPresupuesto, Long idCategoria, String nombreCategoria, BigDecimal montoLimite, 
            BigDecimal gastado, BigDecimal disponible, BigDecimal porcentajeUso, LocalDateTime fechaLimite) {
        return new ResumenPresupuestoCategoriaResponse(idPresupuesto, idCategoria, nombreCategoria, montoLimite, gastado, disponible, porcentajeUso, fechaLimite);
    }
}