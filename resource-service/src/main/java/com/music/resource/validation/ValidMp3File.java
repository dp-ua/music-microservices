package com.music.resource.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = Mp3FileValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMp3File {

    String message() default "Invalid MP3 file. File must not be empty, must have .mp3 extension and valid MP3 format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
