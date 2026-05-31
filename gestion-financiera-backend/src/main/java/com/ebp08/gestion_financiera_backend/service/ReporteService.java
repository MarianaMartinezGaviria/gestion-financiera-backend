package com.ebp08.gestion_financiera_backend.service;

import com.ebp08.gestion_financiera_backend.dto.DatosGraficoDTO;
import com.ebp08.gestion_financiera_backend.dto.MovimientoResumenDTO;
import com.ebp08.gestion_financiera_backend.dto.ReporteComparativoMensualResponse;
import com.ebp08.gestion_financiera_backend.dto.ReporteGastosCategoriaResponse;
import com.ebp08.gestion_financiera_backend.dto.ReporteIngresosCategoriaResponse;
import com.ebp08.gestion_financiera_backend.dto.ReporteSummaryResponse;
import com.ebp08.gestion_financiera_backend.entity.Transaccion;
import com.ebp08.gestion_financiera_backend.enums.TipoTransaccion;
import com.ebp08.gestion_financiera_backend.repository.TransaccionRepository;
import com.ebp08.gestion_financiera_backend.security.SecurityHelper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReporteService {

    private final TransaccionRepository transaccionRepository;
    private final SecurityHelper securityHelper;
    private final GraficaHelper graficaHelper;

    // ── Helpers de fecha ─────────────────────────────────────────────────────

    private LocalDateTime inicioMes(int mes, int anio) {
        return LocalDateTime.of(anio, mes, 1, 0, 0, 0);
    }

    private LocalDateTime finMes(int mes, int anio) {
        LocalDateTime inicio = inicioMes(mes, anio);
        return inicio.withDayOfMonth(inicio.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59);
    }

    private List<Transaccion> obtenerTransaccionesPeriodo(Long idUsuario, int mes, int anio) {
        return transaccionRepository.findByUsuarioIdAndFechaBetween(
                idUsuario, inicioMes(mes, anio), finMes(mes, anio));
    }

    private void validarMesAnio(int mes, int anio) {
        if (mes < 1 || mes > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mes debe estar entre 1 y 12.");
        }
        if (anio < 2000 || anio > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El año no es válido.");
        }
    }

    private Image obtenerLogo() {
        try {
            java.io.InputStream stream = getClass()
                    .getResourceAsStream("/static/logo.png");
            if (stream == null) return null;
            byte[] bytes = stream.readAllBytes();
            Image logo = Image.getInstance(bytes);
            logo.scaleToFit(80, 80); // ajusta el tamaño según tu logo
            return logo;
        } catch (Exception e) {
            return null; // si falla no rompe el PDF
        }
    }

    // ── Reporte 1: gastos por categoría ──────────────────────────────────────

    public List<ReporteGastosCategoriaResponse> obtenerGastosPorCategoria(int mes, int anio) {

        validarMesAnio(mes, anio);
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();

        List<Transaccion> transacciones = obtenerTransaccionesPeriodo(idUsuario, mes, anio);

        // Filtra solo egresos y agrupa por categoría
        Map<Long, List<Transaccion>> porCategoria = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.EGRESO)
                .filter(t -> t.getCategoria() != null)
                .collect(Collectors.groupingBy(t -> t.getCategoria().getId()));

        return porCategoria.entrySet().stream()
                .map(entry -> {
                    List<Transaccion> grupo = entry.getValue();
                    String nombreCategoria = grupo.get(0).getCategoria().getNombre();
                    BigDecimal total = grupo.stream()
                            .map(Transaccion::getMonto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ReporteGastosCategoriaResponse(
                            entry.getKey(), nombreCategoria, total, grupo.size());
                })
                .sorted((a, b) -> b.getTotalGastado().compareTo(a.getTotalGastado()))
                .toList();
    }

    // ── Reporte 2: ingresos por categoría ────────────────────────────────────

    public List<ReporteIngresosCategoriaResponse> obtenerIngresosPorCategoria(int mes, int anio) {

        validarMesAnio(mes, anio);
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();

        List<Transaccion> transacciones = obtenerTransaccionesPeriodo(idUsuario, mes, anio);

        Map<Long, List<Transaccion>> porCategoria = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.INGRESO)
                .filter(t -> t.getCategoria() != null)
                .collect(Collectors.groupingBy(t -> t.getCategoria().getId()));

        return porCategoria.entrySet().stream()
                .map(entry -> {
                    List<Transaccion> grupo = entry.getValue();
                    String nombreCategoria = grupo.get(0).getCategoria().getNombre();
                    BigDecimal total = grupo.stream()
                            .map(Transaccion::getMonto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ReporteIngresosCategoriaResponse(
                            entry.getKey(), nombreCategoria, total, grupo.size());
                })
                .sorted((a, b) -> b.getTotalIngresado().compareTo(a.getTotalIngresado()))
                .toList();
    }

    // ── Reporte 3: resumen ingreso vs egreso ──────────────────────────────────

    public ReporteSummaryResponse obtenerResumen(int mes, int anio) {

        validarMesAnio(mes, anio);
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();

        List<Transaccion> transacciones = obtenerTransaccionesPeriodo(idUsuario, mes, anio);

        BigDecimal totalIngresos = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.INGRESO)
                .map(Transaccion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEgresos = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.EGRESO)
                .map(Transaccion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalIngresos.subtract(totalEgresos);

        BigDecimal porcentajeAhorro = BigDecimal.ZERO;
        if (totalIngresos.compareTo(BigDecimal.ZERO) > 0) {
            porcentajeAhorro = balance
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalIngresos, 2, RoundingMode.HALF_UP);
        }

        return new ReporteSummaryResponse(totalIngresos, totalEgresos, balance, porcentajeAhorro, mes, anio);
    }

    // ── Reporte 4: exportar ───────────────────────────────────────────────────

    public byte[] exportarCSV(int mes, int anio) {

        validarMesAnio(mes, anio);
        Long idUsuario = securityHelper.obtenerUsuarioAutenticado().getId();
        List<Transaccion> transacciones = obtenerTransaccionesPeriodo(idUsuario, mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

                // Encabezado (sin tildes para compatibilidad con Excel)
                writer.writeNext(new String[]{"ID", "Fecha", "Tipo", "Categoria", "Monto", "Descripcion"});

                // Filas
                for (Transaccion t : transacciones) {
                    writer.writeNext(new String[]{
                            String.valueOf(t.getId()),
                            t.getFecha() != null ? t.getFecha().toString() : "",
                            t.getTipo() != null ? t.getTipo().name() : "",
                            t.getCategoria() != null ? t.getCategoria().getNombre() : "Sin categoria",
                            t.getMonto() != null ? t.getMonto().toPlainString() : "0",
                            t.getDescripcion() != null ? t.getDescripcion() : ""
                    });
                }
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el CSV.");
        }

        return out.toByteArray();
    }

    public byte[] exportarPDF(int mes, int anio) {

        validarMesAnio(mes, anio);
        ReporteSummaryResponse resumen = obtenerResumen(mes, anio);
        List<ReporteGastosCategoriaResponse> gastos = obtenerGastosPorCategoria(mes, anio);
        List<ReporteIngresosCategoriaResponse> ingresos = obtenerIngresosPorCategoria(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(documento, out);
            documento.open();

            Font fTitulo    = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD,  BaseColor.WHITE);
            Font fSubtitulo = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,  new BaseColor(40, 40, 40));
            Font fNormal    = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(60, 60, 60));
            Font fRojo      = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,  BaseColor.RED);
            Font fVerde     = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,  new BaseColor(0, 140, 0));
            Font fTabla     = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, new BaseColor(50, 50, 50));
            Font fTablaHead = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,  BaseColor.WHITE);
            Font fTotal     = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD,  new BaseColor(40, 40, 40));

            // ── Encabezado verde oscuro para resumen general ──────────────────────
            Image logo = obtenerLogo();

            PdfPTable encabezado = new PdfPTable(logo != null ? 2 : 1);
            encabezado.setWidthPercentage(100);
            encabezado.setSpacingAfter(12f);
            if (logo != null) {
                encabezado.setWidths(new float[]{1.5f, 8.5f});
            }

            if (logo != null) {
                PdfPCell celdaLogo = new PdfPCell(logo);
                celdaLogo.setBackgroundColor(new BaseColor(30, 100, 80));
                celdaLogo.setBorder(0);
                celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaLogo.setPadding(10f);
                encabezado.addCell(celdaLogo);
            }

            PdfPCell celdaTitulo = new PdfPCell();
            celdaTitulo.setBackgroundColor(new BaseColor(30, 100, 80));
            celdaTitulo.setPadding(16f);
            celdaTitulo.setBorder(0);
            celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph pTitulo = new Paragraph("Reporte Financiero Mensual\n", fTitulo);
            pTitulo.add(new Chunk(
                    "Usuario: " + securityHelper.obtenerUsuarioAutenticado().getNombre()
                    + "   |   Periodo: " + mes + "/" + anio,
                    new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE)));
            celdaTitulo.addElement(pTitulo);
            encabezado.addCell(celdaTitulo);

            documento.add(encabezado);

            // ── Tarjetas de resumen ───────────────────────────────────────────────
            BigDecimal balance = resumen.getBalance();
            boolean esDeficit = balance.compareTo(BigDecimal.ZERO) < 0;

            PdfPTable tarjetas = new PdfPTable(3);
            tarjetas.setWidthPercentage(100);
            tarjetas.setWidths(new float[]{1f, 1f, 1f});
            tarjetas.setSpacingAfter(12f);

            tarjetas.addCell(tarjeta("Total Ingresos",
                    "$" + resumen.getTotalIngresos().toPlainString(),
                    new BaseColor(30, 120, 200), fTablaHead, fTitulo));

            tarjetas.addCell(tarjeta("Total Egresos",
                    "$" + resumen.getTotalEgresos().toPlainString(),
                    new BaseColor(200, 50, 50), fTablaHead, fTitulo));

            BaseColor colorBalance = esDeficit
                    ? new BaseColor(180, 40, 40)
                    : new BaseColor(30, 140, 80);
            String etiquetaBalance = esDeficit ? "Déficit" : "Superávit";
            tarjetas.addCell(tarjeta(etiquetaBalance,
                    "$" + balance.abs().toPlainString(),
                    colorBalance, fTablaHead, fTitulo));
            documento.add(tarjetas);

            // Estado del balance
            if (esDeficit) {
                Paragraph p = new Paragraph("⚠  Balance negativo — hay más gastos que ingresos.", fRojo);
                p.setSpacingAfter(8f);
                documento.add(p);
            } else if (balance.compareTo(BigDecimal.ZERO) > 0) {
                Paragraph p = new Paragraph(
                        "✓  Porcentaje de ahorro: "
                        + resumen.getPorcentajeAhorro().toPlainString() + "%", fVerde);
                p.setSpacingAfter(8f);
                documento.add(p);
            }

            // ── Gráfica comparativa ingresos vs egresos ───────────────────────────
            Paragraph tComp = new Paragraph("Comparativa ingresos vs egresos", fSubtitulo);
            tComp.setSpacingAfter(6f);
            documento.add(tComp);

            byte[] imgComparativa = graficaHelper.generarGraficaComparativa(
                    resumen.getTotalIngresos(), resumen.getTotalEgresos(), mes, anio);
            Image graficaComp = Image.getInstance(imgComparativa);
            graficaComp.setWidthPercentage(90);
            graficaComp.setAlignment(Element.ALIGN_CENTER);
            graficaComp.setSpacingAfter(14f);
            documento.add(graficaComp);

            // ── Gráfica gastos por categoría ──────────────────────────────────────
            if (!gastos.isEmpty()) {
                Paragraph tGastos = new Paragraph("Gastos por categoría", fSubtitulo);
                tGastos.setSpacingAfter(6f);
                documento.add(tGastos);

                java.util.LinkedHashMap<String, BigDecimal> datosGastos = new java.util.LinkedHashMap<>();
                for (ReporteGastosCategoriaResponse g : gastos) {
                    datosGastos.put(g.getNombreCategoria(), g.getTotalGastado());
                }

                byte[] imgGastos = graficaHelper.generarGraficaPorCategoria(
                        datosGastos,
                        "Gastos — " + mes + "/" + anio,
                        new java.awt.Color(180, 40, 40));
                Image graficaGastos = Image.getInstance(imgGastos);
                graficaGastos.setWidthPercentage(90);
                graficaGastos.setAlignment(Element.ALIGN_CENTER);
                graficaGastos.setSpacingAfter(14f);
                documento.add(graficaGastos);

                Paragraph tTablaG = new Paragraph("Detalle gastos por categoría", fSubtitulo);
                tTablaG.setSpacingAfter(6f);
                documento.add(tTablaG);

                PdfPTable tablaGastos = new PdfPTable(3);
                tablaGastos.setWidthPercentage(100);
                tablaGastos.setWidths(new float[]{5f, 3f, 3f});

                BaseColor rojoHeader = new BaseColor(180, 40, 40);
                for (String col : new String[]{"Categoría", "Total Gastado", "N° Transacciones"}) {
                    PdfPCell cab = new PdfPCell(new Paragraph(col, fTablaHead));
                    cab.setBackgroundColor(rojoHeader);
                    cab.setPadding(5f);
                    cab.setBorderColor(BaseColor.WHITE);
                    tablaGastos.addCell(cab);
                }
                boolean parG = false;
                BaseColor gris = new BaseColor(245, 245, 245);
                BigDecimal totalGastos = BigDecimal.ZERO;
                for (ReporteGastosCategoriaResponse g : gastos) {
                    BaseColor fondo = parG ? gris : BaseColor.WHITE;
                    tablaGastos.addCell(celda(g.getNombreCategoria(), fTabla, fondo));
                    tablaGastos.addCell(celda("$" + g.getTotalGastado().toPlainString(), fTabla, fondo));
                    tablaGastos.addCell(celda(String.valueOf(g.getCantidadTransacciones()), fTabla, fondo));
                    totalGastos = totalGastos.add(g.getTotalGastado());
                    parG = !parG;
                }
                BaseColor grisTotal = new BaseColor(210, 210, 210);
                for (PdfPCell c : new PdfPCell[]{
                        new PdfPCell(new Paragraph("TOTAL", fTotal)),
                        new PdfPCell(new Paragraph("$" + totalGastos.toPlainString(), fTotal)),
                        new PdfPCell(new Paragraph(""))}) {
                    c.setBackgroundColor(grisTotal);
                    c.setPadding(5f);
                    tablaGastos.addCell(c);
                }
                documento.add(tablaGastos);
            }

            // ── Gráfica ingresos por categoría ────────────────────────────────────
            if (!ingresos.isEmpty()) {
                documento.add(new Paragraph(" "));
                Paragraph tIngresos = new Paragraph("Ingresos por categoría", fSubtitulo);
                tIngresos.setSpacingAfter(6f);
                documento.add(tIngresos);

                java.util.LinkedHashMap<String, BigDecimal> datosIngresos = new java.util.LinkedHashMap<>();
                for (ReporteIngresosCategoriaResponse i : ingresos) {
                    datosIngresos.put(i.getNombreCategoria(), i.getTotalIngresado());
                }

                byte[] imgIngresos = graficaHelper.generarGraficaPorCategoria(
                        datosIngresos,
                        "Ingresos — " + mes + "/" + anio,
                        new java.awt.Color(30, 100, 180));
                Image graficaIngresos = Image.getInstance(imgIngresos);
                graficaIngresos.setWidthPercentage(90);
                graficaIngresos.setAlignment(Element.ALIGN_CENTER);
                graficaIngresos.setSpacingAfter(14f);
                documento.add(graficaIngresos);

                Paragraph tTablaI = new Paragraph("Detalle ingresos por categoría", fSubtitulo);
                tTablaI.setSpacingAfter(6f);
                documento.add(tTablaI);

                PdfPTable tablaIngresos = new PdfPTable(3);
                tablaIngresos.setWidthPercentage(100);
                tablaIngresos.setWidths(new float[]{5f, 3f, 3f});

                BaseColor azulHeader = new BaseColor(30, 100, 180);
                for (String col : new String[]{"Categoría", "Total Ingresado", "N° Transacciones"}) {
                    PdfPCell cab = new PdfPCell(new Paragraph(col, fTablaHead));
                    cab.setBackgroundColor(azulHeader);
                    cab.setPadding(5f);
                    cab.setBorderColor(BaseColor.WHITE);
                    tablaIngresos.addCell(cab);
                }
                boolean parI = false;
                BigDecimal totalIngresos = BigDecimal.ZERO;
                for (ReporteIngresosCategoriaResponse i : ingresos) {
                    BaseColor fondo = parI ? new BaseColor(245, 245, 245) : BaseColor.WHITE;
                    tablaIngresos.addCell(celda(i.getNombreCategoria(), fTabla, fondo));
                    tablaIngresos.addCell(celda("$" + i.getTotalIngresado().toPlainString(), fTabla, fondo));
                    tablaIngresos.addCell(celda(String.valueOf(i.getCantidadTransacciones()), fTabla, fondo));
                    totalIngresos = totalIngresos.add(i.getTotalIngresado());
                    parI = !parI;
                }
                BaseColor grisTotal = new BaseColor(210, 210, 210);
                for (PdfPCell c : new PdfPCell[]{
                        new PdfPCell(new Paragraph("TOTAL", fTotal)),
                        new PdfPCell(new Paragraph("$" + totalIngresos.toPlainString(), fTotal)),
                        new PdfPCell(new Paragraph(""))}) {
                    c.setBackgroundColor(grisTotal);
                    c.setPadding(5f);
                    tablaIngresos.addCell(c);
                }
                documento.add(tablaIngresos);
            }

            Paragraph pie = new Paragraph(
                    "\nReporte generado automáticamente — Gestión Financiera Personal EBP08",
                    new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, new BaseColor(150, 150, 150)));
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(16f);
            documento.add(pie);

            documento.close();

        } catch (DocumentException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar el PDF del resumen.");
        }

        return out.toByteArray();
    }
    // ── Comparativa mensual ───────────────────────────────────────────────────

    public ReporteComparativoMensualResponse obtenerComparativaMensual(int mes, int anio) {

        validarMesAnio(mes, anio);

        com.ebp08.gestion_financiera_backend.entity.Usuario usuario =
                securityHelper.obtenerUsuarioAutenticado();
        Long idUsuario = usuario.getId();

        List<Transaccion> transacciones = obtenerTransaccionesPeriodo(idUsuario, mes, anio);

        BigDecimal totalIngresos = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.INGRESO)
                .map(Transaccion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGastos = transacciones.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.EGRESO)
                .map(Transaccion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalIngresos.subtract(totalGastos);

        String estadoBalance;
        BigDecimal montoDeficit;
        BigDecimal porcentajeAhorro;

        int comparacion = balance.compareTo(BigDecimal.ZERO);

        if (comparacion > 0) {
            estadoBalance = "Superávit";
            montoDeficit = BigDecimal.ZERO;
            porcentajeAhorro = balance
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalIngresos, 2, RoundingMode.HALF_UP);
        } else if (comparacion < 0) {
            estadoBalance = "Déficit";
            montoDeficit = balance.abs();
            porcentajeAhorro = BigDecimal.ZERO;
        } else {
            estadoBalance = "Equilibrio";
            montoDeficit = BigDecimal.ZERO;
            porcentajeAhorro = BigDecimal.ZERO;
        }

        DatosGraficoDTO datosGrafico = new DatosGraficoDTO(totalIngresos, totalGastos);

        List<MovimientoResumenDTO> movimientos = transacciones.stream()
                .map(t -> new MovimientoResumenDTO(
                        t.getFecha(),
                        t.getTipo(),
                        t.getCategoria() != null ? t.getCategoria().getNombre() : "Sin categoría",
                        t.getMonto(),
                        t.getDescripcion() != null ? t.getDescripcion() : ""))
                .toList();

        return new ReporteComparativoMensualResponse(
                usuario.getNombre(),
                mes,
                anio,
                totalIngresos,
                totalGastos,
                balance,
                estadoBalance,
                montoDeficit,
                porcentajeAhorro,
                datosGrafico,
                movimientos);
    }

    public byte[] exportarComparativaCSV(int mes, int anio) {

        validarMesAnio(mes, anio);
        ReporteComparativoMensualResponse comparativa = obtenerComparativaMensual(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

                // Encabezado del resumen (sin tildes)
                writer.writeNext(new String[]{"Usuario", "Mes", "Anio", "Total Ingresos",
                    "Total Gastos", "Balance", "Estado", "Deficit", "% Ahorro"});
                writer.writeNext(new String[]{
                        comparativa.getNombreUsuario(),
                        String.valueOf(comparativa.getMes()),
                        String.valueOf(comparativa.getAnio()),
                        comparativa.getTotalIngresos().toPlainString(),
                        comparativa.getTotalGastos().toPlainString(),
                        comparativa.getBalance().toPlainString(),
                        comparativa.getEstadoBalance(),
                        comparativa.getMontoDeficit().toPlainString(),
                        comparativa.getPorcentajeAhorro().toPlainString() + "%"
                });

                // Línea en blanco separadora
                writer.writeNext(new String[]{});

                // Detalle de movimientos
                writer.writeNext(new String[]{"Fecha", "Tipo", "Categoria", "Monto", "Descripcion"});
                for (MovimientoResumenDTO m : comparativa.getMovimientosResumen()) {
                    writer.writeNext(new String[]{
                            m.getFecha() != null ? m.getFecha().toString() : "",
                            m.getTipo() != null ? m.getTipo().name() : "",
                            m.getCategoria(),
                            m.getMonto().toPlainString(),
                            m.getDescripcion()
                    });
                }
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el CSV comparativo.");
        }

        return out.toByteArray();
    }

    public byte[] exportarComparativaPDF(int mes, int anio) {

        validarMesAnio(mes, anio);
        ReporteComparativoMensualResponse comparativa = obtenerComparativaMensual(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(documento, out);
            documento.open();

            // ── Fuentes ──────────────────────────────────────────────────────────
            Font fTitulo    = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD,  BaseColor.WHITE);
            Font fSubtitulo = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,  new BaseColor(40, 40, 40));
            Font fNormal    = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(60, 60, 60));
            Font fRojo      = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,  BaseColor.RED);
            Font fVerde     = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,  new BaseColor(0, 140, 0));
            Font fTabla     = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, new BaseColor(50, 50, 50));
            Font fTablaHead = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,  BaseColor.WHITE);
            Font fTotal     = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD,  new BaseColor(40, 40, 40));

            // ── Encabezado con fondo azul ─────────────────────────────────────────
            Image logo = obtenerLogo();

            PdfPTable encabezado = new PdfPTable(logo != null ? 2 : 1);
            encabezado.setWidthPercentage(100);
            encabezado.setSpacingAfter(12f);
            if (logo != null) {
                encabezado.setWidths(new float[]{1.5f, 8.5f});
            }

            if (logo != null) {
                PdfPCell celdaLogo = new PdfPCell(logo);
                celdaLogo.setBackgroundColor(new BaseColor(30, 80, 160));
                celdaLogo.setBorder(0);
                celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaLogo.setPadding(10f);
                encabezado.addCell(celdaLogo);
            }

            PdfPCell celdaTitulo = new PdfPCell();
            celdaTitulo.setBackgroundColor(new BaseColor(30, 80, 160));
            celdaTitulo.setPadding(16f);
            celdaTitulo.setBorder(0);
            celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph pTitulo = new Paragraph(
                    "Comparativa Mensual de Ingresos vs Gastos\n", fTitulo);
            pTitulo.add(new Chunk("Usuario: " + comparativa.getNombreUsuario()
                    + "   |   Periodo: " + comparativa.getMes() + "/" + comparativa.getAnio(),
                    new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE)));
            celdaTitulo.addElement(pTitulo);
            encabezado.addCell(celdaTitulo);
            documento.add(encabezado);

            // ── Tarjetas de resumen en dos columnas ───────────────────────────────
            PdfPTable tarjetas = new PdfPTable(3);
            tarjetas.setWidthPercentage(100);
            tarjetas.setWidths(new float[]{1f, 1f, 1f});
            tarjetas.setSpacingAfter(12f);

            tarjetas.addCell(tarjeta("Total Ingresos",
                    "$" + comparativa.getTotalIngresos().toPlainString(),
                    new BaseColor(30, 120, 200), fTablaHead, fTitulo));

            tarjetas.addCell(tarjeta("Total Gastos",
                    "$" + comparativa.getTotalGastos().toPlainString(),
                    new BaseColor(200, 50, 50), fTablaHead, fTitulo));

            BaseColor colorBalance = "Déficit".equals(comparativa.getEstadoBalance())
                    ? new BaseColor(180, 40, 40)
                    : new BaseColor(30, 140, 80);
            tarjetas.addCell(tarjeta(comparativa.getEstadoBalance(),
                    "$" + comparativa.getBalance().abs().toPlainString(),
                    colorBalance, fTablaHead, fTitulo));

            documento.add(tarjetas);

            // ── Info adicional según estado ───────────────────────────────────────
            if ("Déficit".equals(comparativa.getEstadoBalance())) {
                Paragraph deficit = new Paragraph(
                        "⚠  Monto en déficit: $" + comparativa.getMontoDeficit().toPlainString(), fRojo);
                deficit.setSpacingAfter(8f);
                documento.add(deficit);
            } else if ("Superávit".equals(comparativa.getEstadoBalance())) {
                Paragraph ahorro = new Paragraph(
                        "✓  Porcentaje de ahorro: "
                                + comparativa.getPorcentajeAhorro().toPlainString() + "%", fVerde);
                ahorro.setSpacingAfter(8f);
                documento.add(ahorro);
            }

            // ── Gráfica comparativa ───────────────────────────────────────────────
            Paragraph tituloGrafica = new Paragraph("Gráfico comparativo", fSubtitulo);
            tituloGrafica.setSpacingAfter(6f);
            documento.add(tituloGrafica);

            byte[] imgBytes = graficaHelper.generarGraficaComparativa(
                    comparativa.getTotalIngresos(),
                    comparativa.getTotalGastos(),
                    mes, anio);

            Image grafica = Image.getInstance(imgBytes);
            grafica.setWidthPercentage(90);
            grafica.setAlignment(Element.ALIGN_CENTER);
            grafica.setSpacingAfter(14f);
            documento.add(grafica);

            // ── Tabla de movimientos ──────────────────────────────────────────────
            Paragraph tituloTabla = new Paragraph("Detalle de movimientos", fSubtitulo);
            tituloTabla.setSpacingAfter(6f);
            documento.add(tituloTabla);

            if (comparativa.getMovimientosResumen().isEmpty()) {
                documento.add(new Paragraph("No hay movimientos para este periodo.", fNormal));
            } else {
                PdfPTable tabla = new PdfPTable(5);
                tabla.setWidthPercentage(100);
                tabla.setWidths(new float[]{2.8f, 1.8f, 2.2f, 2f, 2.8f});

                BaseColor azulHeader = new BaseColor(30, 80, 160);
                for (String col : new String[]{"Fecha", "Tipo", "Categoría", "Monto", "Descripción"}) {
                    PdfPCell cab = new PdfPCell(new Paragraph(col, fTablaHead));
                    cab.setBackgroundColor(azulHeader);
                    cab.setPadding(5f);
                    cab.setBorderColor(BaseColor.WHITE);
                    tabla.addCell(cab);
                }

                boolean filaPar = false;
                BaseColor grisClaro = new BaseColor(245, 245, 245);
                for (MovimientoResumenDTO m : comparativa.getMovimientosResumen()) {
                    BaseColor fondo = filaPar ? grisClaro : BaseColor.WHITE;

                    BaseColor fondoTipo = TipoTransaccion.INGRESO.equals(m.getTipo())
                            ? new BaseColor(220, 235, 255)
                            : new BaseColor(255, 228, 228);

                    tabla.addCell(celda(m.getFecha() != null
                            ? m.getFecha().toLocalDate().toString() : "", fTabla, fondo));
                    tabla.addCell(celda(m.getTipo() != null
                            ? m.getTipo().name() : "", fTabla, fondoTipo));
                    tabla.addCell(celda(m.getCategoria(), fTabla, fondo));
                    tabla.addCell(celda("$" + m.getMonto().toPlainString(), fTabla, fondo));
                    tabla.addCell(celda(m.getDescripcion(), fTabla, fondo));
                    filaPar = !filaPar;
                }
                documento.add(tabla);
            }

            // ── Pie de página ─────────────────────────────────────────────────────
            Paragraph pie = new Paragraph(
                    "\nReporte generado automáticamente — Gestión Financiera Personal EBP08",
                    new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, new BaseColor(150, 150, 150)));
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(16f);
            documento.add(pie);

            documento.close();

        } catch (DocumentException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar el PDF comparativo.");
        }

        return out.toByteArray();
    }

    // ── Export gastos por categoría ───────────────────────────────────────────

    public byte[] exportarGastosCSV(int mes, int anio) {

        validarMesAnio(mes, anio);
        List<ReporteGastosCategoriaResponse> gastos = obtenerGastosPorCategoria(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

                writer.writeNext(new String[]{"Categoria", "Total Gastado", "Cantidad Transacciones"});

                for (ReporteGastosCategoriaResponse g : gastos) {
                    writer.writeNext(new String[]{
                            g.getNombreCategoria(),
                            g.getTotalGastado().toPlainString(),
                            String.valueOf(g.getCantidadTransacciones())
                    });
                }

                BigDecimal totalGeneral = gastos.stream()
                        .map(ReporteGastosCategoriaResponse::getTotalGastado)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                writer.writeNext(new String[]{});
                writer.writeNext(new String[]{"TOTAL", totalGeneral.toPlainString(), ""});
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el CSV de gastos.");
        }

        return out.toByteArray();
    }

    public byte[] exportarGastosPDF(int mes, int anio) {

        validarMesAnio(mes, anio);
        List<ReporteGastosCategoriaResponse> gastos = obtenerGastosPorCategoria(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(documento, out);
            documento.open();

            Font fTitulo    = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD,  BaseColor.WHITE);
            Font fSubtitulo = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,  new BaseColor(40, 40, 40));
            Font fNormal    = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(60, 60, 60));
            Font fTabla     = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, new BaseColor(50, 50, 50));
            Font fTablaHead = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,  BaseColor.WHITE);
            Font fTotal     = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD,  new BaseColor(40, 40, 40));

            Image logo = obtenerLogo();

            PdfPTable encabezado = new PdfPTable(logo != null ? 2 : 1);
            encabezado.setWidthPercentage(100);
            encabezado.setSpacingAfter(12f);
            if (logo != null) {
                encabezado.setWidths(new float[]{1.5f, 8.5f});
            }

            if (logo != null) {
                PdfPCell celdaLogo = new PdfPCell(logo);
                celdaLogo.setBackgroundColor(new BaseColor(180, 40, 40));
                celdaLogo.setBorder(0);
                celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaLogo.setPadding(10f);
                encabezado.addCell(celdaLogo);
            }

            PdfPCell celdaTitulo = new PdfPCell();
            celdaTitulo.setBackgroundColor(new BaseColor(180, 40, 40));
            celdaTitulo.setPadding(16f);
            celdaTitulo.setBorder(0);
            celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph pTitulo = new Paragraph("Reporte de Gastos por Categoría\n", fTitulo);
            pTitulo.add(new Chunk("Usuario: "
                    + securityHelper.obtenerUsuarioAutenticado().getNombre()
                    + "   |   Periodo: " + mes + "/" + anio,
                    new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE)));
            celdaTitulo.addElement(pTitulo);
            encabezado.addCell(celdaTitulo);
            documento.add(encabezado);

            if (gastos.isEmpty()) {
                documento.add(new Paragraph("No hay gastos registrados para este periodo.", fNormal));
                documento.close();
                return out.toByteArray();
            }

            BigDecimal totalGeneral = gastos.stream()
                    .map(ReporteGastosCategoriaResponse::getTotalGastado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PdfPTable tarjeta = new PdfPTable(1);
            tarjeta.setWidthPercentage(40);
            tarjeta.setHorizontalAlignment(Element.ALIGN_LEFT);
            tarjeta.setSpacingAfter(12f);
            tarjeta.addCell(tarjeta("Total Gastos", "$" + totalGeneral.toPlainString(),
                    new BaseColor(180, 40, 40), fTablaHead, fTitulo));
            documento.add(tarjeta);

            Paragraph tGrafica = new Paragraph("Distribución por categoría", fSubtitulo);
            tGrafica.setSpacingAfter(6f);
            documento.add(tGrafica);

            java.util.LinkedHashMap<String, BigDecimal> datosGrafica = new java.util.LinkedHashMap<>();
            for (ReporteGastosCategoriaResponse g : gastos) {
                datosGrafica.put(g.getNombreCategoria(), g.getTotalGastado());
            }

            byte[] imgBytes = graficaHelper.generarGraficaPorCategoria(
                    datosGrafica,
                    "Gastos — " + mes + "/" + anio,
                    new java.awt.Color(180, 40, 40));

            Image grafica = Image.getInstance(imgBytes);
            grafica.setWidthPercentage(90);
            grafica.setAlignment(Element.ALIGN_CENTER);
            grafica.setSpacingAfter(14f);
            documento.add(grafica);

            Paragraph tTabla = new Paragraph("Detalle por categoría", fSubtitulo);
            tTabla.setSpacingAfter(6f);
            documento.add(tTabla);

            PdfPTable tabla = new PdfPTable(3);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{5f, 3f, 3f});

            BaseColor rojoHeader = new BaseColor(180, 40, 40);
            for (String col : new String[]{"Categoría", "Total Gastado", "N° Transacciones"}) {
                PdfPCell cab = new PdfPCell(new Paragraph(col, fTablaHead));
                cab.setBackgroundColor(rojoHeader);
                cab.setPadding(5f);
                cab.setBorderColor(BaseColor.WHITE);
                tabla.addCell(cab);
            }

            boolean filaPar = false;
            BaseColor grisClaro = new BaseColor(245, 245, 245);
            for (ReporteGastosCategoriaResponse g : gastos) {
                BaseColor fondo = filaPar ? grisClaro : BaseColor.WHITE;
                tabla.addCell(celda(g.getNombreCategoria(), fTabla, fondo));
                tabla.addCell(celda("$" + g.getTotalGastado().toPlainString(), fTabla, fondo));
                tabla.addCell(celda(String.valueOf(g.getCantidadTransacciones()), fTabla, fondo));
                filaPar = !filaPar;
            }

            BaseColor grisTotal = new BaseColor(210, 210, 210);
            PdfPCell t1 = new PdfPCell(new Paragraph("TOTAL", fTotal));
            PdfPCell t2 = new PdfPCell(new Paragraph("$" + totalGeneral.toPlainString(), fTotal));
            PdfPCell t3 = new PdfPCell(new Paragraph(""));
            for (PdfPCell c : new PdfPCell[]{t1, t2, t3}) {
                c.setBackgroundColor(grisTotal);
                c.setPadding(5f);
                tabla.addCell(c);
            }
            documento.add(tabla);

            Paragraph pie = new Paragraph(
                    "\nReporte generado automáticamente — Gestión Financiera Personal EBP08",
                    new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, new BaseColor(150, 150, 150)));
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(16f);
            documento.add(pie);

            documento.close();

        } catch (DocumentException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar el PDF de gastos.");
        }

        return out.toByteArray();
    }

    // ── Export ingresos por categoría ─────────────────────────────────────────

    public byte[] exportarIngresosCSV(int mes, int anio) {

        validarMesAnio(mes, anio);
        List<ReporteIngresosCategoriaResponse> ingresos = obtenerIngresosPorCategoria(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

                writer.writeNext(new String[]{"Categoria", "Total Ingresado", "Cantidad Transacciones"});

                for (ReporteIngresosCategoriaResponse i : ingresos) {
                    writer.writeNext(new String[]{
                            i.getNombreCategoria(),
                            i.getTotalIngresado().toPlainString(),
                            String.valueOf(i.getCantidadTransacciones())
                    });
                }

                BigDecimal totalGeneral = ingresos.stream()
                        .map(ReporteIngresosCategoriaResponse::getTotalIngresado)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                writer.writeNext(new String[]{});
                writer.writeNext(new String[]{"TOTAL", totalGeneral.toPlainString(), ""});
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el CSV de ingresos.");
        }

        return out.toByteArray();
    }

    public byte[] exportarIngresosPDF(int mes, int anio) {

        validarMesAnio(mes, anio);
        List<ReporteIngresosCategoriaResponse> ingresos = obtenerIngresosPorCategoria(mes, anio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(documento, out);
            documento.open();

            Font fTitulo    = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD,  BaseColor.WHITE);
            Font fSubtitulo = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,  new BaseColor(40, 40, 40));
            Font fNormal    = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(60, 60, 60));
            Font fTabla     = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, new BaseColor(50, 50, 50));
            Font fTablaHead = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,  BaseColor.WHITE);
            Font fTotal     = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD,  new BaseColor(40, 40, 40));

            Image logo = obtenerLogo();

            PdfPTable encabezado = new PdfPTable(logo != null ? 2 : 1);
            encabezado.setWidthPercentage(100);
            encabezado.setSpacingAfter(12f);
            if (logo != null) {
                encabezado.setWidths(new float[]{1.5f, 8.5f});
            }

            if (logo != null) {
                PdfPCell celdaLogo = new PdfPCell(logo);
                celdaLogo.setBackgroundColor(new BaseColor(30, 100, 180));
                celdaLogo.setBorder(0);
                celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaLogo.setPadding(10f);
                encabezado.addCell(celdaLogo);
            }

            PdfPCell celdaTitulo = new PdfPCell();
            celdaTitulo.setBackgroundColor(new BaseColor(30, 100, 180));
            celdaTitulo.setPadding(16f);
            celdaTitulo.setBorder(0);
            celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph pTitulo = new Paragraph("Reporte de Ingresos por Categoría\n", fTitulo);
            pTitulo.add(new Chunk("Usuario: "
                    + securityHelper.obtenerUsuarioAutenticado().getNombre()
                    + "   |   Periodo: " + mes + "/" + anio,
                    new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE)));
            celdaTitulo.addElement(pTitulo);
            encabezado.addCell(celdaTitulo);
            documento.add(encabezado);

            if (ingresos.isEmpty()) {
                documento.add(new Paragraph("No hay ingresos registrados para este periodo.", fNormal));
                documento.close();
                return out.toByteArray();
            }

            BigDecimal totalGeneral = ingresos.stream()
                    .map(ReporteIngresosCategoriaResponse::getTotalIngresado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PdfPTable tarjeta = new PdfPTable(1);
            tarjeta.setWidthPercentage(40);
            tarjeta.setHorizontalAlignment(Element.ALIGN_LEFT);
            tarjeta.setSpacingAfter(12f);
            tarjeta.addCell(tarjeta("Total Ingresos", "$" + totalGeneral.toPlainString(),
                    new BaseColor(30, 100, 180), fTablaHead, fTitulo));
            documento.add(tarjeta);

            Paragraph tGrafica = new Paragraph("Distribución por categoría", fSubtitulo);
            tGrafica.setSpacingAfter(6f);
            documento.add(tGrafica);

            java.util.LinkedHashMap<String, BigDecimal> datosGrafica = new java.util.LinkedHashMap<>();
            for (ReporteIngresosCategoriaResponse i : ingresos) {
                datosGrafica.put(i.getNombreCategoria(), i.getTotalIngresado());
            }

            byte[] imgBytes = graficaHelper.generarGraficaPorCategoria(
                    datosGrafica,
                    "Ingresos — " + mes + "/" + anio,
                    new java.awt.Color(30, 100, 180));

            Image grafica = Image.getInstance(imgBytes);
            grafica.setWidthPercentage(90);
            grafica.setAlignment(Element.ALIGN_CENTER);
            grafica.setSpacingAfter(14f);
            documento.add(grafica);

            Paragraph tTabla = new Paragraph("Detalle por categoría", fSubtitulo);
            tTabla.setSpacingAfter(6f);
            documento.add(tTabla);

            PdfPTable tabla = new PdfPTable(3);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{5f, 3f, 3f});
            tabla.setSpacingBefore(6f);

            BaseColor azulHeader = new BaseColor(30, 100, 180);
            for (String col : new String[]{"Categoría", "Total Ingresado", "N° Transacciones"}) {
                PdfPCell cab = new PdfPCell(new Paragraph(col, fTablaHead));
                cab.setBackgroundColor(azulHeader);
                cab.setPadding(5f);
                cab.setBorderColor(BaseColor.WHITE);
                tabla.addCell(cab);
            }

            boolean filaPar = false;
            BaseColor grisClaro = new BaseColor(245, 245, 245);
            for (ReporteIngresosCategoriaResponse i : ingresos) {
                BaseColor fondo = filaPar ? grisClaro : BaseColor.WHITE;
                tabla.addCell(celda(i.getNombreCategoria(), fTabla, fondo));
                tabla.addCell(celda("$" + i.getTotalIngresado().toPlainString(), fTabla, fondo));
                tabla.addCell(celda(String.valueOf(i.getCantidadTransacciones()), fTabla, fondo));
                filaPar = !filaPar;
            }

            BaseColor grisTotal = new BaseColor(210, 210, 210);
            PdfPCell t1 = new PdfPCell(new Paragraph("TOTAL", fTotal));
            PdfPCell t2 = new PdfPCell(new Paragraph("$" + totalGeneral.toPlainString(), fTotal));
            PdfPCell t3 = new PdfPCell(new Paragraph(""));
            for (PdfPCell c : new PdfPCell[]{t1, t2, t3}) {
                c.setBackgroundColor(grisTotal);
                c.setPadding(5f);
                tabla.addCell(c);
            }

            documento.add(tabla);

            Paragraph pie = new Paragraph(
                    "\nReporte generado automáticamente — Gestión Financiera Personal EBP08",
                    new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, new BaseColor(150, 150, 150)));
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(16f);
            documento.add(pie);

            documento.close();

        } catch (DocumentException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar el PDF de ingresos.");
        }

        return out.toByteArray();
    }

    private PdfPCell tarjeta(String etiqueta, String valor,
                              BaseColor color, Font fEtiqueta, Font fValor) {
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(color);
        celda.setPadding(10f);
        celda.setBorder(0);

        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + "\n",
                new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.WHITE)));
        p.add(new Chunk(valor,
                new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.WHITE)));
        celda.addElement(p);
        return celda;
    }

    private PdfPCell celda(String texto, Font fuente, BaseColor fondo) {
        PdfPCell c = new PdfPCell(new Paragraph(texto, fuente));
        c.setBackgroundColor(fondo);
        c.setPadding(4f);
        return c;
    }
}
