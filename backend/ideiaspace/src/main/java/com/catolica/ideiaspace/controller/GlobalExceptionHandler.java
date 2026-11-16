package com.catolica.ideiaspace.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        return buildResponse(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado: permissões insuficientes.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Não autorizado: falha de autenticação.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex) {
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        log.warn("DataAccessException capturada: {}", rootCause.getMessage());

        if (rootCause instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            String message = sqlEx.getMessage();

            if ("42501".equals(sqlState) || (message != null && message.contains("row-level security policy"))) {
                return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado pelas políticas de segurança do banco de dados.");
            }
            if ("23505".equals(sqlState)) {
                if (contem(message, "idx_usuario_email_ativo")) {
                    return buildResponse(HttpStatus.CONFLICT, "E-mail já cadastrado.");
                }
                if (contem(message, "idx_usuario_cpf_lookup_hmac_ativo")) {
                    return buildResponse(HttpStatus.CONFLICT, "CPF já cadastrado.");
                }
                return buildResponse(HttpStatus.CONFLICT, "Conflito: registro já existente com os mesmos dados únicos.");
            }
            if ("23503".equals(sqlState)) {
                return buildResponse(HttpStatus.CONFLICT, "Operação não permitida devido a vínculos existentes com outros registros.");
            }
            if ("23502".equals(sqlState)) {
                return buildResponse(HttpStatus.BAD_REQUEST, "Campo obrigatório não informado.");
            }
            if ("23514".equals(sqlState)) {
                if (contem(message, "ck_usuario_cpf_aluno")) {
                    return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Conta de aluno exige CPF cadastrado.");
                }
                if (contem(message, "ck_usuario_email_minimo")) {
                    return buildResponse(HttpStatus.BAD_REQUEST, "E-mail inválido.");
                }
                if (contem(message, "ck_usuario_nome_nao_vazio")) {
                    return buildResponse(HttpStatus.BAD_REQUEST, "Nome deve ter ao menos 3 caracteres.");
                }
                return buildResponse(HttpStatus.BAD_REQUEST, "Dados inválidos: violação de regra de integridade do banco.");
            }
        }

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao processar a operação no banco de dados.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Dados da requisição inválidos.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou malformatado.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.valueOf(413),
                "O arquivo enviado excede o tamanho máximo permitido.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parte obrigatória ausente na requisição: " + ex.getRequestPartName() + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName() + ".");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro: " + ex.getName() + ".");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Requisição multipart inválida ou malformatada.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo da requisição não suportado.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Exceção não tratada capturada", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor.");
    }

    private static boolean contem(String mensagem, String trecho) {
        return mensagem != null && mensagem.contains(trecho);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
