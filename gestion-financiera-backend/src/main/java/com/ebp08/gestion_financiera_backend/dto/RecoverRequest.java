package com.ebp08.gestion_financiera_backend.dto;
import lombok.Data;

@Data
public class RecoverRequest {
    private String correo;
    private String codigo;
}
