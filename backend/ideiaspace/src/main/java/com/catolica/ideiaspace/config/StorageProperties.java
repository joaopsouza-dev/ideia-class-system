package com.catolica.ideiaspace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Configuração do armazenamento privado de materiais.
 *
 * Provedores (APP_STORAGE_PROVIDER):
 *  - local    (padrão): arquivos em disco, em diretório privado fora de qualquer pasta estática.
 *             Usado em desenvolvimento e nos testes automatizados.
 *  - firebase: objetos privados em um bucket do Firebase Storage / Google Cloud Storage.
 *
 * Nenhum segredo fica aqui: as credenciais vêm das Application Default Credentials do Google
 * (variável GOOGLE_APPLICATION_CREDENTIALS apontando para o JSON da conta de serviço, FORA do
 * repositório, ou a identidade do ambiente em execução).
 */
@Component
public class StorageProperties {

    private static final long BYTES_POR_MB = 1024L * 1024L;

    /** Limites do prazo das URLs temporárias. O V4 do GCS aceita até 7 dias; aqui o teto é 1 hora. */
    public static final long TTL_MINIMO_SEGUNDOS = 30;
    public static final long TTL_MAXIMO_SEGUNDOS = 3600;

    @Value("${APP_STORAGE_ROOT:./data/storage}")
    private String root;

    @Value("${APP_UPLOAD_MAX_PDF_MB:50}")
    private long maxPdfMb;

    @Value("${APP_UPLOAD_MAX_VIDEO_MB:500}")
    private long maxVideoMb;

    @Value("${APP_STORAGE_PROVIDER:local}")
    private String provider;

    @Value("${FIREBASE_STORAGE_BUCKET:}")
    private String firebaseBucket;

    @Value("${FIREBASE_PROJECT_ID:}")
    private String firebaseProjectId;

    @Value("${APP_STORAGE_SIGNED_URL_TTL_SECONDS:900}")
    // Mantém o mesmo padrão declarado em @Value também em instâncias criadas diretamente
    // (por exemplo, nos testes unitários sem o contêiner do Spring).
    private long signedUrlTtlSeconds = 900;

    public String getRoot() {
        return root;
    }

    public long getMaxPdfBytes() {
        return maxPdfMb * BYTES_POR_MB;
    }

    public long getMaxVideoBytes() {
        return maxVideoMb * BYTES_POR_MB;
    }

    public long getMaxPdfMb() {
        return maxPdfMb;
    }

    public long getMaxVideoMb() {
        return maxVideoMb;
    }

    public String getProvider() {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "local";
    }

    public String getFirebaseBucket() {
        return firebaseBucket == null ? "" : firebaseBucket.trim();
    }

    public String getFirebaseProjectId() {
        return firebaseProjectId == null ? "" : firebaseProjectId.trim();
    }

    /** Prazo das URLs temporárias, sempre dentro de [TTL_MINIMO_SEGUNDOS, TTL_MAXIMO_SEGUNDOS]. */
    public Duration getSignedUrlTtl() {
        long segundos = Math.min(Math.max(signedUrlTtlSeconds, TTL_MINIMO_SEGUNDOS), TTL_MAXIMO_SEGUNDOS);
        return Duration.ofSeconds(segundos);
    }
}
