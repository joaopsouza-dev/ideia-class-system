package com.catolica.ideiaspace.domain;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.domain.enums.StatusConta;
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
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo")
    private String nomeCompleto;

    @Column(name = "email")
    private String email;

    @Transient
    private byte[] cpfCifrado;

    @Transient
    private byte[] cpfLookupHmac;

    @Transient
    private String hashSenha;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.type.PostgreSQLEnumJdbcType.class)
    @Column(name = "perfil")
    private PerfilUsuario perfil;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.type.PostgreSQLEnumJdbcType.class)
    @Column(name = "status_conta")
    private StatusConta statusConta;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @OneToMany(mappedBy = "usuario")
    private List<TurmaUsuario> turmas = new ArrayList<>();

    @OneToMany(mappedBy = "professor")
    private List<Conteudo> conteudos = new ArrayList<>();

    @OneToMany(mappedBy = "usuario")
    private List<LogAcesso> logsAcesso = new ArrayList<>();
}