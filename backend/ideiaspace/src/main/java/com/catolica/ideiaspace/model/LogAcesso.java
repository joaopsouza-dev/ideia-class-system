package com.catolica.ideiaspace.model;

import com.catolica.ideiaspace.model.enums.AcaoLog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "log_acesso")
@IdClass(LogAcessoId.class)
public class LogAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "conteudo_id")
    private Conteudo conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao")
    private AcaoLog acao;

    @Column(name = "ip_origem")
    private String ipOrigem;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_id")
    private UUID requestId;

    // O mapeamento de detalhes (jsonb) será configurado separadamente.
}