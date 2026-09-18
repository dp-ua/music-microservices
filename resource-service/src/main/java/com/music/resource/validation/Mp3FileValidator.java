package com.music.resource.validation;

import static com.music.resource.exception.ExceptionConstants.FILE_CANNOT_BE_NULL_OR_EMPTY;
import static com.music.resource.exception.ExceptionConstants.FILE_MUST_HAVE_MP_3_EXTENSION;
import static com.music.resource.utils.Mp3ProcessingConstants.MP3_EXTENSION;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Mp3FileValidator implements ConstraintValidator<ValidMp3File, MultipartFile> {

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            setCustomMessage(context, FILE_CANNOT_BE_NULL_OR_EMPTY);
            return false;
        }

        var originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(MP3_EXTENSION)) {
            setCustomMessage(context, FILE_MUST_HAVE_MP_3_EXTENSION);
            return false;
        }
        return true;
    }

    private void setCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

}
