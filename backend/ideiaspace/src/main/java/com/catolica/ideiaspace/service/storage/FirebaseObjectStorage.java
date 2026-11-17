package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Armazenamento dos materiais no Firebase Storage (Google Cloud Storage), provedor "firebase".
 *
 * - Objetos PRIVADOS: nada é tornado público e nenhuma regra de acesso do bucket é alterada.
 * - O envio passa SEMPRE pelo backend: o navegador nunca recebe credenciais nem faz upload direto.
 * - A validação (tipo pelo conteúdo, tamanho, SHA-256) é a mesma do provedor local
 *   ({@link UploadInspector}) e acontece ANTES de qualquer byte ser enviado ao bucket.
 * - O nome do objeto é gerado pelo servidor (materiais/AAAA/MM/uuid.ext) e criado com a
 *   pré-condição "não existe", impedindo sobrescrita acidental.
 * - A leitura pelo aluno usa Signed URL V4 de curta duração (prazo configurável).
 * - Credenciais: Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS). Nenhum segredo
 *   é registrado em log.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "firebase")
public class FirebaseObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(FirebaseObjectStorage.class);

    private static final String PREFIXO_OBJETOS = "materiais/";
    private static final int TAMANHO_MAXIMO_NOME = 1024;
    private static final int HTTP_PRECONDITION_FAILED = 412;

    private final Storage storage;
    private final String bucket;
    private final StorageProperties props;
    private final Path temporario;

    @Autowired
    public FirebaseObjectStorage(StorageProperties props) {
        this(criarCliente(props), props.getFirebaseBucket(), props);
    }

    /** Construtor para testes: recebe um cliente Storage já configurado (ou simulado). */
    public FirebaseObjectStorage(Storage storage, String bucket, StorageProperties props) {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException(
                    "FIREBASE_STORAGE_BUCKET deve ser informado quando APP_STORAGE_PROVIDER=firebase.");
        }
        this.storage = storage;
        this.bucket = bucket.trim();
        this.props = props;
        this.temporario = Path.of(System.getProperty("java.io.tmpdir"), "ideiaspace-upload");
        log.info("Armazenamento de materiais: Firebase Storage (bucket '{}')", this.bucket);
    }

    private static Storage criarCliente(StorageProperties props) {
        if (!StringUtils.hasText(props.getFirebaseBucket())) {
            throw new IllegalStateException(
                    "FIREBASE_STORAGE_BUCKET deve ser informado quando APP_STORAGE_PROVIDER=firebase.");
        }
        try {
            // Application Default Credentials: GOOGLE_APPLICATION_CREDENTIALS (arquivo JSON da conta
            // de serviço, fora do repositório) ou a identidade do ambiente (ex.: Cloud Run).
            GoogleCredentials credenciais = GoogleCredentials.getApplicationDefault();
            if (!(credenciais instanceof ServiceAccountSigner)) {
                log.warn("As credenciais carregadas não conseguem assinar URLs localmente. "
                        + "Use uma conta de serviço (arquivo JSON) ou, no Google Cloud, uma identidade com "
                        + "permissão iam.serviceAccounts.signBlob (papel Service Account Token Creator).");
            }
            StorageOptions.Builder builder = StorageOptions.newBuilder().setCredentials(credenciais);
            if (StringUtils.hasText(props.getFirebaseProjectId())) {
                builder.setProjectId(props.getFirebaseProjectId());
            }
            return builder.build().getService();
        } catch (IOException e) {
            // Mensagem sem o conteúdo do arquivo de credenciais.
            throw new IllegalStateException(
                    "Não foi possível carregar as credenciais do Google Cloud (GOOGLE_APPLICATION_CREDENTIALS).", e);
        }
    }

    @Override
    public String provedor() {
        return "firebase";
    }

    // ------------------------------------------------------------------ gravação

    @Override
    public ArquivoArmazenado salvar(MultipartFile arquivo) {
        // 1) Validação completa ANTES de falar com o bucket (arquivo inválido nunca é enviado).
        UploadInspector.Inspecionado inspecionado = UploadInspector.inspecionar(arquivo, temporario, props);
        String nome = null;
        try {
            TipoArquivo tipo = inspecionado.tipo();
            LocalDate hoje = LocalDate.now();
            nome = PREFIXO_OBJETOS + hoje.getYear() + "/" + String.format("%02d", hoje.getMonthValue())
                    + "/" + UUID.randomUUID() + "." + tipo.extensao();

            BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, nome))
                    .setContentType(tipo.mimeType())
                    .setCacheControl("private, no-store")
                    .setMetadata(Map.of("sha256", inspecionado.sha256()))
                    .build();

            // 2) Envio (upload resumível para arquivos grandes), com pré-condição de não sobrescrever.
            storage.createFrom(info, inspecionado.temporario(), Storage.BlobWriteOption.doesNotExist());

            return new ArquivoArmazenado(nome, tipo, tipo.mimeType(),
                    inspecionado.tamanhoBytes(), inspecionado.sha256());
        } catch (StorageException | IOException e) {
            log.error("Falha ao enviar material ao Firebase Storage ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
            boolean conflito = e instanceof StorageException se && se.getCode() == HTTP_PRECONDITION_FAILED;
            if (nome != null && !conflito) {
                remover(nome); // melhor esforço: não deixa objeto órfão se o envio falhou após finalizar
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível armazenar o arquivo. Tente novamente em instantes.");
        } finally {
            UploadInspector.descartar(inspecionado.temporario());
        }
    }

    // ------------------------------------------------------------------ consulta / remoção

    @Override
    public boolean existe(String storagePath) {
        if (!nomeValido(storagePath)) {
            return false;
        }
        try {
            Blob blob = storage.get(BlobId.of(bucket, storagePath),
                    Storage.BlobGetOption.fields(Storage.BlobField.NAME));
            return blob != null;
        } catch (StorageException e) {
            log.error("Falha ao consultar objeto no Firebase Storage ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O armazenamento de arquivos está indisponível no momento.");
        }
    }

    @Override
    public void remover(String storagePath) {
        if (!nomeValido(storagePath)) {
            return;
        }
        try {
            storage.delete(BlobId.of(bucket, storagePath));
        } catch (RuntimeException e) {
            log.warn("Não foi possível remover objeto do Firebase Storage: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Resource abrir(String storagePath, long tamanhoBytes) {
        if (!nomeValido(storagePath)) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido.");
        }
        return new GcsBlobResource(storage, BlobId.of(bucket, storagePath), tamanhoBytes);
    }

    // ------------------------------------------------------------------ Signed URL V4

    @Override
    public UrlTemporaria gerarUrlLeitura(String storagePath, String mimeType, Duration validade) {
        if (!nomeValido(storagePath)) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido.");
        }
        long segundos = Math.max(1, validade.getSeconds());
        try {
            BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, storagePath)).build();
            // O navegador recebe o arquivo para exibição (inline) com o tipo correto; sem "attachment".
            Map<String, String> consulta = Map.of(
                    "response-content-type", mimeType,
                    "response-content-disposition", "inline");
            Instant expiraEm = Instant.now().plusSeconds(segundos);
            URL url = storage.signUrl(info, segundos, TimeUnit.SECONDS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature(),
                    Storage.SignUrlOption.withQueryParams(consulta));
            return new UrlTemporaria(url.toString(), expiraEm);
        } catch (RuntimeException e) {
            // IllegalStateException/SigningException: credenciais sem capacidade de assinatura.
            log.error("Falha ao gerar Signed URL ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível gerar o acesso temporário ao arquivo. Tente novamente em instantes.");
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Aceita somente nomes gerados por este provedor: materiais/..., sem traversal nem barras invertidas. */
    private static boolean nomeValido(String nome) {
        if (!StringUtils.hasText(nome) || nome.length() > TAMANHO_MAXIMO_NOME) {
            return false;
        }
        if (!nome.startsWith(PREFIXO_OBJETOS) || nome.contains("\\") || nome.indexOf('\0') >= 0) {
            return false;
        }
        for (String parte : nome.split("/", -1)) {
            if (parte.isEmpty() || parte.equals(".") || parte.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
