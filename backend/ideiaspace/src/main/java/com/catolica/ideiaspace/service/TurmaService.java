package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.Disciplina;
import com.catolica.ideiaspace.domain.Turma;
import com.catolica.ideiaspace.domain.TurmaUsuario;
import com.catolica.ideiaspace.domain.Usuario;
import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.domain.enums.StatusConta;
import com.catolica.ideiaspace.domain.enums.StatusMatricula;
import com.catolica.ideiaspace.domain.enums.StatusTurma;
import com.catolica.ideiaspace.dto.turma.*;
import com.catolica.ideiaspace.repositories.DisciplinaRepository;
import com.catolica.ideiaspace.repositories.TurmaRepository;
import com.catolica.ideiaspace.repositories.TurmaUsuarioRepository;
import com.catolica.ideiaspace.repositories.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TurmaUsuarioRepository turmaUsuarioRepository;
    private final UsuarioRepository usuarioRepository;

    public TurmaService(
            TurmaRepository turmaRepository,
            DisciplinaRepository disciplinaRepository,
            TurmaUsuarioRepository turmaUsuarioRepository,
            UsuarioRepository usuarioRepository) {
        this.turmaRepository = turmaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.turmaUsuarioRepository = turmaUsuarioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public TurmaResponseDTO cadastrar(TurmaRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados da turma são obrigatórios.");
        }

        validarCamposTurma(dto.disciplinaId(), dto.codigo(), dto.nome(), dto.ano(), dto.semestre());

        Disciplina disciplina = disciplinaRepository.findByIdAndExcluidoEmIsNull(dto.disciplinaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina ativa não encontrada com ID: " + dto.disciplinaId()));

        String codigoFormatado = dto.codigo().trim();
        if (turmaRepository.existsByCodigoAtivo(codigoFormatado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma turma ativa cadastrada com o código: " + codigoFormatado);
        }

        Turma turma = new Turma();
        turma.setDisciplina(disciplina);
        turma.setCodigo(codigoFormatado);
        turma.setNome(dto.nome().trim());
        turma.setAno(dto.ano());
        turma.setSemestre(dto.semestre());
        turma.setStatus(dto.status() != null ? dto.status() : StatusTurma.ATIVA);

        LocalDateTime agora = LocalDateTime.now();
        turma.setCriadoEm(agora);
        turma.setAtualizadoEm(agora);
        turma.setExcluidoEm(null);

        Turma salva = turmaRepository.save(turma);
        return TurmaResponseDTO.fromEntity(salva);
    }

    @Transactional(readOnly = true)
    public List<TurmaResponseDTO> listar() {
        return turmaRepository.findByExcluidoEmIsNull()
                .stream()
                .map(TurmaResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TurmaResponseDTO buscarPorId(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da turma é obrigatório.");
        }

        Turma turma = turmaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma não encontrada com ID: " + id));

        return TurmaResponseDTO.fromEntity(turma);
    }

    @Transactional
    public TurmaResponseDTO atualizar(Long id, TurmaUpdateDTO dto) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da turma é obrigatório.");
        }
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados para atualização são obrigatórios.");
        }

        validarCamposTurma(dto.disciplinaId(), dto.codigo(), dto.nome(), dto.ano(), dto.semestre());

        Turma turma = turmaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma não encontrada com ID: " + id));

        if (!turma.getDisciplina().getId().equals(dto.disciplinaId())) {
            Disciplina novaDisciplina = disciplinaRepository.findByIdAndExcluidoEmIsNull(dto.disciplinaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina ativa não encontrada com ID: " + dto.disciplinaId()));
            turma.setDisciplina(novaDisciplina);
        }

        String codigoFormatado = dto.codigo().trim();
        if (turmaRepository.existsByCodigoAtivoAndIdNot(codigoFormatado, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe outra turma ativa cadastrada com o código: " + codigoFormatado);
        }

        turma.setCodigo(codigoFormatado);
        turma.setNome(dto.nome().trim());
        turma.setAno(dto.ano());
        turma.setSemestre(dto.semestre());
        if (dto.status() != null) {
            turma.setStatus(dto.status());
        }
        turma.setAtualizadoEm(LocalDateTime.now());

        Turma atualizada = turmaRepository.save(turma);
        return TurmaResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void excluir(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da turma é obrigatório.");
        }

        Turma turma = turmaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma não encontrada com ID: " + id));

        if (turmaRepository.hasConteudosAtivos(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível excluir a turma pois existem materiais ativos vinculados a ela.");
        }

        if (turmaRepository.hasUsuariosAtivos(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível excluir a turma pois existem participantes ativos vinculados a ela.");
        }

        LocalDateTime agora = LocalDateTime.now();
        turma.setExcluidoEm(agora);
        turma.setStatus(StatusTurma.INATIVA);
        turma.setAtualizadoEm(agora);

        turmaRepository.save(turma);
    }

    @Transactional
    public TurmaUsuarioResponseDTO vincularUsuario(Long turmaId, TurmaUsuarioRequestDTO dto) {
        if (turmaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da turma é obrigatório.");
        }
        if (dto == null || dto.usuarioId() == null || dto.papel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O usuário e o papel na turma são obrigatórios.");
        }

        Turma turma = turmaRepository.findByIdAndExcluidoEmIsNull(turmaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma ativa não encontrada com ID: " + turmaId));

        if (turma.getStatus() != StatusTurma.ATIVA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A turma não está ativa para novos vínculos.");
        }

        Usuario usuario = usuarioRepository.findByIdAndExcluidoEmIsNull(dto.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário ativo não encontrado com ID: " + dto.usuarioId()));

        if (usuario.getStatusConta() != StatusConta.ATIVA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O usuário informado não possui conta ativa.");
        }

        if (usuario.getPerfil() == PerfilUsuario.ALUNO && dto.papel() == PapelTurma.DOCENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuário com perfil de ALUNO não pode ser vinculado como DOCENTE.");
        }

        LocalDate inicio = dto.inicioEm() != null ? dto.inicioEm() : LocalDate.now();
        LocalDate fim = dto.fimEm();

        if (fim != null && fim.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de término não pode ser anterior à data de início.");
        }

        if (turmaUsuarioRepository.existsAtivoByTurmaIdAndUsuarioId(turmaId, dto.usuarioId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O usuário já possui vínculo ativo nesta turma.");
        }

        TurmaUsuario vinculo = new TurmaUsuario();
        vinculo.setTurma(turma);
        vinculo.setUsuario(usuario);
        vinculo.setPapel(dto.papel());
        vinculo.setStatus(StatusMatricula.ATIVA);
        vinculo.setInicioEm(inicio);
        vinculo.setFimEm(fim);

        LocalDateTime agora = LocalDateTime.now();
        vinculo.setCriadoEm(agora);
        vinculo.setAtualizadoEm(agora);
        vinculo.setExcluidoEm(null);

        TurmaUsuario salvo = turmaUsuarioRepository.save(vinculo);
        return TurmaUsuarioResponseDTO.fromEntity(salvo);
    }

    @Transactional(readOnly = true)
    public List<TurmaUsuarioResponseDTO> listarParticipantes(Long turmaId) {
        if (turmaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da turma é obrigatório.");
        }

        if (!turmaRepository.existsById(turmaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Turma não encontrada com ID: " + turmaId);
        }

        return turmaUsuarioRepository.findByTurmaIdAndExcluidoEmIsNull(turmaId)
                .stream()
                .map(TurmaUsuarioResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public void removerVinculoUsuario(Long turmaId, Long vinculoId) {
        if (turmaId == null || vinculoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os IDs da turma e do vínculo são obrigatórios.");
        }

        TurmaUsuario vinculo = turmaUsuarioRepository.findByIdAndExcluidoEmIsNull(vinculoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vínculo não encontrado com ID: " + vinculoId));

        if (!vinculo.getTurma().getId().equals(turmaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O vínculo informado não pertence à turma indicada.");
        }

        LocalDateTime agora = LocalDateTime.now();
        vinculo.setExcluidoEm(agora);
        vinculo.setStatus(StatusMatricula.CANCELADA);
        vinculo.setAtualizadoEm(agora);

        turmaUsuarioRepository.save(vinculo);
    }

    private void validarCamposTurma(Long disciplinaId, String codigo, String nome, Integer ano, Integer semestre) {
        if (disciplinaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A disciplina vinculada é obrigatória.");
        }
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código da turma é obrigatório.");
        }
        if (codigo.trim().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código da turma deve ter no máximo 50 caracteres.");
        }
        if (nome == null || nome.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome da turma deve ter no mínimo 2 caracteres.");
        }
        if (nome.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome da turma deve ter no máximo 255 caracteres.");
        }
        if (ano == null || ano < 2000 || ano > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ano da turma deve estar entre 2000 e 2100.");
        }
        if (semestre == null || (semestre != 1 && semestre != 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O semestre da turma deve ser 1 ou 2.");
        }
    }
}
