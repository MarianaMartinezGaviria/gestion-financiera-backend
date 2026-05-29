package com.ebp08.gestion_financiera_backend.controller; // Indica que esta clase pertenece al paquete controller, donde van los endpoints HTTP.

import java.util.List; // Importa List porque vamos a devolver listas de transacciones.

import org.springframework.http.ResponseEntity; // Importa ResponseEntity para construir respuestas HTTP con estado y cuerpo.
import org.springframework.web.bind.annotation.DeleteMapping; // Importa la anotación para manejar peticiones HTTP DELETE.
import org.springframework.web.bind.annotation.GetMapping; // Importa la anotación para manejar peticiones HTTP GET.
import org.springframework.web.bind.annotation.PathVariable; // Importa la anotación para capturar variables desde la URL.
import org.springframework.web.bind.annotation.PostMapping; // Importa la anotación para manejar peticiones HTTP POST.
import org.springframework.web.bind.annotation.PutMapping; // Importa la anotación para manejar peticiones HTTP PUT.
import org.springframework.web.bind.annotation.RequestBody; // Importa la anotación para leer el JSON del cuerpo de la petición.
import org.springframework.web.bind.annotation.RequestMapping; // Importa la anotación para definir la ruta base del controlador.
import org.springframework.web.bind.annotation.RestController; // Importa la anotación que marca esta clase como controlador REST.

import com.ebp08.gestion_financiera_backend.dto.ActualizarTransaccionRequest;
import com.ebp08.gestion_financiera_backend.dto.CrearTransaccionRequest;
import com.ebp08.gestion_financiera_backend.dto.TransaccionResponse;
import com.ebp08.gestion_financiera_backend.entity.Transaccion; // Importa la entidad Transaccion porque este controlador recibe y devuelve transacciones.
import com.ebp08.gestion_financiera_backend.service.TransaccionService; // Importa el servicio de transacciones, que contiene la lógica de negocio.

import lombok.AllArgsConstructor; // Importa Lombok para generar automáticamente el constructor con todos los atributos.

@RestController // Le dice a Spring que esta clase maneja peticiones HTTP y devuelve respuestas en formato JSON.
@RequestMapping("/api/transacciones") // Define la ruta base para todos los endpoints de este controlador.
@AllArgsConstructor // Lombok genera un constructor con todos los atributos final de la clase.


public class TransaccionController {

    private final TransaccionService transaccionService; // Inyecta el servicio de transacciones para usar su lógica desde el controlador.

    @PostMapping
    public ResponseEntity<TransaccionResponse> crearTransaccion(@RequestBody CrearTransaccionRequest request) { // Recibe un JSON en el body y lo convierte en un objeto CrearTransaccionRequest.
        TransaccionResponse t = transaccionService.crearTransaccion(request);
        return ResponseEntity.status(201).body(t);
    }

    @GetMapping("/usuario")
    public ResponseEntity<List<Transaccion>> obtenerTransaccionesUsuario() {
        List<Transaccion> transacciones = transaccionService.obtenerTransaccionesUsuario();
        return ResponseEntity.ok(transacciones);
    }

    @GetMapping("/usuario/ingresos")
    public ResponseEntity<List<Transaccion>> obtenerIngresosRecientes() {
        List<Transaccion> ingresos = transaccionService.obtenerIngresosRecientes();
        return ResponseEntity.ok(ingresos);
    }

    @GetMapping("/usuario/gastos")
    public ResponseEntity<List<Transaccion>> obtenerGastosRecientes() {
        List<Transaccion> gastos = transaccionService.obtenerGastosRecientes();
        return ResponseEntity.ok(gastos);
    }

    @PutMapping("/{idTransaccion}/usuario")
    public ResponseEntity<Transaccion> actualizarTransaccion(
            @PathVariable Long idTransaccion,
            @RequestBody ActualizarTransaccionRequest request) {
        // Actualiza solo los campos enviados en el body.
        Transaccion actualizada = transaccionService.actualizarTransaccion(idTransaccion, request);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{idTransaccion}/usuario") // Toma de la URL el id de la transacción y el id del usuario.
    public ResponseEntity<Void> eliminarTransaccion(@PathVariable Long idTransaccion) {
        transaccionService.eliminarTransaccion(idTransaccion);
        return ResponseEntity.noContent().build();  // Devuelve respuesta HTTP 204 No Content, porque la eliminación fue exitosa y no se devuelve cuerpo.
    }
}

