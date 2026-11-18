package ru.mininms.emulator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.mininms.emulator.common.DeviceNotFoundException;
import ru.mininms.emulator.common.DeviceSilentException;
import ru.mininms.emulator.common.ScenarioValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Централизованная обработка ошибок в формате RFC 7807 (ProblemDetail) —
 * единый стиль с nms-backend (критерий №12 заявки).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail deviceNotFound(DeviceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Устройство не найдено");
        return problem;
    }

    @ExceptionHandler(DeviceSilentException.class)
    public ProblemDetail deviceSilent(DeviceSilentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Устройство недоступно");
        return problem;
    }

    @ExceptionHandler(ScenarioValidationException.class)
    public ProblemDetail scenarioInvalid(ScenarioValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Некорректный сценарий");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail bodyInvalid(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Некорректное тело запроса");
        problem.setTitle("Ошибка валидации");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }
}