package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.ExpenseDto;
import com.cristian.moneymanager.dto.IncomeDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExcelService {

    private static final String[] HEADERS = {"Nº", "Nombre", "Categoría", "Importe", "Fecha"};

    public void writeIncomesToExcel(OutputStream os, List<IncomeDto> incomes) throws IOException {
        writeRows(os, "Ingresos", incomes.stream()
                .map(i -> new RowData(i.getName(), i.getCategoryId() != null ? i.getCategoryName() : null, i.getAmount(), i.getDate()))
                .toList());
    }

    public void writeExpensesToExcel(OutputStream os, List<ExpenseDto> expenses) throws IOException {
        writeRows(os, "Gastos", expenses.stream()
                .map(e -> new RowData(e.getName(), e.getCategoryId() != null ? e.getCategoryName() : null, e.getAmount(), e.getDate()))
                .toList());
    }

    private record RowData(String name, String category, BigDecimal amount, LocalDate date) {
    }

    private void writeRows(OutputStream os, String sheetName, List<RowData> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            DataFormat format = workbook.createDataFormat();

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            // Real numbers/dates (not text) so the user can sum and sort them in Excel
            CellStyle euroStyle = workbook.createCellStyle();
            euroStyle.setDataFormat(format.getFormat("#,##0.00 \"€\""));
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < rows.size(); i++) {
                RowData data = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(data.name() != null ? data.name() : "N/A");
                row.createCell(2).setCellValue(data.category() != null ? data.category() : "N/A");

                Cell amount = row.createCell(3);
                amount.setCellValue(data.amount() != null ? data.amount().doubleValue() : 0);
                amount.setCellStyle(euroStyle);

                Cell date = row.createCell(4);
                if (data.date() != null) {
                    date.setCellValue(data.date());
                    date.setCellStyle(dateStyle);
                } else {
                    date.setCellValue("N/A");
                }
            }

            // Fixed widths (in 1/256 of a character): autoSizeColumn needs system fonts,
            // which minimal server containers may not have
            int[] widths = {6, 30, 20, 14, 12};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            workbook.write(os);
        }
    }
}
