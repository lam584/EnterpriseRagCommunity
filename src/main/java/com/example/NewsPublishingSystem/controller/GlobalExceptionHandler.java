package com.example.NewsPublishingSystem.controller;

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
        logger.debug("进入 handleValidationExceptions 方法，处理参数验证异常");
        logger.debug("异常详细信息: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            logger.debug("字段: {}, 错误信息: {}", fieldName, errorMessage);
            errors.put(fieldName, errorMessage);
        });

        logger.debug("返回的错误信息: {}", errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({DataAccessException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseExceptions(Exception ex) {
        logger.debug("进入 handleDatabaseExceptions 方法，处理��据库异常");
        logger.error("数据库操作异常详细��息: ", ex);

        Map<String, String> response = new HashMap<>();
        if (ex.getMessage().contains("administrator_id")) {
            logger.debug("检测到管理员信息缺失错误");
            response.put("message", "添加图书失败：管理员信息缺失");
        } else if (ex.getMessage().contains("unique constraint") || ex.getMessage().contains("Duplicate entry")) {
            logger.debug("检测到数据唯一性约束错误");
            response.put("message", "操作失败：数据已存在");
        } else {
            logger.debug("检测到其他数据库操作错误");
            response.put("message", "数据操作失败，请联系系统管理员");
        }

        logger.debug("返回的错误信息: {}", response);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOtherExceptions(Exception ex) {
        logger.debug("进入 handleOtherExceptions 方法，处理系统异常");
        logger.error("系统异常详细信息: ", ex);

        Map<String, String> response = new HashMap<>();
        response.put("message", "系统处理请求时发生错误");

        logger.debug("返回的错误信息: {}", response);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}