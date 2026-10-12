package com.catolica.ideiaspace.model;

import com.catolica.ideiaspace.model.enums.TipoMaterial;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conteudo")
public class Conteudo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @ManyToOne
    @JoinColumn(name = "professor_id")
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

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @OneToMany(mappedBy = "conteudo")
    private List<LogAcesso> logsAcesso = new ArrayList<>();
}