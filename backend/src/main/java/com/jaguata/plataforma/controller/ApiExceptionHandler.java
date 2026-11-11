package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Erros padronizados em RFC 7807 (ProblemDetail). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    ProblemDetail regraNegocio(RegraNegocioException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(com.jaguata.plataforma.exception.CredenciaisInvalidasException.class)
    ProblemDetail credenciais(com.jaguata.plataforma.exception.CredenciaisInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        pd.setProperty("campos", campos);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail parametros(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido (JSON malformado ou valor de enum fora do cardápio).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Violação de integridade dos dados.");
    }
}
