package com.ebp08.gestion_financiera_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ebp08.gestion_financiera_backend.dto.CrearPresupuestoCategoriaRequest;
import com.ebp08.gestion_financiera_backend.dto.CrearPresupuestoGlobalRequest;
import com.ebp08.gestion_financiera_backend.dto.ResumenPresupuestoCategoriaResponse;
import com.ebp08.gestion_financiera_backend.dto.ResumenPresupuestoGlobalResponse;
import com.ebp08.gestion_financiera_backend.entity.Categoria;
import com.ebp08.gestion_financiera_backend.entity.Presupuesto;
import com.ebp08.gestion_financiera_backend.entity.Transaccion;
import com.ebp08.gestion_financiera_backend.entity.Usuario;
import com.ebp08.gestion_financiera_backend.repository.CategoriaRepository;
import com.ebp08.gestion_financiera_backend.repository.PresupuestoRepository;
import com.ebp08.gestion_financiera_backend.repository.TransaccionRepository;
import com.ebp08.gestion_financiera_backend.enums.TipoTransaccion;
import com.ebp08.gestion_financiera_backend.security.SecurityHelper;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;
    private final SecurityHelper securityHelper;
    
    // crear un presupuesto global 
    public Presupuesto crearPresupuestoGlobal(CrearPresupuestoGlobalRequest request) {
        
        Usuario usuarioAutenticado = securityHelper.obtenerUsuarioAutenticado();
        
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setUsuario(usuarioAutenticado);
        presupuesto.setMontoLimite(request.getMontoLimite());
        presupuesto.setCategoria(null); // Un presupuesto global no tiene categoría
    
        // Busca si ya existe un presupuesto global para este usuario en el mes actual
        Optional<Presupuesto> existente = presupuestoRepository
            .findByUsuarioIdAndMesActual(presupuesto.getUsuario().getId());

        if (existente.isPresent()) {
            // Si existe, actualiza el monto del mismo
            Presupuesto presupuestoActual = existente.get();
            presupuestoActual.setMontoLimite(presupuesto.getMontoLimite());
            presupuestoActual.setFechaLimite(LocalDateTime.now()
                .withDayOfMonth(LocalDateTime.now().toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59));
            return presupuestoRepository.save(presupuestoActual); // Actualiza el presupuesto existente
        }

        // Si no existe, asigna fecha límite y crea uno nuevo
        presupuesto.setFechaLimite(LocalDateTime.now()
            .withDayOfMonth(LocalDateTime.now().toLocalDate().lengthOfMonth())
            .withHour(23).withMinute(59).withSecond(59));

        return presupuestoRepository.save(presupuesto); // Crea un nuevo presupuesto global
    }
    // Crear un presupuesto específico para una categoría
    public Presupuesto crearPresupuestoCategoria(CrearPresupuestoCategoriaRequest request) {
        
        Usuario usuarioAutenticado = securityHelper.obtenerUsuarioAutenticado();
        
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setUsuario(usuarioAutenticado);
        presupuesto.setCategoria(categoriaRepository.findById(request.getIdCategoria()).orElse(null));
        presupuesto.setMontoLimite(request.getMontoLimite());

        Categoria categoria = presupuesto.getCategoria();
        Usuario usuario = presupuesto.getUsuario();

        // Validación: la categoría debe existir y debe ser del usuario autenticado
        // o una categoría global (usuario == null).
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada.");
        }

        if (categoria.getUsuario() != null &&
            !categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "La categoría no pertenece al usuario ni es global.");
        }

        // Buscar si ya existe un presupuesto para esta categoría en el mes actual
        Optional<Presupuesto> existente = presupuestoRepository
            .findByUsuarioIdAndCategoriaIdAndMesActual(usuario.getId(), categoria.getId());

        if (existente.isPresent()) {
            // Si existe, actualiza el monto y la fecha límite
            Presupuesto presupuestoActual = existente.get();
            presupuestoActual.setMontoLimite(presupuesto.getMontoLimite());
            presupuestoActual.setFechaLimite(LocalDateTime.now()
                .withDayOfMonth(LocalDateTime.now().toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59));
            return presupuestoRepository.save(presupuestoActual); // Actualiza el presupuesto existente
        }

        // Si no existe, asigna fecha límite y crea uno nuevo
        presupuesto.setFechaLimite(LocalDateTime.now()
            .withDayOfMonth(LocalDateTime.now().toLocalDate().lengthOfMonth())
            .withHour(23).withMinute(59).withSecond(59));
        
        return presupuestoRepository.save(presupuesto); // Crea un nuevo presupuesto por categoría
    }

    public ResumenPresupuestoGlobalResponse obtenerResumenPresupuestoGlobal() {

        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        Optional<Presupuesto> presupuestoActual = presupuestoRepository.findByUsuarioIdAndMesActual(idUsuario);

        if (presupuestoActual.isEmpty()) {
            return ResumenPresupuestoGlobalResponse.sinPresupuesto();
        }

        Presupuesto presupuesto = presupuestoActual.get();
        BigDecimal gastado = calcularGastoMensual(idUsuario, null);
        BigDecimal disponible = presupuesto.getMontoLimite().subtract(gastado);
        BigDecimal porcentajeUso = calcularPorcentajeUso(presupuesto.getMontoLimite(), gastado);

        return ResumenPresupuestoGlobalResponse.conPresupuesto(
            presupuesto.getMontoLimite(),
            gastado,
            disponible,
            porcentajeUso,
            presupuesto.getFechaLimite(),
            presupuesto.getId()
        );
    }

    public List<ResumenPresupuestoCategoriaResponse> obtenerResumenPresupuestoCategorias() {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        
        securityHelper.validarPropiedad(idUsuario);

        List<Categoria> categorias = categoriaRepository.findByUsuarioIsNullOrUsuarioId(idUsuario);
        List<Transaccion> transaccionesMes = obtenerTransaccionesMes(idUsuario);

        List<ResumenPresupuestoCategoriaResponse> resultado = categorias.stream()
            .map(categoria -> construirResumenCategoria(categoria, idUsuario, transaccionesMes))
            .filter(java.util.Objects::nonNull)
            .toList();

        return resultado;
    }

    private ResumenPresupuestoCategoriaResponse construirResumenCategoria(Categoria categoria, Long idUsuario, List<Transaccion> transaccionesMes) {
        Optional<Presupuesto> presupuestoActual = presupuestoRepository.findByUsuarioIdAndCategoriaIdAndMesActual(idUsuario, categoria.getId());

        if (presupuestoActual.isEmpty()) {
            return null;
        }

        Presupuesto presupuesto = presupuestoActual.get();
        BigDecimal gastado = calcularGastoPorCategoria(transaccionesMes, categoria.getId());
        BigDecimal disponible = presupuesto.getMontoLimite().subtract(gastado);
        BigDecimal porcentajeUso = calcularPorcentajeUso(presupuesto.getMontoLimite(), gastado);

        return ResumenPresupuestoCategoriaResponse.de(
            presupuesto.getId(),
            categoria.getId(),
            categoria.getNombre(),
            presupuesto.getMontoLimite(),
            gastado,
            disponible,
            porcentajeUso,
            presupuesto.getFechaLimite()
        );
    }

    private List<Transaccion> obtenerTransaccionesMes(Long idUsuario) {
        LocalDateTime inicioMes = LocalDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);

        return transaccionRepository.findByUsuarioIdAndFechaBetween(idUsuario, inicioMes, LocalDateTime.now());
    }

    private BigDecimal calcularGastoMensual(Long idUsuario, Long idCategoria) {
        return calcularGastoPorCategoria(obtenerTransaccionesMes(idUsuario), idCategoria);
    }

    private BigDecimal calcularGastoPorCategoria(List<Transaccion> transaccionesMes, Long idCategoria) {
        return transaccionesMes.stream()
            .filter(transaccion -> transaccion.getTipo() == TipoTransaccion.EGRESO)
            .filter(transaccion -> idCategoria == null || (transaccion.getCategoria() != null && idCategoria.equals(transaccion.getCategoria().getId())))
            .map(Transaccion::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcularPorcentajeUso(BigDecimal montoLimite, BigDecimal gastado) {
        if (montoLimite == null || montoLimite.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return gastado.multiply(BigDecimal.valueOf(100))
            .divide(montoLimite, 2, RoundingMode.HALF_UP);
    }

    public Optional<Presupuesto> obtenerPresupuestoPorId(Long idPresupuesto) {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        return presupuestoRepository.findByIdAndUsuarioId(idPresupuesto, idUsuario);
    }

}
