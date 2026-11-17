package com.catolica.ideiaspace.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Origem da requisição HTTP, determinada pelo SERVIDOR para a auditoria. Nada aqui vem de
 * parâmetros ou corpo enviados pelo cliente.
 *
 * O IP é o endereço da conexão (getRemoteAddr). Atrás de proxy reverso, configure
 * server.forward-headers-strategy=native e server.tomcat.remoteip.internal-proxies para que o
 * servidor só aceite X-Forwarded-For de proxies confiáveis; caso contrário um cliente
 * conseguiria forjar o próprio IP no log.
 */
public record ClienteRequisicao(String ip, String userAgent) {

    private static final int USER_AGENT_MAX = 1000;

    public static ClienteRequisicao de(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            ip = "0.0.0.0";
        }
        int zona = ip.indexOf('%'); // IPv6 com zona (fe80::1%eth0) não é aceito pelo tipo INET
        if (zona >= 0) {
            ip = ip.substring(0, zona);
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null) {
            ua = ua.replaceAll("\\p{Cntrl}", " ").trim();
            if (ua.length() > USER_AGENT_MAX) {
                ua = ua.substring(0, USER_AGENT_MAX);
            }
            if (ua.isEmpty()) {
                ua = null;
            }
        }
        return new ClienteRequisicao(ip, ua);
    }
}
