package com.ebp08.gestion_financiera_backend.repository;

import com.ebp08.gestion_financiera_backend.entity.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    // Para buscar todos los códigos no usados de un usuario
    List<RecoveryCode> findByUsuarioIdAndUsadoFalse(Long usuarioId);

    // Para verificar si el usuario está bloqueado
    Optional<RecoveryCode> findFirstByUsuarioIdOrderByBloqueadoHastaDesc(Long usuarioId);

    Optional<RecoveryCode> findByTokenTemporal(String tokenTemporal);
}
