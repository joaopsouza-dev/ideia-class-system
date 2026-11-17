package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.domain.enums.StatusPublicacao;
import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import com.catolica.ideiaspace.dto.conteudo.AcessoConteudoResponseDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoResponseDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoStatusDTO;
import com.catolica.ideiaspace.dto.conteudo.EventoAcessoRequestDTO;
import com.catolica.ideiaspace.service.ClienteRequisicao;
import com.catolica.ideiaspace.service.ConteudoService;
import com.catolica.ideiaspace.service.VisualizacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conteudos")
public class ConteudoController {

    private final ConteudoService conteudoService;
    private final VisualizacaoService visualizacaoService;

    public ConteudoController(ConteudoService conteudoService, VisualizacaoService visualizacaoService) {
        this.conteudoService = conteudoService;
        this.visualizacaoService = visualizacaoService;
    }

    @GetMapping
    public ResponseEntity<List<ConteudoResponseDTO>> listar(
            @RequestParam(name = "turmaId", required = false) Long turmaId) {
        return ResponseEntity.ok(conteudoService.listar(turmaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConteudoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(conteudoService.buscarPorId(id));
    }

    /**
     * Cadastro com upload real (multipart/form-data). O cliente NÃO informa caminho, tipo MIME,
     * tamanho nem hash: tudo isso é determinado pelo servidor a partir do arquivo recebido.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConteudoResponseDTO> cadastrar(
            @RequestParam("turmaId") Long turmaId,
            @RequestParam(name = "professorId", required = false) Long professorId,
            @RequestParam("titulo") String titulo,
            @RequestParam(name = "descricao", required = false) String descricao,
            @RequestParam("tipoMaterial") TipoMaterial tipoMaterial,
            @RequestParam(name = "statusPublicacao", required = false) StatusPublicacao statusPublicacao,
            @RequestParam("arquivo") MultipartFile arquivo) {
        ConteudoResponseDTO criado = conteudoService.cadastrar(
                turmaId, professorId, titulo, descricao, tipoMaterial, statusPublicacao, arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    /** Edição de título/descrição e, opcionalmente, substituição do arquivo ("arquivo"). */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConteudoResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestParam("titulo") String titulo,
            @RequestParam(name = "descricao", required = false) String descricao,
            @RequestParam(name = "tipoMaterial", required = false) TipoMaterial tipoMaterial,
            @RequestParam(name = "arquivo", required = false) MultipartFile arquivo) {
        return ResponseEntity.ok(conteudoService.atualizar(id, titulo, descricao, tipoMaterial, arquivo));
    }

    /** Publica (PUBLICADO) ou despublica (RASCUNHO) o material. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ConteudoResponseDTO> alterarStatus(
            @PathVariable Long id,
            @Valid @RequestBody ConteudoStatusDTO dto) {
        return ResponseEntity.ok(conteudoService.alterarStatus(id, dto.statusPublicacao()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        conteudoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Leitura autenticada do arquivo (autor ou administrador), com suporte a Range. Não há URL
     * pública: o acesso sempre passa por JWT, RLS e pela regra de gestão do material.
     */
    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Resource> arquivo(@PathVariable Long id) {
        ConteudoService.ArquivoConteudo arquivo = conteudoService.obterArquivo(id);
        Resource recurso = arquivo.recurso();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(arquivo.nomeSugerido()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(recurso);
    }

    /**
     * Visualização protegida: devolve uma URL TEMPORÁRIA assinada (e a expiração) para o usuário
     * que tem acesso ao material (aluno: publicado e da própria turma; docente autor; admin).
     * Registra ABRIU_PDF / INICIOU_VIDEO no servidor, com IP e User-Agent da própria requisição.
     * Sem permissão (ou material inexistente): 404.
     */
    @GetMapping("/{id}/acesso")
    public ResponseEntity<AcessoConteudoResponseDTO> acesso(@PathVariable Long id, HttpServletRequest request) {
        AcessoConteudoResponseDTO acesso = visualizacaoService.abrir(id, ClienteRequisicao.de(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(acesso);
    }

    /**
     * Tentativas detectadas pelo navegador (download, impressão/captura). Lista fechada de ações;
     * abertura e acesso negado são registrados apenas pelo servidor.
     */
    @PostMapping("/{id}/eventos")
    public ResponseEntity<Void> registrarEvento(
            @PathVariable Long id,
            @Valid @RequestBody EventoAcessoRequestDTO dto,
            HttpServletRequest request) {
        visualizacaoService.registrarEvento(id, dto.acao(), dto.detalhe(), ClienteRequisicao.de(request));
        return ResponseEntity.noContent().build();
    }
}
