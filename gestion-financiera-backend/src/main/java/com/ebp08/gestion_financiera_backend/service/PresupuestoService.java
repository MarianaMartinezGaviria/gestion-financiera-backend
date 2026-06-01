package com.ebp08.gestion_financiera_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        LocalDateTime inicioMes = inicioMesActual();
        LocalDateTime finMes = finMesActual();
        Optional<Presupuesto> existente = presupuestoRepository
            .findByUsuarioIdAndFechaLimiteBetweenAndCategoriaIsNull(
                presupuesto.getUsuario().getId(),
                inicioMes,
                finMes
            );

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
            .findByUsuarioIdAndCategoriaIdAndFechaLimiteBetween(
                usuario.getId(),
                categoria.getId(),
                inicioMesActual(),
                finMesActual()
            );

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
        Optional<Presupuesto> presupuestoActual = obtenerPresupuestoGlobalUsuario();

        if (presupuestoActual.isEmpty()) {
            return ResumenPresupuestoGlobalResponse.sinPresupuesto();
        }

        Presupuesto presupuesto = presupuestoActual.get();
        BigDecimal gastado = calcularGastoPresupuesto(presupuesto);
        BigDecimal disponible = presupuesto.getMontoLimite().subtract(gastado);
        BigDecimal porcentajeUso = calcularPorcentajeUsoPresupuesto(presupuesto);

        return ResumenPresupuestoGlobalResponse.conPresupuesto(
            presupuesto.getMontoLimite(),
            gastado,
            disponible,
            porcentajeUso,
            presupuesto.getFechaLimite(),
            presupuesto.getId()
        );
    }

    // Trae el presupuesto global actual del usuario autenticado para uso interno entre servicios.
    public Optional<Presupuesto> obtenerPresupuestoGlobalUsuario() {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        return presupuestoRepository.findByUsuarioIdAndFechaLimiteBetweenAndCategoriaIsNull(
            idUsuario,
            inicioMesActual(),
            finMesActual()
        );
    }

    // Trae los presupuestos por categoria del usuario autenticado para uso interno entre servicios.
    public List<Presupuesto> obtenerResumenPresupuestoCategorias() {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        
        securityHelper.validarPropiedad(idUsuario);
        LocalDateTime inicioMes = inicioMesActual();
        LocalDateTime finMes = finMesActual();

        List<Categoria> categorias = categoriaRepository.findByUsuarioIsNullOrUsuarioId(idUsuario);

        return categorias.stream()
            .map(categoria -> presupuestoRepository
                .findByUsuarioIdAndCategoriaIdAndFechaLimiteBetween(
                    idUsuario,
                    categoria.getId(),
                    inicioMes,
                    finMes
                )
                .orElse(null))
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    // Trae el resumen de presupuestos por categoria para respuestas del controller.
    public List<ResumenPresupuestoCategoriaResponse> obtenerResumenPresupuestoCategoriasResponse() {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        List<Transaccion> transaccionesMes = obtenerTransaccionesMes(idUsuario);

        return obtenerResumenPresupuestoCategorias().stream()
            .map(presupuesto -> construirResumenCategoriaResponse(presupuesto, transaccionesMes))
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private ResumenPresupuestoCategoriaResponse construirResumenCategoriaResponse(Presupuesto presupuesto, List<Transaccion> transaccionesMes) {
        if (presupuesto.getCategoria() == null) {
            return null;
        }

        BigDecimal gastado = calcularGastoPorCategoria(transaccionesMes, presupuesto.getCategoria().getId());
        BigDecimal disponible = presupuesto.getMontoLimite().subtract(gastado);
        BigDecimal porcentajeUso = calcularPorcentajeUso(presupuesto.getMontoLimite(), gastado);

        return ResumenPresupuestoCategoriaResponse.de(
            presupuesto.getId(),
            presupuesto.getCategoria().getId(),
            presupuesto.getCategoria().getNombre(),
            presupuesto.getMontoLimite(),
            gastado,
            disponible,
            porcentajeUso,
            presupuesto.getFechaLimite()
        );
    }

    // Calcula el porcentaje de uso del presupuesto para uso interno entre servicios.
    public BigDecimal calcularPorcentajeUsoPresupuesto(Presupuesto presupuesto) {
        BigDecimal gastado = calcularGastoPresupuesto(presupuesto);
        return calcularPorcentajeUso(presupuesto.getMontoLimite(), gastado);
    }

    // Calcula el gasto acumulado del mes asociado a un presupuesto (global o por categoria).
    public BigDecimal calcularGastoPresupuesto(Presupuesto presupuesto) {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        Long idCategoria = presupuesto.getCategoria() != null ? presupuesto.getCategoria().getId() : null;
        return calcularGastoMensual(idUsuario, idCategoria);
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

    private LocalDateTime inicioMesActual() {
        return LocalDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    }

    private LocalDateTime finMesActual() {
        LocalDateTime ahora = LocalDateTime.now();
        return ahora
            .withDayOfMonth(ahora.toLocalDate().lengthOfMonth())
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(999_999_999);
    }

    public Optional<Presupuesto> obtenerPresupuestoPorId(Long idPresupuesto) {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        securityHelper.validarPropiedad(idUsuario);

        return presupuestoRepository.findByIdAndUsuarioId(idPresupuesto, idUsuario);
    }

}
