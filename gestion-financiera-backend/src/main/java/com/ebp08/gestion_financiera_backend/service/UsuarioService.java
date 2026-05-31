package com.ebp08.gestion_financiera_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ebp08.gestion_financiera_backend.dto.RegistroRequest;
import com.ebp08.gestion_financiera_backend.entity.Usuario;
import com.ebp08.gestion_financiera_backend.enums.Estado;
import com.ebp08.gestion_financiera_backend.repository.UsuarioRepository;
import com.ebp08.gestion_financiera_backend.security.JwtUtil;
import com.ebp08.gestion_financiera_backend.security.SecurityHelper;
import lombok.AllArgsConstructor;
import com.ebp08.gestion_financiera_backend.dto.RegistroResponse;

@Service
@AllArgsConstructor
public class UsuarioService {
    
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecurityHelper securityHelper;
    private final RecoveryCodeService recoveryCodeService;
   

    public RegistroResponse crearUsuario(RegistroRequest request) {

        //  Verificamos que el correo no esté registrado por otro usuario
        if (!usuarioRepository.existsByCorreo(request.getCorreo())){
            // 1. Creamos un nuevo usuario con los datos del request y estado ACTIVO por defecto
            Usuario usuario = new Usuario();
            usuario.setNombre(request.getNombre());
            usuario.setCorreo(request.getCorreo());
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setEstado(Estado.ACTIVO);
            
            //  Encriptamos y guardamos la clave del usuario
            String claveEncriptada = passwordEncoder.encode(request.getClave());
            usuario.setClave(claveEncriptada);

            // Guardamos el usuario en la base de datos
            Usuario usuarioGuardado = usuarioRepository.save(usuario);
            
            //Generamos los codigos de recuperación
            List<String> codigosRecuperacion = recoveryCodeService.generarCodigos(usuarioGuardado);

            
            
            return  new RegistroResponse(usuarioGuardado, codigosRecuperacion);

        } else {
            throw new ResponseStatusException
                                (HttpStatus.CONFLICT, 
                                "El correo " + request.getCorreo() + " ya se encuentra registrado.");
        }
        
    }

    public Optional<String> verificarCredenciales(String correo, String clave) {
        Optional<Usuario> usuarioOpcional = usuarioRepository.findByCorreo(correo);
        if (usuarioOpcional.isPresent()){ // Si el Optional tiene un valor, el usuario existe
            Usuario usuario = usuarioOpcional.get();
            
            if (passwordEncoder.matches(clave, usuario.getClave())) {
                
                if (usuario.getEstado() == Estado.INACTIVO) { // Usuario bloqueado o inactivo
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo o bloqueado");
                }

                // Si existe, las credenciales son correctas y el usuario está activo, generamos un token JWT
                String token = jwtUtil.generarToken(correo); 
                return Optional.of(token);
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El correo " + correo + " no está registrado.");
        }
    }

    public String actualizarClave(String claveAntigua, String claveNueva){
        Usuario usuarioAutenticado = securityHelper.obtenerUsuarioAutenticado();

        if (passwordEncoder.matches(claveAntigua, usuarioAutenticado.getClave())){
            // "matches" compara (textoPlano, textoEncriptado)
            if (!passwordEncoder.matches(claveNueva, usuarioAutenticado.getClave())){

                String claveEncriptada = passwordEncoder.encode(claveNueva);
                usuarioAutenticado.setClave(claveEncriptada);
                usuarioRepository.save(usuarioAutenticado);
                return "Contraseña actualizada exitosamente.";

            } throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "La nueva contraseña no puede ser igual a la anterior.");
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta.");
        }
    }
}
