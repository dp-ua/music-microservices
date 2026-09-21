package com.music.song.validator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.music.song.exception.InvalidIdException;

@Service
public class CSVValidator {

    private static final int MAX_CSV_LENGTH = 200;
    private static final String COMMA = ",";

    public List<Long> parseAndValidateIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            throw InvalidIdException.empty();
        }

        if (ids.length() > MAX_CSV_LENGTH) {
            throw InvalidIdException.csvTooLong(ids.length(), MAX_CSV_LENGTH);
        }

        var parts = StringUtils.split(ids, COMMA);
        var result = new ArrayList<Long>(parts.length);

        for (String part : parts) {
            var trimmed = part.trim();
            long value;
            try {
                value = Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw InvalidIdException.invalidFormat(trimmed);
            }
            if (value <= 0) {
                throw InvalidIdException.invalidFormat(trimmed);
            }
            result.add(value);
        }

        return result;
    }

}
