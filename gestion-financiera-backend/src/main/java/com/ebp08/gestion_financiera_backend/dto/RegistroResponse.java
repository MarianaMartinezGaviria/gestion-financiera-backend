package com.ebp08.gestion_financiera_backend.dto;

import com.ebp08.gestion_financiera_backend.entity.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RegistroResponse {
    private Usuario usuario;
    private List<String> codigosRecuperacion;
}
