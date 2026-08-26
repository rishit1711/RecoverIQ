package com.recoveriq.api;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.bind.MethodArgumentNotValidException; import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR","Request validation failed"));}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ApiError> missing(NoSuchElementException e){return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND","Requested recovery record was not found"));}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<ApiError> invalid(RuntimeException e){return ResponseEntity.unprocessableEntity().body(ApiError.of("RECOVERY_NOT_ALLOWED",e.getMessage()));}
 @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected(Exception e){return ResponseEntity.status(500).body(ApiError.of("INTERNAL_ERROR","Unable to process recovery request"));}
}
