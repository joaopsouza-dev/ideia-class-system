package com.catolica.ideiaspace.dto.conteudo;

import com.catolica.ideiaspace.domain.enums.AcaoLog;

/**
 * Únicas ações que o navegador pode reportar. Abertura de material, geração de URL e acesso
 * negado são registrados exclusivamente pelo servidor, para que o cliente não consiga forjá-los.
 */
public enum AcaoEventoCliente {
    TENTATIVA_DOWNLOAD(AcaoLog.TENTATIVA_DOWNLOAD),
    TENTATIVA_PRINT_DETECTADA(AcaoLog.TENTATIVA_PRINT_DETECTADA);

    private final AcaoLog acaoLog;

    AcaoEventoCliente(AcaoLog acaoLog) {
        this.acaoLog = acaoLog;
    }

    public AcaoLog acaoLog() {
        return acaoLog;
    }
}
