package io.github.apertechprojects.queryforge.internal.util;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.exception.QueryForgeNoFirstValueException;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class SpecificationUtil {

    public static String toLowerCaseFirstLetter(String string) {
        if (string.length() <= 1) {
            throw new RuntimeException("String does not have the required length");
        }
        return Character.toLowerCase(string.charAt(0)) + string.substring(1);
    }

    public static void checkHasFirstValue(Criteria criteria) {
        if (criteria.getValues().size() == 0) {
            throw new QueryForgeNoFirstValueException("There is No Value in Criteria Key: " + criteria.getKey());
        }
    }

    public static void checkHasTwoValue(Criteria criteria) {
        if (criteria.getValues().size() < 2) {
            throw new QueryForgeNoFirstValueException("There is No Value in Criteria Key: " + criteria.getKey());
        }
    }

    public static String getStringFromResourceFile(String path) throws IOException {
        File resource = new ClassPathResource(path).getFile();
        return new String(Files.readAllBytes(resource.toPath()), StandardCharsets.UTF_8);
    }

    public static void convertObjectToCriteriaValue(Object value, Criteria criteriaDTO) {
        if (value.getClass().isArray()) {
            criteriaDTO.setValues(Arrays.asList((Object[]) value));
        } else if (value instanceof List) {
            criteriaDTO.setValues((List) value);
        } else {
            Object[] array = new Object[1];
            array[0] = value;
            criteriaDTO.setValues(Arrays.asList(array));
        }
    }
}
