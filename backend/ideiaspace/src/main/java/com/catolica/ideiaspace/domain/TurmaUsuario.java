package com.catolica.ideiaspace.domain;

import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.StatusMatricula;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "turma_usuario")
public class TurmaUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.type.PostgreSQLEnumJdbcType.class)
    @Column(name = "papel")
    private PapelTurma papel;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.type.PostgreSQLEnumJdbcType.class)
    @Column(name = "status")
    private StatusMatricula status;

    @Column(name = "inicio_em")
    private LocalDate inicioEm;

    @Column(name = "fim_em")
    private LocalDate fimEm;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;
}