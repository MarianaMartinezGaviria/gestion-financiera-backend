package com.ebp08.gestion_financiera_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.ebp08.gestion_financiera_backend.dto.GroqRequest;
import com.ebp08.gestion_financiera_backend.dto.GroqResponse;
import com.ebp08.gestion_financiera_backend.entity.Alerta;
import com.ebp08.gestion_financiera_backend.entity.Presupuesto;
import com.ebp08.gestion_financiera_backend.entity.Transaccion;

@Service
public class RecomendacionService {

    private static final String IA_NO_DISPONIBLE =
            "El servicio de recomendaciones no está disponible temporalmente. Intenta nuevamente más tarde.";

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final WebClient webClient;
    private final TransaccionService transaccionService;
    private final AlertaService alertaService;
    private final PresupuestoService presupuestoService;

    public RecomendacionService(
            WebClient webClient,
            TransaccionService transaccionService,
            AlertaService alertaService,
            PresupuestoService presupuestoService) {

        this.webClient = webClient;
        this.transaccionService = transaccionService;
        this.alertaService = alertaService;
        this.presupuestoService = presupuestoService;
    }

    public String obtenerRecomendacionesPorBalance() {

        int mesActual = LocalDate.now().getMonthValue();
        int anioActual = LocalDate.now().getYear();

        List<Transaccion> transaccionesMes =
                transaccionService.obtenerTransaccionesPorMes(
                        mesActual,
                        anioActual
                );

        BigDecimal totalIngresos =
                transaccionService.calcularTotalIngresos(
                        transaccionesMes
                );

        BigDecimal totalGastos =
                transaccionService.calcularTotalGastos(
                        transaccionesMes
                );

        BigDecimal balance =
                totalIngresos.subtract(totalGastos);

        StringBuilder detalleTransacciones =
                new StringBuilder();

        transaccionesMes.forEach(t ->
                detalleTransacciones.append(
                        String.format(
                                "- %s | %s | $%s | %s%n",
                                t.getCategoria().getNombre(),
                                t.getTipo(),
                                t.getMonto(),
                                t.getDescripcion() != null
                                        ? t.getDescripcion()
                                        : "Sin descripción"
                        )
                )
        );

        String prompt = String.format("""
                Eres un asesor financiero inteligente.

                Basándote en la información proporcionada:

                - Genera exactamente 3 recomendaciones.
                - Deben estar numeradas.
                - Deben ser breves.
                - Deben ser accionables.
                - Responde únicamente en español.

                Balance de %s %d:

                - Ingresos Totales: $%s
                - Gastos Totales: $%s
                - Balance Neto: $%s

                Transacciones:

                %s
                """,
                LocalDate.now()
                        .getMonth()
                        .getDisplayName(
                                TextStyle.FULL,
                                Locale.forLanguageTag("es")
                        ),
                anioActual,
                totalIngresos,
                totalGastos,
                balance,
                detalleTransacciones
        );

        return llamarAPI(prompt);
    }

    public String obtenerRecomendacionesPorAlertas() {

        List<Alerta> alertas =
                alertaService.obtenerAlertasUsuario();

        if (alertas.isEmpty()) {
            return "No se han detectado alertas de presupuesto este mes. ¡Tu planificación va excelente!";
        }

        List<Presupuesto> presupuestos =
                presupuestoService.obtenerResumenPresupuestoCategorias();

        StringBuilder detalleAlertas =
                new StringBuilder();

        alertas.forEach(a ->
                detalleAlertas.append(
                        String.format(
                                "- %s: %s%n",
                                a.getTipo(),
                                a.getMensaje()
                        )
                )
        );

        StringBuilder detallePresupuestos =
                new StringBuilder();

        presupuestos.forEach(p ->
                detallePresupuestos.append(
                        String.format(
                                "- %s: límite $%s, gastado $%s (%s%%)%n",
                                p.getCategoria().getNombre(),
                                p.getMontoLimite(),
                                presupuestoService.calcularGastoPresupuesto(p),
                                presupuestoService.calcularPorcentajeUsoPresupuesto(p)
                        )
                )
        );

        String prompt = String.format("""
                Eres un asesor financiero experto en control de presupuestos.

                Genera exactamente 3 recomendaciones numeradas y breves para ayudar al usuario a controlar sus gastos.

                Alertas activas:

                %s

                Presupuestos:

                %s
                """,
                detalleAlertas,
                detallePresupuestos
        );

        return llamarAPI(prompt);
    }

    private String llamarAPI(String prompt) {

        GroqRequest request = new GroqRequest(
                model,
                List.of(
                        new GroqRequest.Message(
                                "system",
                                """
                                Eres un asesor financiero especializado en finanzas personales.
                                Responde siempre en español.
                                Genera recomendaciones concretas, breves y accionables.
                                """
                        ),
                        new GroqRequest.Message(
                                "user",
                                prompt
                        )
                )
        );

        try {

            GroqResponse response =
                    webClient.post()
                            .uri(apiUrl)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + apiKey
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(GroqResponse.class)
                            .block();

            if (response == null
                    || response.getChoices() == null
                    || response.getChoices().isEmpty()) {

                return "La IA no devolvió una respuesta válida.";
            }

            return response.getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

        } catch (Exception ex) {

            System.err.println(
                    "Error Groq: "
                            + ex.getMessage()
            );

            return IA_NO_DISPONIBLE;
        }
    }
}