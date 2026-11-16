package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.Conteudo;
import com.catolica.ideiaspace.domain.TurmaUsuario;
import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.StatusMatricula;
import com.catolica.ideiaspace.domain.enums.StatusPublicacao;
import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import com.catolica.ideiaspace.dto.conteudo.ConteudoResponseDTO;
import com.catolica.ideiaspace.repositories.ConteudoRepository;
import com.catolica.ideiaspace.repositories.TurmaRepository;
import com.catolica.ideiaspace.repositories.TurmaUsuarioRepository;
import com.catolica.ideiaspace.security.UserPrincipal;
import com.catolica.ideiaspace.service.storage.ArquivoArmazenado;
import com.catolica.ideiaspace.service.storage.FileStorageService;
import com.catolica.ideiaspace.service.storage.TipoArquivo;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negócio dos materiais (cadastro, edição, publicação, exclusão lógica, leitura do arquivo).
 *
 * O upload de arquivos grandes NÃO acontece dentro de uma transação de banco: o arquivo é
 * validado e gravado primeiro, e só depois a(s) operação(ões) de banco rodam em transação
 * curta. Se o banco falhar, o arquivo recém-gravado é removido; ao substituir um arquivo,
 * o antigo só é removido depois do commit.
 */
@Service
public class ConteudoService {

    private static final String PERFIL_ADMIN = "ADMIN";
    private static final String PERFIL_DOCENTE = "DOCENTE";
    private static final int TITULO_MIN = 2;
    private static final int TITULO_MAX = 255;
    private static final int DESCRICAO_MAX = 2000;

    private final ConteudoRepository conteudoRepository;
    private final TurmaRepository turmaRepository;
    private final TurmaUsuarioRepository turmaUsuarioRepository;
    private final FileStorageService storage;
    private final EntityManager entityManager;
    private final TransactionTemplate tx;
    private final TransactionTemplate txLeitura;

    public ConteudoService(
            ConteudoRepository conteudoRepository,
            TurmaRepository turmaRepository,
            TurmaUsuarioRepository turmaUsuarioRepository,
            FileStorageService storage,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.conteudoRepository = conteudoRepository;
        this.turmaRepository = turmaRepository;
        this.turmaUsuarioRepository = turmaUsuarioRepository;
        this.storage = storage;
        this.entityManager = entityManager;
        this.tx = new TransactionTemplate(transactionManager);
        this.txLeitura = new TransactionTemplate(transactionManager);
        this.txLeitura.setReadOnly(true);
    }

    // ------------------------------------------------------------------ cadastro

    public ConteudoResponseDTO cadastrar(
            Long turmaId,
            Long professorId,
            String titulo,
            String descricao,
            TipoMaterial tipoMaterial,
            StatusPublicacao statusPublicacao,
            MultipartFile arquivo) {

        if (turmaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A turma do conteúdo é obrigatória.");
        }
        validarTextos(titulo, descricao);
        if (tipoMaterial == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo de material é obrigatório.");
        }
        StatusPublicacao status = statusPublicacao != null ? statusPublicacao : StatusPublicacao.RASCUNHO;

        // 1) Autorização barata ANTES de aceitar/gravar bytes.
        Long autorId = txLeitura.execute(st -> autorizarCadastro(turmaId, professorId));

        // 2) Validação e gravação do arquivo (fora de transação de banco).
        ArquivoArmazenado salvo = storage.salvar(arquivo);
        try {
            validarCompatibilidade(tipoMaterial, salvo.tipo());

            // 3) Persistência (a função do banco reaplica vínculo DOCENTE e RLS).
            return tx.execute(st -> {
                Long novoId = conteudoRepository.criarPorFuncao(
                        turmaId,
                        autorId,
                        titulo.trim(),
                        normalizarTexto(descricao),
                        tipoMaterial.name(),
                        salvo.storagePath(),
                        salvo.mimeType(),
                        salvo.tamanhoBytes(),
                        salvo.sha256(),
                        status.name());

                Conteudo criado = conteudoRepository.findByIdAndExcluidoEmIsNull(novoId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Conteúdo criado não pôde ser recuperado."));
                return ConteudoResponseDTO.fromEntity(criado);
            });
        } catch (RuntimeException e) {
            storage.remover(salvo.storagePath());
            throw e;
        }
    }

    /**
     * Retorna o id do professor autor (null quando o autor é o próprio usuário docente; a função
     * do banco usa o usuário da sessão nesse caso).
     */
    private Long autorizarCadastro(Long turmaId, Long professorId) {
        turmaRepository.findByIdAndExcluidoEmIsNull(turmaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Turma ativa não encontrada com ID: " + turmaId));

        UserPrincipal usuario = usuarioAtual();
        if (isAdmin(usuario)) {
            if (professorId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Administradores devem informar o professor autor do conteúdo.");
            }
            return professorId;
        }
        if (!PERFIL_DOCENTE.equals(usuario.perfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas professores e administradores podem cadastrar conteúdos.");
        }
        Optional<TurmaUsuario> vinculo = turmaUsuarioRepository
                .findByTurmaIdAndUsuarioIdAndStatusAndExcluidoEmIsNull(turmaId, usuario.id(), StatusMatricula.ATIVA);
        if (vinculo.isEmpty() || vinculo.get().getPapel() != PapelTurma.DOCENTE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas professores vinculados como DOCENTE à turma podem cadastrar materiais nela.");
        }
        // O autor de um docente é sempre ele mesmo; qualquer professorId enviado é ignorado.
        return null;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<ConteudoResponseDTO> listar(Long turmaId) {
        List<Conteudo> conteudos = (turmaId != null)
                ? conteudoRepository.findByTurmaIdAndExcluidoEmIsNullOrderByCriadoEmDesc(turmaId)
                : conteudoRepository.findByExcluidoEmIsNullOrderByCriadoEmDesc();

        return conteudos.stream()
                .map(ConteudoResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConteudoResponseDTO buscarPorId(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
        return ConteudoResponseDTO.fromEntity(buscarAtivo(id));
    }

    /** Arquivo físico de um material, para leitura autenticada (autor ou administrador). */
    public record ArquivoConteudo(Path caminho, String mimeType, long tamanhoBytes, String nomeSugerido) {}

    @Transactional(readOnly = true)
    public ArquivoConteudo obterArquivo(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
        Conteudo conteudo = buscarAtivo(id);
        // Entrega do arquivo a alunos fica para o visualizador protegido (marca d'água, log, URL
        // temporária). Aqui apenas quem gerencia o material pode conferir o arquivo.
        verificarPermissaoDeGestao(conteudo);

        Path caminho;
        try {
            caminho = storage.resolver(conteudo.getStoragePath());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo do material não encontrado.");
        }
        if (!Files.isRegularFile(caminho)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo do material não encontrado.");
        }
        String mime = conteudo.getMimeType();
        String extensao = "video/webm".equals(mime) ? "webm" : "video/mp4".equals(mime) ? "mp4" : "pdf";
        return new ArquivoConteudo(caminho, mime, conteudo.getTamanhoBytes(),
                "material-" + conteudo.getId() + "." + extensao);
    }

    // ------------------------------------------------------------------ edição

    private record ResultadoEdicao(String caminhoAntigo, ConteudoResponseDTO dto) {}

    public ConteudoResponseDTO atualizar(
            Long id,
            String titulo,
            String descricao,
            TipoMaterial tipoMaterial,
            MultipartFile arquivo) {

        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
        validarTextos(titulo, descricao);

        // 1) Autorização e leitura do tipo atual ANTES de aceitar bytes.
        TipoMaterial tipoAtual = txLeitura.execute(st -> {
            Conteudo c = buscarAtivo(id);
            verificarPermissaoDeGestao(c);
            return c.getTipoMaterial();
        });
        if (tipoMaterial != null && familiaPdf(tipoMaterial) != familiaPdf(tipoAtual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O tipo do material não pode ser alterado entre PDF e vídeo.");
        }
        TipoMaterial tipoFinal = tipoMaterial != null ? tipoMaterial : tipoAtual;

        // 2) Substituição de arquivo (opcional).
        boolean substituir = arquivo != null
                && (!arquivo.isEmpty() || (arquivo.getOriginalFilename() != null && !arquivo.getOriginalFilename().isBlank()));
        ArquivoArmazenado novo = substituir ? storage.salvar(arquivo) : null;

        try {
            if (novo != null) {
                validarCompatibilidade(tipoFinal, novo.tipo());
            }

            // 3) Persistência.
            ResultadoEdicao resultado = tx.execute(st -> {
                Conteudo conteudo = buscarAtivo(id);
                verificarPermissaoDeGestao(conteudo);

                String antigo = conteudo.getStoragePath();
                conteudo.setTitulo(titulo.trim());
                conteudo.setDescricao(normalizarTexto(descricao));
                conteudo.setTipoMaterial(tipoFinal);
                if (novo != null) {
                    conteudo.setStoragePath(novo.storagePath());
                    conteudo.setMimeType(novo.mimeType());
                    conteudo.setTamanhoBytes(novo.tamanhoBytes());
                    conteudo.setSha256(novo.sha256());
                }
                conteudoRepository.saveAndFlush(conteudo);
                // atualizado_em é preenchido por trigger no banco; recarrega para refletir o valor real.
                entityManager.refresh(conteudo);
                return new ResultadoEdicao(novo != null ? antigo : null, ConteudoResponseDTO.fromEntity(conteudo));
            });

            // 4) Só depois do commit o arquivo substituído é descartado.
            if (resultado.caminhoAntigo() != null) {
                storage.remover(resultado.caminhoAntigo());
            }
            return resultado.dto();
        } catch (RuntimeException e) {
            if (novo != null) {
                storage.remover(novo.storagePath());
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ publicação

    @Transactional
    public ConteudoResponseDTO alterarStatus(Long id, StatusPublicacao novoStatus) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
        if (novoStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O status de publicação é obrigatório.");
        }
        Conteudo conteudo = buscarAtivo(id);
        verificarPermissaoDeGestao(conteudo);

        if (conteudo.getStatusPublicacao() != novoStatus) {
            conteudo.setStatusPublicacao(novoStatus);
            conteudoRepository.saveAndFlush(conteudo);
            entityManager.refresh(conteudo);
        }
        return ConteudoResponseDTO.fromEntity(conteudo);
    }

    // ------------------------------------------------------------------ exclusão lógica

    /**
     * Exclusão lógica (fn_arquivar_conteudo). O arquivo físico é mantido no armazenamento para
     * permitir auditoria/recuperação; ele deixa de ser acessível pela API junto com o registro.
     */
    @Transactional
    public void excluir(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }

        Conteudo conteudo = buscarAtivo(id);
        verificarPermissaoDeGestao(conteudo);

        conteudoRepository.arquivarPorFuncao(id);
    }

    // ------------------------------------------------------------------ apoio

    private Conteudo buscarAtivo(Long id) {
        return conteudoRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conteúdo não encontrado com ID: " + id));
    }

    /*
     * Regra: somente o professor autor ou um ADMIN pode editar, publicar, despublicar, excluir
     * ou conferir o arquivo do conteúdo. O banco aplica a mesma regra (políticas de UPDATE e
     * fn_arquivar_conteudo); esta verificação antecipada só existe para devolver 403 com
     * mensagem clara. Aluno nunca chega aqui com sucesso: rascunhos são invisíveis (404) e
     * conteúdo publicado resulta em 403.
     */
    private void verificarPermissaoDeGestao(Conteudo conteudo) {
        UserPrincipal usuario = usuarioAtual();
        if (isAdmin(usuario)) {
            return;
        }
        boolean ehAutor = conteudo.getProfessor() != null
                && conteudo.getProfessor().getId().equals(usuario.id());
        if (!ehAutor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o professor autor ou um administrador pode alterar este conteúdo.");
        }
    }

    private static void validarTextos(String titulo, String descricao) {
        if (titulo == null || titulo.trim().length() < TITULO_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título do conteúdo deve ter no mínimo 2 caracteres.");
        }
        if (titulo.trim().length() > TITULO_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título do conteúdo deve ter no máximo 255 caracteres.");
        }
        if (descricao != null && descricao.trim().length() > DESCRICAO_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A descrição deve ter no máximo 2000 caracteres.");
        }
    }

    private static void validarCompatibilidade(TipoMaterial tipoMaterial, TipoArquivo tipoArquivo) {
        boolean ok = familiaPdf(tipoMaterial) ? tipoArquivo.isPdf() : tipoArquivo.isVideo();
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    familiaPdf(tipoMaterial)
                            ? "O arquivo enviado não é um PDF, mas o material foi cadastrado como PDF."
                            : "O arquivo enviado não é um vídeo MP4/WebM, mas o material foi cadastrado como videoaula.");
        }
    }

    private static boolean familiaPdf(TipoMaterial tipo) {
        return tipo == TipoMaterial.PDF_LIVRO || tipo == TipoMaterial.PDF_APOSTILA;
    }

    private static String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private static UserPrincipal usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }
        return principal;
    }

    private static boolean isAdmin(UserPrincipal usuario) {
        return PERFIL_ADMIN.equals(usuario.perfil());
    }
}
