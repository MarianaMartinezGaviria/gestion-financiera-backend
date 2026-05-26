package com.ebp08.gestion_financiera_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenPresupuestoGlobalResponse {

    private boolean presupuestoDefinido;
    private Long idPresupuesto;
    private BigDecimal montoLimite;
    private BigDecimal gastado;
    private BigDecimal disponible;
    private BigDecimal porcentajeUso;
    private LocalDateTime fechaLimite;
    private String mensaje;

    public static ResumenPresupuestoGlobalResponse sinPresupuesto() {
        ResumenPresupuestoGlobalResponse response = new ResumenPresupuestoGlobalResponse();
        response.setPresupuestoDefinido(false);
        response.setMensaje("No tienes un presupuesto global definido.");
        return response;
    }

    public static ResumenPresupuestoGlobalResponse conPresupuesto(BigDecimal montoLimite, BigDecimal gastado, 
                                                                    BigDecimal disponible, BigDecimal porcentajeUso, 
                                                                    LocalDateTime fechaLimite, Long idPresupuesto) {
        ResumenPresupuestoGlobalResponse response = new ResumenPresupuestoGlobalResponse();
        response.setPresupuestoDefinido(true);
        response.setMontoLimite(montoLimite);
        response.setGastado(gastado);
        response.setDisponible(disponible);
        response.setPorcentajeUso(porcentajeUso);
        response.setFechaLimite(fechaLimite);
        response.setIdPresupuesto(idPresupuesto);
        return response;
    }
}