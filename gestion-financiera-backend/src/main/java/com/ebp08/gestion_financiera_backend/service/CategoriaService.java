package com.ebp08.gestion_financiera_backend.service;

import com.ebp08.gestion_financiera_backend.dto.ActualizarCategoriaRequest;
import com.ebp08.gestion_financiera_backend.dto.CrearCategoriaRequest;
import com.ebp08.gestion_financiera_backend.entity.Categoria;
import com.ebp08.gestion_financiera_backend.entity.Transaccion;
import com.ebp08.gestion_financiera_backend.entity.Usuario;
import com.ebp08.gestion_financiera_backend.repository.CategoriaRepository;
import com.ebp08.gestion_financiera_backend.repository.PresupuestoRepository;
import com.ebp08.gestion_financiera_backend.repository.TransaccionRepository;
import com.ebp08.gestion_financiera_backend.security.SecurityHelper;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@AllArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;
    private final PresupuestoRepository presupuestoRepository;
    private final SecurityHelper securityHelper;

    public Categoria crearCategoriaPersonalizada(CrearCategoriaRequest request) {

        Usuario usuarioAutenticado = securityHelper.obtenerUsuarioAutenticado();

        // Crear la categoría
        Categoria categoria = new Categoria();
        categoria.setNombre(request.getNombre());
        categoria.setDescripcion(request.getDescripcion());
        categoria.setUsuario(usuarioAutenticado);

        return categoriaRepository.save(categoria);
    }

    public Categoria actualizarCategoriaPersonalizada(Long idCategoria, ActualizarCategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(idCategoria)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada."));

        if (categoria.getUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No se puede actualizar una categoría global.");
        }

        securityHelper.validarPropiedad(categoria.getUsuario().getId());

        categoria.setNombre(request.getNombre().trim());
        categoria.setDescripcion(request.getDescripcion().trim());

        return categoriaRepository.save(categoria);
    }

    public List<Categoria> obtenerCategoriasUsuario() {

        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();

        if (idUsuario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del usuario no puede ser nulo.");
        }
        
        // Validar que el usuario autenticado sea quien solicita sus propias categorías
        securityHelper.validarPropiedad(idUsuario);
        
        return categoriaRepository.findByUsuarioIsNullOrUsuarioId(idUsuario);
    }

    @Transactional // Asegura que todas las operaciones dentro de este método se ejecuten como una sola transacción
    public void eliminarCategoriaPersonalizada(Long idCategoria) {
        Categoria categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada."));

        if (categoria.getUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No se puede eliminar una categoría global.");
        }

        securityHelper.validarPropiedad(categoria.getUsuario().getId());

        Categoria categoriaPorDefecto = obtenerCategoriaPorDefecto();

        List<Transaccion> transacciones = transaccionRepository.findByCategoriaId(idCategoria);
        transacciones.forEach(t -> t.setCategoria(categoriaPorDefecto));
        transaccionRepository.saveAll(transacciones);

        presupuestoRepository.deleteByCategoriaId(idCategoria);
        categoriaRepository.delete(categoria);
    }

    private Categoria obtenerCategoriaPorDefecto() {
        return categoriaRepository.findByNombreIgnoreCaseAndUsuarioIsNull(com.ebp08.gestion_financiera_backend.util.AppConstants.DEFAULT_CATEGORY_NAME)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format(com.ebp08.gestion_financiera_backend.util.AppConstants.DEFAULT_CATEGORY_NOT_FOUND,
                            com.ebp08.gestion_financiera_backend.util.AppConstants.DEFAULT_CATEGORY_NAME)));
    }
}
