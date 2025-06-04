package com.example.FinalAssignments.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({DataAccessException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseExceptions(Exception ex) {
        // 记录详细错误信息到日志
        logger.error("数据库操作异常", ex);

        Map<String, String> response = new HashMap<>();
        // 返回友好的错误消息，不暴露数据库信息
        if (ex.getMessage().contains("administrator_id")) {
            response.put("message", "添加图书失败：管理员信息缺失");
        } else if (ex.getMessage().contains("unique constraint") || ex.getMessage().contains("Duplicate entry")) {
            response.put("message", "操作失败：数据已存在");
        } else {
            response.put("message", "数据操作失败，请联系系统管理员");
        }
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOtherExceptions(Exception ex) {
        // 记录详细错误到日志
        logger.error("系统异常", ex);

        Map<String, String> response = new HashMap<>();
        // 返回通用错误信息，避免暴露��统细节
        response.put("message", "系统处理请求时发生错误");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
