package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.config.StorageProperties;
import com.catolica.ideiaspace.domain.Conteudo;
import com.catolica.ideiaspace.domain.enums.AcaoLog;
import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import com.catolica.ideiaspace.dto.conteudo.AcaoEventoCliente;
import com.catolica.ideiaspace.dto.conteudo.AcessoConteudoResponseDTO;
import com.catolica.ideiaspace.repositories.ConteudoRepository;
import com.catolica.ideiaspace.service.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Visualização protegida dos materiais.
 *
 * Fluxo de {@link #abrir}: (1) o conteúdo é lido sob RLS com o usuário da requisição — aluno só
 * enxerga PUBLICADO das próprias turmas; rascunho só para admin/autor; (2) o objeto é conferido
 * no armazenamento; (3) é gerada a URL temporária; (4) o evento ABRIU_PDF / INICIOU_VIDEO é
 * gravado por fn_registrar_acesso, que reaplica a regra de acesso no banco. Tudo na MESMA
 * transação: se qualquer etapa falhar, nenhuma URL é devolvida e nenhum evento fica gravado.
 *
 * Conteúdo inexistente, de outra turma, rascunho ou sem permissão geram a MESMA resposta (404),
 * sem revelar a existência do material. Quando o conteúdo existe, a tentativa é registrada
 * como TENTATIVA_ACESSO_NEGADO em uma transação própria.
 *
 * IP e User-Agent vêm do servidor (ClienteRequisicao), nunca do cliente.
 */
@Service
public class VisualizacaoService {

    private static final Logger log = LoggerFactory.getLogger(VisualizacaoService.class);

    private static final String MSG_NAO_ENCONTRADO = "Conteúdo não encontrado com ID: ";
    private static final int DETALHE_MAX = 200;

    private static final String SQL_PREFIXO =
            "SELECT public.fn_registrar_acesso(CAST(? AS BIGINT), CAST(? AS acao_log_enum), "
                    + "CAST(? AS INET), CAST(? AS TEXT), ";
    private static final String SQL_ABERTURA = SQL_PREFIXO
            + "jsonb_build_object('origem', 'servidor', 'provedor', CAST(? AS TEXT), "
            + "'ttlSegundos', CAST(? AS INTEGER)))::text";
    private static final String SQL_CLIENTE = SQL_PREFIXO
            + "jsonb_build_object('origem', 'cliente', 'detalhe', CAST(? AS TEXT)))::text";
    private static final String SQL_NEGADO = SQL_PREFIXO
            + "jsonb_build_object('origem', 'servidor', 'motivo', CAST(? AS TEXT)))::text";

    private final ConteudoRepository conteudoRepository;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public VisualizacaoService(
            ConteudoRepository conteudoRepository,
            ObjectStorage storage,
            StorageProperties props,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.conteudoRepository = conteudoRepository;
        this.storage = storage;
        this.props = props;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Falha de autorização interna: provoca rollback e vira 404 para o cliente. */
    private static final class AcessoNegado extends RuntimeException {
        private final String motivo;

        AcessoNegado(String motivo) {
            super(motivo, null, false, false);
            this.motivo = motivo;
        }
    }

    // ------------------------------------------------------------------ abertura

    public AcessoConteudoResponseDTO abrir(Long conteudoId, ClienteRequisicao cliente) {
        exigirId(conteudoId);
        Duration validade = props.getSignedUrlTtl();
        try {
            return tx.execute(st -> emitirAcesso(conteudoId, cliente, validade));
        } catch (AcessoNegado e) {
            registrarNegado(conteudoId, cliente, e.motivo);
            throw naoEncontrado(conteudoId);
        } catch (DataAccessException e) {
            if (permissaoNegada(e)) {
                registrarNegado(conteudoId, cliente, "permissao_banco");
                throw naoEncontrado(conteudoId);
            }
            throw e;
        }
    }

    private AcessoConteudoResponseDTO emitirAcesso(Long conteudoId, ClienteRequisicao cliente, Duration validade) {
        // RLS: aluno não enxerga rascunho nem material de turma alheia; docente só o que lhe cabe.
        Conteudo conteudo = conteudoRepository.findByIdAndExcluidoEmIsNull(conteudoId)
                .orElseThrow(() -> new AcessoNegado("nao_visivel"));

        String caminho = conteudo.getStoragePath();
        if (!StringUtils.hasText(caminho) || !storage.existe(caminho)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo do material não encontrado.");
        }

        ObjectStorage.UrlTemporaria url = storage.gerarUrlLeitura(caminho, conteudo.getMimeType(), validade);

        boolean video = conteudo.getTipoMaterial() == TipoMaterial.VIDEO_AULA;
        AcaoLog acao = video ? AcaoLog.INICIOU_VIDEO : AcaoLog.ABRIU_PDF;
        // Reaplica a permissão no banco (usuario_pode_acessar_conteudo) e grava o evento.
        jdbc.queryForObject(SQL_ABERTURA, String.class,
                conteudoId, acao.name(), cliente.ip(), cliente.userAgent(),
                storage.provedor(), (int) validade.toSeconds());

        return new AcessoConteudoResponseDTO(
                url.url(),
                OffsetDateTime.ofInstant(url.expiraEm(), ZoneOffset.UTC),
                video ? "VIDEO" : "PDF",
                conteudo.getMimeType());
    }

    // ------------------------------------------------------------------ eventos do navegador

    /** Registra uma tentativa reportada pelo navegador (download/impressão/captura). */
    public void registrarEvento(Long conteudoId, AcaoEventoCliente acao, String detalhe, ClienteRequisicao cliente) {
        exigirId(conteudoId);
        if (acao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ação do evento é obrigatória.");
        }
        String texto = higienizar(detalhe);
        tx.executeWithoutResult(st -> {
            conteudoRepository.findByIdAndExcluidoEmIsNull(conteudoId)
                    .orElseThrow(() -> naoEncontrado(conteudoId));
            jdbc.queryForObject(SQL_CLIENTE, String.class,
                    conteudoId, acao.acaoLog().name(), cliente.ip(), cliente.userAgent(), texto);
        });
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Grava TENTATIVA_ACESSO_NEGADO em transação própria. Se o conteúdo nem existir, a função do
     * banco recusa e nada é gravado; essa falha é ignorada de propósito para não alterar a resposta.
     */
    private void registrarNegado(Long conteudoId, ClienteRequisicao cliente, String motivo) {
        try {
            tx.executeWithoutResult(st -> jdbc.queryForObject(SQL_NEGADO, String.class,
                    conteudoId, AcaoLog.TENTATIVA_ACESSO_NEGADO.name(),
                    cliente.ip(), cliente.userAgent(), motivo));
        } catch (RuntimeException e) {
            log.debug("Tentativa negada não registrada (conteúdo inexistente ou sessão inválida): {}",
                    e.getClass().getSimpleName());
        }
    }

    private static boolean permissaoNegada(DataAccessException e) {
        Throwable raiz = NestedExceptionUtils.getMostSpecificCause(e);
        return raiz instanceof SQLException sql && "42501".equals(sql.getSQLState());
    }

    private static void exigirId(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID do conteúdo é obrigatório.");
        }
    }

    private static ResponseStatusException naoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NAO_ENCONTRADO + id);
    }

    private static String higienizar(String detalhe) {
        if (detalhe == null) {
            return null;
        }
        String limpo = detalhe.replaceAll("\\p{Cntrl}", " ").trim();
        if (limpo.isEmpty()) {
            return null;
        }
        return limpo.length() > DETALHE_MAX ? limpo.substring(0, DETALHE_MAX) : limpo;
    }
}
