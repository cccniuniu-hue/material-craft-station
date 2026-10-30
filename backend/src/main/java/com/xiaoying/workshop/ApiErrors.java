package com.xiaoying.workshop;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String,String>> business(ApiException e) { return response(e.status(), e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> tooLarge() { return response(413, "单张图片不能超过 10 MB"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, MissingServletRequestPartException.class})
    ResponseEntity<Map<String,String>> invalid(Exception e) { return response(400, "请求参数不合法，请检查图片数量、文件和模拟设置"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected(Exception e) {
        log.error("Request failed", e);
        return response(500, "服务暂时不可用，请稍后重试");
    }
    private ResponseEntity<Map<String,String>> response(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
