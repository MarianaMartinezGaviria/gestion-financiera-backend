package com.ebp08.gestion_financiera_backend.dto;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String tokenTemporal;
    private String nuevaClave;
}
