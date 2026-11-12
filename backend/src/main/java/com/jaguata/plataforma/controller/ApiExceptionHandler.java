package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.CredenciaisInvalidasException;
import com.jaguata.plataforma.exception.EntradaInvalidaException;
import com.jaguata.plataforma.exception.ServicoExternoException;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.exception.TentativasExcedidasException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erros padronizados em RFC 7807 (ProblemDetail). Estende ResponseEntityExceptionHandler para que as
 * exceções do Spring MVC (415, 405, 404 de rota...) mantenham o status correto, e nunca vaza detalhes
 * internos: qualquer erro inesperado vira 500 genérico (o detalhe vai só para o log).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    ProblemDetail regraNegocio(RegraNegocioException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ConsultaNaoEncontradaException.class)
    ProblemDetail consultaNaoEncontrada(ConsultaNaoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EntradaInvalidaException.class)
    ProblemDetail entradaInvalida(EntradaInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Receita/CEP fora do ar ou lentos: 503, com mensagem genérica (o detalhe técnico fica no log). */
    @ExceptionHandler(ServicoExternoException.class)
    ResponseEntity<ProblemDetail> servicoExterno(ServicoExternoException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ProblemDetail credenciais(CredenciaisInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    ResponseEntity<ProblemDetail> tentativas(TentativasExcedidasException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    // Exceções do Spring Security lançadas dentro de controllers (@PreAuthorize) precisam manter 401/403.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail acessoNegado(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail naoAutenticado(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Não autenticado.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail parametros(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetros inválidos.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException e) {
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Violação de integridade dos dados.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        pd.setProperty("campos", campos);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido (JSON malformado ou valor de enum fora do cardápio)."));
    }

    /** Rede de segurança: nunca devolve stack trace, SQL ou nomes de classes ao cliente. */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente.");
    }
}
