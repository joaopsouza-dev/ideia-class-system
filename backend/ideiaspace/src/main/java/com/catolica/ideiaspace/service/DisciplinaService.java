package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.Disciplina;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaRequestDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaResponseDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaUpdateDTO;
import com.catolica.ideiaspace.repositories.DisciplinaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;

    public DisciplinaService(DisciplinaRepository disciplinaRepository) {
        this.disciplinaRepository = disciplinaRepository;
    }

    @Transactional
    public DisciplinaResponseDTO cadastrar(DisciplinaRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados da disciplina são obrigatórios.");
        }

        validarCampos(dto.codigo(), dto.nome());

        String codigoFormatado = dto.codigo().trim();
        if (disciplinaRepository.existsByCodigoAtivo(codigoFormatado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma disciplina ativa cadastrada com o código: " + codigoFormatado);
        }

        Disciplina disciplina = new Disciplina();
        disciplina.setCodigo(codigoFormatado);
        disciplina.setNome(dto.nome().trim());
        disciplina.setDescricao(dto.descricao() != null ? dto.descricao().trim() : null);

        LocalDateTime agora = LocalDateTime.now();
        disciplina.setCriadoEm(agora);
        disciplina.setAtualizadoEm(agora);
        disciplina.setExcluidoEm(null);

        Disciplina salva = disciplinaRepository.save(disciplina);
        return DisciplinaResponseDTO.fromEntity(salva);
    }

    @Transactional(readOnly = true)
    public List<DisciplinaResponseDTO> listar() {
        return disciplinaRepository.findByExcluidoEmIsNull()
                .stream()
                .map(DisciplinaResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DisciplinaResponseDTO buscarPorId(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da disciplina é obrigatório.");
        }

        Disciplina disciplina = disciplinaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina não encontrada com ID: " + id));

        return DisciplinaResponseDTO.fromEntity(disciplina);
    }

    @Transactional
    public DisciplinaResponseDTO atualizar(Long id, DisciplinaUpdateDTO dto) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da disciplina é obrigatório.");
        }
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados para atualização são obrigatórios.");
        }

        validarCampos(dto.codigo(), dto.nome());

        Disciplina disciplina = disciplinaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina não encontrada com ID: " + id));

        String codigoFormatado = dto.codigo().trim();
        if (disciplinaRepository.existsByCodigoAtivoAndIdNot(codigoFormatado, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe outra disciplina ativa cadastrada com o código: " + codigoFormatado);
        }

        disciplina.setCodigo(codigoFormatado);
        disciplina.setNome(dto.nome().trim());
        disciplina.setDescricao(dto.descricao() != null ? dto.descricao().trim() : null);
        disciplina.setAtualizadoEm(LocalDateTime.now());

        Disciplina atualizada = disciplinaRepository.save(disciplina);
        return DisciplinaResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void excluir(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ID da disciplina é obrigatório.");
        }

        Disciplina disciplina = disciplinaRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina não encontrada com ID: " + id));

        if (disciplinaRepository.hasTurmasAtivas(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível excluir a disciplina pois existem turmas ativas vinculadas a ela.");
        }

        LocalDateTime agora = LocalDateTime.now();
        disciplina.setExcluidoEm(agora);
        disciplina.setAtualizadoEm(agora);

        disciplinaRepository.save(disciplina);
    }

    private void validarCampos(String codigo, String nome) {
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código da disciplina é obrigatório.");
        }
        if (codigo.trim().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código da disciplina deve ter no máximo 50 caracteres.");
        }
        if (nome == null || nome.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome da disciplina deve ter no mínimo 2 caracteres.");
        }
        if (nome.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome da disciplina deve ter no máximo 255 caracteres.");
        }
    }
}
