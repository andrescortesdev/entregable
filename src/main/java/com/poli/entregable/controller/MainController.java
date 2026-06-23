package com.poli.entregable.controller;

import com.poli.entregable.service.ExcelCsvReaderService;
import com.poli.entregable.service.FirebaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Controller
public class MainController {

    private final FirebaseService firebaseService;
    private final ExcelCsvReaderService readerService;

    public MainController(FirebaseService firebaseService, ExcelCsvReaderService readerService) {
        this.firebaseService = firebaseService;
        this.readerService = readerService;
    }

    // ─── HOME ────────────────────────────────────────────────────────────────

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("collections", firebaseService.listCollections());
        } catch (Exception e) {
            model.addAttribute("error", "Error conectando a Firebase: " + e.getMessage());
            model.addAttribute("collections", List.of());
        }
        return "index";
    }

    // ─── UPLOAD ──────────────────────────────────────────────────────────────

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "collectionName", required = false) String collectionName,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Por favor selecciona un archivo.");
            return "redirect:/";
        }
        if (collectionName == null || collectionName.isBlank()) {
            ra.addFlashAttribute("error", "El nombre de la colección es obligatorio.");
            return "redirect:/";
        }
        try {
            String base = sanitizeName(collectionName.trim());
            String name = base + buildSuffix(request);

            List<Map<String, String>> records = readerService.readFile(file);
            if (records.isEmpty()) {
                ra.addFlashAttribute("error", "El archivo no contiene datos.");
                return "redirect:/";
            }
            int count = firebaseService.uploadRecords(name, records);
            ra.addFlashAttribute("success",
                    "¡Carga exitosa! " + count + " registros subidos a la colección «" + name + "».");
            return "redirect:/collection/" + name;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al cargar el archivo: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ─── COLECCIÓN ───────────────────────────────────────────────────────────

    @GetMapping("/collection/{name}")
    public String viewCollection(@PathVariable String name,
                                 @RequestParam(required = false) String searchField,
                                 @RequestParam(required = false) String searchValue,
                                 Model model) {
        try {
            List<String> fields = firebaseService.getCollectionFields(name);
            List<Map<String, Object>> docs;

            if (searchField != null && !searchField.isBlank() && searchValue != null && !searchValue.isBlank()) {
                docs = firebaseService.searchDocuments(name, searchField, searchValue);
                model.addAttribute("searchField", searchField);
                model.addAttribute("searchValue", searchValue);
            } else {
                docs = firebaseService.getDocuments(name);
            }

            model.addAttribute("collectionName", name);
            model.addAttribute("fields", fields);
            model.addAttribute("documents", docs);
            model.addAttribute("collections", firebaseService.listCollections());
        } catch (Exception e) {
            model.addAttribute("error", "Error: " + e.getMessage());
        }
        return "collection";
    }

    // ─── CREAR ───────────────────────────────────────────────────────────────

    @GetMapping("/collection/{name}/new")
    public String newDocumentForm(@PathVariable String name, Model model) {
        try {
            List<String> fields = firebaseService.getCollectionFields(name);
            model.addAttribute("collectionName", name);
            model.addAttribute("fields", fields);
            model.addAttribute("document", new HashMap<>());
            model.addAttribute("isNew", true);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "form";
    }

    @PostMapping("/collection/{name}/create")
    public String createDocument(@PathVariable String name,
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        try {
            Map<String, String> fields = extractFields(request);
            firebaseService.createDocument(name, fields);
            ra.addFlashAttribute("success", "Registro creado exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al crear: " + e.getMessage());
        }
        return "redirect:/collection/" + name;
    }

    // ─── EDITAR ──────────────────────────────────────────────────────────────

    @GetMapping("/collection/{name}/edit/{docId}")
    public String editDocumentForm(@PathVariable String name,
                                   @PathVariable String docId,
                                   Model model) {
        try {
            Map<String, Object> doc = firebaseService.getDocumentById(name, docId);
            List<String> fields = firebaseService.getCollectionFields(name);
            model.addAttribute("collectionName", name);
            model.addAttribute("fields", fields);
            model.addAttribute("document", doc);
            model.addAttribute("docId", docId);
            model.addAttribute("isNew", false);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "form";
    }

    @PostMapping("/collection/{name}/update/{docId}")
    public String updateDocument(@PathVariable String name,
                                 @PathVariable String docId,
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        try {
            Map<String, String> fields = extractFields(request);
            firebaseService.updateDocument(name, docId, fields);
            ra.addFlashAttribute("success", "Registro actualizado exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }
        return "redirect:/collection/" + name;
    }

    // ─── ELIMINAR ────────────────────────────────────────────────────────────

    @PostMapping("/collection/{name}/delete/{docId}")
    public String deleteDocument(@PathVariable String name,
                                 @PathVariable String docId,
                                 RedirectAttributes ra) {
        try {
            firebaseService.deleteDocument(name, docId);
            ra.addFlashAttribute("success", "Registro eliminado exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/collection/" + name;
    }

    // ─── DEBUG ───────────────────────────────────────────────────────────────

    @GetMapping("/api/debug/{name}")
    @ResponseBody
    public Map<String, Object> debug(@PathVariable String name) throws Exception {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("fields", firebaseService.getCollectionFields(name));
        List<Map<String, Object>> docs = firebaseService.getDocuments(name);
        result.put("count", docs.size());
        result.put("first", docs.isEmpty() ? null : docs.get(0));
        return result;
    }

    // ─── ELIMINAR COLECCIÓN ──────────────────────────────────────────────────

    @PostMapping("/collection/{name}/delete-all")
    public String deleteCollection(@PathVariable String name, RedirectAttributes ra) {
        try {
            firebaseService.deleteCollection(name);
            ra.addFlashAttribute("success", "Colección «" + name + "» eliminada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/";
    }

    // ─── UTILIDADES ──────────────────────────────────────────────────────────

    private String sanitizeName(String input) {
        return input.replaceAll("\\.(csv|xlsx|xls)$", "")
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .toLowerCase();
    }

    private String buildSuffix(HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String fecha = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String hora  = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String ip    = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                               .orElse(request.getRemoteAddr())
                               .replace(".", "").replace(":", "").replace("0", "");
        if (ip.length() > 6) ip = ip.substring(ip.length() - 6);
        String rand  = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF)).toUpperCase();
        return "_" + fecha + "_" + hora + "_" + ip + "_" + rand;
    }

    private Map<String, String> extractFields(HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!key.startsWith("_")) {
                fields.put(key, values[0]);
            }
        });
        return fields;
    }
}
