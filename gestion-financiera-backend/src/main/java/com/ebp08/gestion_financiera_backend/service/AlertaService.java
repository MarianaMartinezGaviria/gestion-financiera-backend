package com.ebp08.gestion_financiera_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ebp08.gestion_financiera_backend.dto.ResumenPresupuestoCategoriaResponse;
import com.ebp08.gestion_financiera_backend.dto.ResumenPresupuestoGlobalResponse;
import com.ebp08.gestion_financiera_backend.entity.Alerta;
import com.ebp08.gestion_financiera_backend.entity.Presupuesto;
import com.ebp08.gestion_financiera_backend.entity.Usuario;
import com.ebp08.gestion_financiera_backend.enums.TipoAlerta;
import com.ebp08.gestion_financiera_backend.repository.AlertaRepository;
import com.ebp08.gestion_financiera_backend.security.SecurityHelper;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AlertaService {
    
    private final AlertaRepository alertaRepository;
    private final SecurityHelper securityHelper;
    private final PresupuestoService presupuestoService;

    public List<Alerta> obtenerAlertasUsuario() {
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();

        if (idUsuario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El id del usuario no puede ser nulo.");
        }

        return alertaRepository.findByUsuarioId(idUsuario);
    }

    public void generarAlertas(){

        Usuario usuario = securityHelper.obtenerUsuarioAutenticado();

        ResumenPresupuestoGlobalResponse presupuestoGlobal = presupuestoService.obtenerResumenPresupuestoGlobal();
        if (presupuestoGlobal.isPresupuestoDefinido()) {
            evaluarYGenerarAlerta(presupuestoGlobal.getIdPresupuesto(), presupuestoGlobal.getPorcentajeUso(), usuario);
        }

        List<ResumenPresupuestoCategoriaResponse> presupuestosCategoria = presupuestoService.obtenerResumenPresupuestoCategorias();
        for (ResumenPresupuestoCategoriaResponse presupuesto : presupuestosCategoria) {
            evaluarYGenerarAlerta(presupuesto.getIdPresupuesto(), presupuesto.getPorcentajeUso(), usuario);
        }
    }

    private void evaluarYGenerarAlerta(Long idPresupuesto, BigDecimal porcentaje, Usuario usuario) {
        if (porcentaje.compareTo(BigDecimal.valueOf(100)) >= 0
                && !alertaRepository.existsByPresupuestoIdAndTipo(idPresupuesto, TipoAlerta.SOBREPASO)) {
            crearAlerta(idPresupuesto, usuario, TipoAlerta.SOBREPASO, porcentaje);

        } else if (porcentaje.compareTo(BigDecimal.valueOf(80)) >= 0
                && !alertaRepository.existsByPresupuestoIdAndTipo(idPresupuesto, TipoAlerta.PROXIMIDAD)) {
            crearAlerta(idPresupuesto, usuario, TipoAlerta.PROXIMIDAD, porcentaje);
        }
    }

    private void crearAlerta(Long idPresupuesto, Usuario usuario, TipoAlerta tipo, BigDecimal porcentaje) {
        Presupuesto presupuesto = presupuestoService.obtenerPresupuestoPorId(idPresupuesto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                                            "Presupuesto no encontrado para generar alerta."));

        Alerta alerta = new Alerta();
        alerta.setUsuario(usuario);
        alerta.setPresupuesto(presupuesto);
        alerta.setTipo(tipo);
        alerta.setMensaje(String.format("⚠️ ¡Alerta! Has alcanzado el %.2f%% de tu presupuesto %s.", porcentaje, 
            presupuesto.getCategoria() != null ? "en la categoría " + presupuesto.getCategoria().getNombre() : "global"));
        alerta.setFecha(LocalDateTime.now());
        alertaRepository.save(alerta);
    }
}
