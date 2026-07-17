package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.model.RetObj;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。成功走 controller 的 RetObj.success;错误在此统一转
 * ResponseEntity(HTTP status == RetObj.code)。未知异常只记服务端日志,不对外泄漏。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<RetObj<?>> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getStatus().getCode(), e.getMessage());
        return build(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<RetObj<?>> handleValidation(BindException e) {
        StringBuilder sb = new StringBuilder();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            sb.append(fe.getDefaultMessage()).append("; ");
        }
        String msg = !sb.isEmpty() ? sb.toString().trim() : SystemStatus.BAD_REQUEST.getErrorMessage();
        log.warn("参数校验异常: {}", msg);
        return build(SystemStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RetObj<?>> handleConstraint(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        return build(SystemStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<RetObj<?>> handleBadInput(Exception e) {
        log.warn("请求解析异常: {}", e.getMessage());
        return build(SystemStatus.BAD_REQUEST, SystemStatus.BAD_REQUEST.getErrorMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RetObj<?>> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return build(SystemStatus.METHOD_NOT_ALLOWED, SystemStatus.METHOD_NOT_ALLOWED.getErrorMessage());
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<RetObj<?>> handleConflict(Exception e) {
        log.warn("数据冲突: {}", e.getMessage());
        return build(SystemStatus.CONFLICT, SystemStatus.CONFLICT.getErrorMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RetObj<?>> handleException(Exception e) {
        log.error("系统内部异常: ", e);
        return build(SystemStatus.INTERNAL_ERROR, SystemStatus.INTERNAL_ERROR.getErrorMessage());
    }

    private ResponseEntity<RetObj<?>> build(SystemStatus status, String message) {
        return ResponseEntity.status(status.getCode()).body(RetObj.error(status, message));
    }
}
