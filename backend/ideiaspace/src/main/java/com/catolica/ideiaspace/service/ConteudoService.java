package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.Conteudo;
import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import com.catolica.ideiaspace.dto.conteudo.ConteudoRequestDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoResponseDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoUpdateDTO;
import com.catolica.ideiaspace.repositories.ConteudoRepository;
import com.catolica.ideiaspace.repositories.TurmaRepository;
import com.catolica.ideiaspace.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class ConteudoService {

    private static final String PERFIL_ADMIN = "ADMIN";

    private final ConteudoRepository conteudoRepository;
    private final TurmaRepository turmaRepository;
    private final EntityManager entityManager;

    public ConteudoService(
            ConteudoRepository conteudoRepository,
            TurmaRepository turmaRepository,
            EntityManager entityManager) {
        this.conteudoRepository = conteudoRepository;
        this.turmaRepository = turmaRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ConteudoResponseDTO cadastrar(ConteudoRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados do conteúdo são obrigatórios.");
        }
        if (dto.turmaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A turma do conteúdo é obrigatória.");
        }

        validarCampos(dto.titulo(), dto.tipoMaterial(), dto.storagePath(), dto.mimeType(), dto.tamanhoBytes(), dto.sha256());

        turmaRepository.findByIdAndExcluidoEmIsNull(dto.turmaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Turma ativa não encontrada com ID: " + dto.turmaId()));

        UserPrincipal usuario = usuarioAtual();
        Long professorId = null;
        if (isAdmin(usuario)) {
            if (dto.professorId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Administradores devem informar o professor autor do conteúdo.");
            }
            professorId = dto.professorId();
        }

        Long novoId = conteudoRepository.criarPorFuncao(
                dto.turmaId(),
                professorId,
                dto.titulo().trim(),
                normalizarTexto(dto.descricao()),
                dto.tipoMaterial().name(),
                dto.storagePath().trim(),
                dto.mimeType().trim(),
                dto.tamanhoBytes(),
                normalizarSha256(dto.sha256()));

        Conteudo criado = conteudoRepository.findByIdAndExcluidoEmIsNull(novoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Conteúdo criado não pôde ser recuperado."));

        return ConteudoResponseDTO.fromEntity(criado);
    }

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

    @Transactional
    public ConteudoResponseDTO atualizar(Long id, ConteudoUpdateDTO dto) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados para atualização são obrigatórios.");
        }

        validarCampos(dto.titulo(), dto.tipoMaterial(), dto.storagePath(), dto.mimeType(), dto.tamanhoBytes(), dto.sha256());

        Conteudo conteudo = buscarAtivo(id);
        verificarPermissaoDeGestao(conteudo);

        conteudo.setTitulo(dto.titulo().trim());
        conteudo.setDescricao(normalizarTexto(dto.descricao()));
        conteudo.setTipoMaterial(dto.tipoMaterial());
        conteudo.setStoragePath(dto.storagePath().trim());
        conteudo.setMimeType(dto.mimeType().trim());
        conteudo.setTamanhoBytes(dto.tamanhoBytes());
        conteudo.setSha256(normalizarSha256(dto.sha256()));

        conteudoRepository.saveAndFlush(conteudo);
        // atualizado_em é preenchido por trigger no banco; recarrega para refletir o valor real.
        entityManager.refresh(conteudo);

        return ConteudoResponseDTO.fromEntity(conteudo);
    }

    @Transactional
    public void excluir(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }

        Conteudo conteudo = buscarAtivo(id);
        verificarPermissaoDeGestao(conteudo);

        conteudoRepository.arquivarPorFuncao(id);
    }

    private Conteudo buscarAtivo(Long id) {
        return conteudoRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conteúdo não encontrado com ID: " + id));
    }

    /*
     * Regra: somente o professor autor ou um ADMIN pode editar ou arquivar o conteúdo.
     * O banco aplica a mesma regra (políticas de UPDATE e fn_arquivar_conteudo); esta
     * verificação antecipada só existe para devolver 403 com mensagem clara.
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

    private void validarCampos(String titulo, TipoMaterial tipoMaterial, String storagePath,
                               String mimeType, Long tamanhoBytes, String sha256) {
        if (titulo == null || titulo.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título do conteúdo deve ter no mínimo 2 caracteres.");
        }
        if (titulo.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título do conteúdo deve ter no máximo 255 caracteres.");
        }
        if (tipoMaterial == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo de material é obrigatório.");
        }
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O caminho de armazenamento é obrigatório.");
        }
        String caminho = storagePath.trim();
        if (caminho.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O caminho de armazenamento deve ter no máximo 2048 caracteres.");
        }
        String caminhoMinusculo = caminho.toLowerCase(Locale.ROOT);
        if (caminhoMinusculo.startsWith("http://") || caminhoMinusculo.startsWith("https://") || caminho.startsWith("//")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O caminho de armazenamento deve ser interno, não uma URL.");
        }
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo MIME é obrigatório.");
        }
        if (mimeType.trim().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo MIME deve ter no máximo 150 caracteres.");
        }
        if (tamanhoBytes == null || tamanhoBytes < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tamanho do arquivo deve ser um valor não negativo.");
        }
        if (sha256 != null && !sha256.isBlank() && !sha256.trim().matches("^[0-9a-fA-F]{64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O SHA-256 deve conter 64 caracteres hexadecimais.");
        }
    }

    private static String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private static String normalizarSha256(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return null;
        }
        return sha256.trim().toLowerCase(Locale.ROOT);
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
