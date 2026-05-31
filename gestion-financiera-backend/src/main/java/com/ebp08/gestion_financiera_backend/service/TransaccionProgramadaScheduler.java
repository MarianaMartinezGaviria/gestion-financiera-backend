package com.ebp08.gestion_financiera_backend.service;

import com.ebp08.gestion_financiera_backend.entity.Transaccion;
import com.ebp08.gestion_financiera_backend.entity.TransaccionProgramada;
import com.ebp08.gestion_financiera_backend.enums.Estado;
import com.ebp08.gestion_financiera_backend.repository.TransaccionProgramadaRepository;
import com.ebp08.gestion_financiera_backend.repository.TransaccionRepository;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransaccionProgramadaScheduler {

    private final TransaccionProgramadaRepository transaccionProgramadaRepository;
    private final TransaccionRepository transaccionRepository;

    @Scheduled(cron = "0 0 2 * * *") // Se ejecuta cada dia a las 2:00 AM
    public void procesarTransaccionesProgramadas() {
        List<TransaccionProgramada> activas = transaccionProgramadaRepository
            .findByEstado(Estado.ACTIVO);

        for (TransaccionProgramada tp : activas) {
            if (debeProcesarse(tp)) {
                ejecutarTransaccion(tp);
            }
        }
    }
    
    private boolean debeProcesarse(TransaccionProgramada tp) {

        LocalDate hoy = LocalDate.now();

        // Si tiene fecha fin y ya pasó → no procesar
        if (tp.getFechaFin() != null && hoy.isAfter(tp.getFechaFin())) {
            return false;
        }

        // Si nunca se ha ejecutado → ejecutar
        if (tp.getUltimaEjecucion() == null) {
            return true;
        }

        LocalDate ultimaEjecucion = tp.getUltimaEjecucion().toLocalDate();

        // Verificar según frecuencia
        return switch (tp.getFrecuencia()) {
            case DIARIA -> !ultimaEjecucion.isEqual(hoy);
            case SEMANAL -> ultimaEjecucion.plusDays(7).isEqual(hoy) ||
                             ultimaEjecucion.plusDays(7).isBefore(hoy);
            case MENSUAL -> ultimaEjecucion.plusMonths(1).isEqual(hoy) ||
                             ultimaEjecucion.plusMonths(1).isBefore(hoy);
        };
    }

    private void ejecutarTransaccion(TransaccionProgramada tp) {
        Transaccion transaccion = new Transaccion();
        transaccion.setUsuario(tp.getUsuario());
        transaccion.setCategoria(tp.getCategoria());
        transaccion.setTipo(tp.getTipo());
        transaccion.setMonto(tp.getMonto());
        transaccion.setFecha(LocalDateTime.now());
        transaccion.setDescripcion(tp.getDescripcion());

        transaccionRepository.save(transaccion);

        tp.setUltimaEjecucion(LocalDateTime.now());
        transaccionProgramadaRepository.save(tp);
    }
}