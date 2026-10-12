package com.catolica.ideiaspace.model;

import com.catolica.ideiaspace.model.enums.StatusTurma;
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
@Table(name = "turma")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "nome")
    private String nome;

    @Column(name = "ano")
    private Integer ano;

    @Column(name = "semestre")
    private Integer semestre;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusTurma status;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @OneToMany(mappedBy = "turma")
    private List<TurmaUsuario> turmaUsuarios = new ArrayList<>();

    @OneToMany(mappedBy = "turma")
    private List<Conteudo> conteudos = new ArrayList<>();
}