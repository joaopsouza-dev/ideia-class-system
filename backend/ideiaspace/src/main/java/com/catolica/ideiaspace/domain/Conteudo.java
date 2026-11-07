package com.catolica.ideiaspace.domain;

import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * Colunas imutáveis pela aplicação (updatable = false): o role app_backend possui
 * UPDATE apenas em titulo, descricao, tipo_material, storage_path, mime_type,
 * tamanho_bytes e sha256 (V1__firstversion.sql). Criação e arquivamento passam
 * pelas funções fn_criar_conteudo e fn_arquivar_conteudo.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "conteudo")
public class Conteudo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "turma_id", updatable = false)
    private Turma turma;

    @ManyToOne
    @JoinColumn(name = "professor_id", updatable = false)
    private Usuario professor;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "descricao")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_material")
    private TipoMaterial tipoMaterial;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", updatable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "excluido_em", updatable = false)
    private LocalDateTime excluidoEm;

    @OneToMany(mappedBy = "conteudo")
    private List<LogAcesso> logsAcesso = new ArrayList<>();
}
