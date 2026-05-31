package com.ebp08.gestion_financiera_backend.controller;

import com.ebp08.gestion_financiera_backend.dto.ActualizarClaveRequest;
import org.springframework.web.bind.annotation.*;

import com.ebp08.gestion_financiera_backend.dto.LoginRequest;
import com.ebp08.gestion_financiera_backend.dto.RegistroRequest;
import com.ebp08.gestion_financiera_backend.dto.RecoverRequest;
import com.ebp08.gestion_financiera_backend.dto.ResetPasswordRequest;   
import com.ebp08.gestion_financiera_backend.dto.RegistroResponse;
import com.ebp08.gestion_financiera_backend.service.RecoveryCodeService;
import com.ebp08.gestion_financiera_backend.service.UsuarioService;


import lombok.AllArgsConstructor;

import java.util.Optional;

import org.springframework.http.ResponseEntity;


@RestController
@RequestMapping("/api/usuarios")
@AllArgsConstructor
public class UsuarioController {
    
    private final UsuarioService usuarioService;
    private final RecoveryCodeService recoveryCodeService;

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrarUsuario(@RequestBody RegistroRequest registroRequest) {
        RegistroResponse creado = usuarioService.crearUsuario(registroRequest);
        return ResponseEntity.status(201).body(creado); // 201: Created
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest loginRequest) {
        Optional<String> token = usuarioService.verificarCredenciales(loginRequest.getCorreo(), loginRequest.getClave());
        return ResponseEntity.status(200).body(token.orElse(null));
        // Devuelve el 200: OK y el JWT que expirará en 30 días
    }

    @PostMapping("/logout")
    public ResponseEntity<String> cerrarSesion(){
        return ResponseEntity.ok("Sesion cerrada exitosamente.");
        // Aquí desde el Front se debería borrar el JWT
    }

    @PostMapping("/recover")
    public ResponseEntity<String> recuperarContrasena(@RequestBody RecoverRequest request) {
    String tokenTemporal = recoveryCodeService.validarCodigo(
        request.getCorreo(), request.getCodigo());
    return ResponseEntity.ok(tokenTemporal); // Frontend lo guarda
}

@PostMapping("/reset-password")
    public ResponseEntity<String> resetearContrasena(@RequestBody ResetPasswordRequest request) {
        recoveryCodeService.resetearContrasena(
        request.getTokenTemporal(),
        request.getNuevaClave()
    );
    return ResponseEntity.ok("Contraseña actualizada exitosamente.");
}

    @PutMapping("/actualizarClave")
    public ResponseEntity<String> actualizarClave(@RequestBody ActualizarClaveRequest request){
        String confirmacion = usuarioService.actualizarClave(request.getClaveAntigua(), request.getClaveNueva());
        return ResponseEntity.ok(confirmacion);
    }
}