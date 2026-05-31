package com.ebp08.gestion_financiera_backend.service;

import com.ebp08.gestion_financiera_backend.entity.RecoveryCode;
import com.ebp08.gestion_financiera_backend.entity.Usuario;
import com.ebp08.gestion_financiera_backend.repository.RecoveryCodeRepository;
import com.ebp08.gestion_financiera_backend.repository.UsuarioRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RecoveryCodeService {

    private final RecoveryCodeRepository recoveryCodeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // Método 1: Generar 10 códigos al registrarse
    public List<String> generarCodigos(Usuario usuario) {
        List<String> codigosPlanos = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String codigo = generarCodigoAleatorio();

            RecoveryCode recoveryCode = new RecoveryCode();
            recoveryCode.setUsuario(usuario);
            recoveryCode.setCodeHash(passwordEncoder.encode(codigo));
            recoveryCode.setUsado(false);
            recoveryCode.setFechaCreacion(LocalDateTime.now());
            recoveryCode.setIntentosFallidos(0);
            recoveryCode.setBloqueadoHasta(null);
            recoveryCode.setFechaReset(null);

            recoveryCodeRepository.save(recoveryCode);
            codigosPlanos.add(codigo);
        }

        return codigosPlanos; // Se muestran UNA sola vez al usuario
    }

    // Método 2: Validar código de recuperación 
    public String validarCodigo(String correo, String codigoIngresado) {

        Usuario usuario = usuarioRepository.findByCorreo(correo)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Código no válido."));

        // Verificar formato XXXX-XXXX-XXXX antes de consumir intentos
        if (!codigoIngresado.matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Formato de código incorrecto.");
        }

        List<RecoveryCode> codigosDisponibles = recoveryCodeRepository
            .findByUsuarioIdAndUsadoFalse(usuario.getId());

        // Verificar bloqueo
        Optional<RecoveryCode> bloqueado = codigosDisponibles.stream()
            .filter(rc -> rc.getBloqueadoHasta() != null &&
                          rc.getBloqueadoHasta().isAfter(LocalDateTime.now()))
            .findFirst();

        if (bloqueado.isPresent()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Recuperación bloqueada. Intenta en 30 minutos.");
        }
        codigosDisponibles.forEach(rc -> {
        if (rc.getBloqueadoHasta() != null && 
            rc.getBloqueadoHasta().isBefore(LocalDateTime.now())) {
            rc.setIntentosFallidos(0);
            rc.setBloqueadoHasta(null);
            recoveryCodeRepository.save(rc);
            }
        });



        // Verificar si el código coincide con algún hash
        Optional<RecoveryCode> codigoValido = codigosDisponibles.stream()
            .filter(rc -> passwordEncoder.matches(codigoIngresado, rc.getCodeHash()))
            .findFirst();

        if (codigoValido.isEmpty()) {
            // Registrar intento fallido
            codigosDisponibles.forEach(rc -> {
                rc.setIntentosFallidos(rc.getIntentosFallidos() + 1);
                if (rc.getIntentosFallidos() >= 5) {
                    rc.setBloqueadoHasta(LocalDateTime.now().plusMinutes(30));
                }
                recoveryCodeRepository.save(rc);
            });
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Código no válido.");
        }
        String tokenTemporal = UUID.randomUUID().toString();
        codigoValido.get().setTokenTemporal(tokenTemporal);
        recoveryCodeRepository.save(codigoValido.get());

    return tokenTemporal;

    }

    //  Método 3: Resetear contraseña
    public void resetearContrasena(String tokenTemporal, String nuevaClave) {

    // Busca el código por token temporal
    RecoveryCode codigo = recoveryCodeRepository
        .findByTokenTemporal(tokenTemporal)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Token no válido."));

    // Validar que no esté usado
    if (codigo.isUsado()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Token no válido.");
    }

    // Validar nueva contraseña
    if (nuevaClave == null || nuevaClave.trim().length() < 8) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "La contraseña debe tener mínimo 8 caracteres.");
    }

    // Actualizar contraseña del usuario
    Usuario usuario = codigo.getUsuario();
    usuario.setClave(passwordEncoder.encode(nuevaClave));
    usuarioRepository.save(usuario);

    // Invalidar el código y limpiar el token
    codigo.setUsado(true);
    codigo.setFechaReset(LocalDateTime.now());
    codigo.setTokenTemporal(null);
    recoveryCodeRepository.save(codigo);
}
    // Método privado: Generar código aleatorio XXXX-XXXX-XXXX
    private String generarCodigoAleatorio() {
        SecureRandom random = new SecureRandom();
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i == 4 || i == 8) codigo.append("-");
            codigo.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return codigo.toString();
    }
}