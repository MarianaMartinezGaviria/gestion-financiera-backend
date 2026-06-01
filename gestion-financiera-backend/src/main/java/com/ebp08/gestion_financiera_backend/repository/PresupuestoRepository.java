package com.ebp08.gestion_financiera_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ebp08.gestion_financiera_backend.entity.Presupuesto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

   List<Presupuesto> findByUsuarioId(Long idUsuario);


    // Consulta personalizada para encontrar un presupuesto global dentro del rango del mes actual.
   @Query("SELECT p FROM Presupuesto p WHERE p.usuario.id = :usuarioId " +
      "AND p.categoria IS NULL " +
      "AND p.fechaLimite BETWEEN :inicioMes AND :finMes")

   Optional<Presupuesto> findByUsuarioIdAndFechaLimiteBetweenAndCategoriaIsNull(
      @Param("usuarioId") Long usuarioId,
      @Param("inicioMes") LocalDateTime inicioMes,
      @Param("finMes") LocalDateTime finMes);

    // Consulta personalizada para encontrar un presupuesto por categoría en el mes actual
   @Query("SELECT p FROM Presupuesto p WHERE p.usuario.id = :usuarioId " +
      "AND p.categoria.id = :categoriaId " +
      "AND p.fechaLimite BETWEEN :inicioMes AND :finMes")


   Optional<Presupuesto> findByUsuarioIdAndCategoriaIdAndFechaLimiteBetween(
      @Param("usuarioId") Long usuarioId,
      @Param("categoriaId") Long categoriaId,
      @Param("inicioMes") LocalDateTime inicioMes,
      @Param("finMes") LocalDateTime finMes); // @param para pasar el id de la categoría como parámetro a la consulta personalizada

   void deleteByCategoriaId(Long idCategoria);


   Optional<Presupuesto> findByIdAndUsuarioId(Long idPresupuesto, Long idUsuario);

}
