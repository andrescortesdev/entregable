package com.poli.entregable.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    // En producción: variable de entorno FIREBASE_CREDENTIALS_BASE64 (JSON en base64)
    // En desarrollo: archivo src/main/resources/firebase/serviceAccountKey.json
    @Value("${FIREBASE_CREDENTIALS_BASE64:}")
    private String credentialsBase64;

    @Value("${firebase.credentials.path:firebase/serviceAccountKey.json}")
    private String credentialsPath;

    @Bean
    public Firestore firestore() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccount = resolveCredentials();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }

    private InputStream resolveCredentials() throws IOException {
        // Prioridad 1: variable de entorno (producción / Docker)
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64.trim());
            return new ByteArrayInputStream(decoded);
        }
        // Prioridad 2: archivo local (desarrollo)
        return new ClassPathResource(credentialsPath).getInputStream();
    }
}
