package com.poli.entregable.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ExcelCsvReaderService {

    public List<Map<String, String>> readFile(MultipartFile file) throws IOException, CsvException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".csv")) {
            return readCsv(file);
        } else if (filename.endsWith(".xlsx")) {
            return readExcel(file, false);
        } else if (filename.endsWith(".xls")) {
            return readExcel(file, true);
        }
        throw new IllegalArgumentException("Formato no soportado. Use .csv, .xlsx o .xls");
    }

    private List<Map<String, String>> readCsv(MultipartFile file) throws IOException, CsvException {
        List<Map<String, String>> records = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return records;

            String[] headers = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                Map<String, String> record = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    record.put(headers[j].trim(), j < row.length ? row[j].trim() : "");
                }
                records.add(record);
            }
        }
        return records;
    }

    private List<Map<String, String>> readExcel(MultipartFile file, boolean isLegacy) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        Workbook workbook = isLegacy
                ? new HSSFWorkbook(file.getInputStream())
                : new XSSFWorkbook(file.getInputStream());

        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            workbook.close();
            return records;
        }

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(cellToString(cell).trim());
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, String> record = new LinkedHashMap<>();
            boolean hasData = false;
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String value = cellToString(cell).trim();
                record.put(headers.get(j), value);
                if (!value.isEmpty()) hasData = true;
            }
            if (hasData) records.add(record);
        }
        workbook.close();
        return records;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? String.valueOf(cell.getNumericCellValue())
                    : cell.getRichStringCellValue().getString();
            default -> "";
        };
    }
}
