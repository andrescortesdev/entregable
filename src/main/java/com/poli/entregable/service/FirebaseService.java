package com.poli.entregable.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class FirebaseService {

    private final Firestore firestore;

    public FirebaseService(Firestore firestore) {
        this.firestore = firestore;
    }

    // Subir lista de registros a una colección (la crea si no existe)
    public int uploadRecords(String collectionName, List<Map<String, String>> records)
            throws ExecutionException, InterruptedException {
        CollectionReference col = firestore.collection(collectionName);
        WriteBatch batch = firestore.batch();
        int count = 0;
        for (Map<String, String> record : records) {
            DocumentReference doc = col.document();
            batch.set(doc, record);
            count++;
            // Firestore limita a 500 ops por batch
            if (count % 499 == 0) {
                batch.commit().get();
                batch = firestore.batch();
            }
        }
        batch.commit().get();
        return count;
    }

    // Listar todas las colecciones disponibles
    public List<String> listCollections() throws ExecutionException, InterruptedException {
        Iterable<CollectionReference> collections = firestore.listCollections();
        List<String> names = new ArrayList<>();
        for (CollectionReference col : collections) {
            names.add(col.getId());
        }
        Collections.sort(names);
        return names;
    }

    // Obtener todos los documentos de una colección
    public List<Map<String, Object>> getDocuments(String collectionName)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(collectionName).get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_id", doc.getId());
            data.putAll(doc.getData());
            result.add(data);
        }
        return result;
    }

    // Obtener un documento por ID
    public Map<String, Object> getDocumentById(String collectionName, String docId)
            throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(collectionName).document(docId).get().get();
        if (!doc.exists()) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", doc.getId());
        data.putAll(Objects.requireNonNull(doc.getData()));
        return data;
    }

    // Crear un documento nuevo
    public void createDocument(String collectionName, Map<String, String> fields)
            throws ExecutionException, InterruptedException {
        firestore.collection(collectionName).document().set(fields).get();
    }

    // Actualizar un documento existente
    public void updateDocument(String collectionName, String docId, Map<String, String> fields)
            throws ExecutionException, InterruptedException {
        Map<String, Object> updates = new HashMap<>(fields);
        firestore.collection(collectionName).document(docId).set(updates).get();
    }

    // Eliminar un documento
    public void deleteDocument(String collectionName, String docId)
            throws ExecutionException, InterruptedException {
        firestore.collection(collectionName).document(docId).delete().get();
    }

    // Buscar documentos por campo y valor
    public List<Map<String, Object>> searchDocuments(String collectionName, String field, String value)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(collectionName)
                .whereEqualTo(field, value).get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_id", doc.getId());
            data.putAll(doc.getData());
            result.add(data);
        }
        return result;
    }

    // Obtener las columnas/campos de la colección (del primer documento)
    public List<String> getCollectionFields(String collectionName)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(collectionName).limit(1).get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        if (docs.isEmpty()) return new ArrayList<>();
        return docs.get(0).getData().keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    // Eliminar colección completa
    public void deleteCollection(String collectionName)
            throws ExecutionException, InterruptedException {
        CollectionReference col = firestore.collection(collectionName);
        ApiFuture<QuerySnapshot> future = col.get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        WriteBatch batch = firestore.batch();
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            batch.delete(doc.getReference());
            count++;
            if (count % 499 == 0) {
                batch.commit().get();
                batch = firestore.batch();
            }
        }
        batch.commit().get();
    }
}
