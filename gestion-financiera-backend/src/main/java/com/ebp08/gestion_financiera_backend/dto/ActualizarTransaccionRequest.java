package com.ebp08.gestion_financiera_backend.dto;

import com.ebp08.gestion_financiera_backend.enums.TipoTransaccion;
import lombok.Data;

@Data
public class ActualizarTransaccionRequest {

    private Long idCategoria; // Opcional: permite mover la transacción a otra categoría.
    private TipoTransaccion tipo; // Opcional: permite corregir si era ingreso o egreso.
    private String descripcion; // Opcional: si llega vacío, en servicio se guarda como "".
    private String monto; // Opcional: se valida y convierte con MoneyParser.
}