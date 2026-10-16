package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.verify.QueryForgeVerifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryForgeVerifierTest {

    @Test
    void reportsExactlyTheUnsupportedLambdasOfTheTestSuite() throws Exception {
        Path testClasses = Path.of(QueryForgeVerifierTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> problems = QueryForgeVerifier.verify(testClasses, getClass().getClassLoader());

        assertEquals(4, problems.size(), String.join("\n", problems));
        assertEquals(2, problems.stream().filter(p -> p.contains("java.lang.String.hashCode")).count(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.contains("ProjectionAndAggregateTest") && p.contains("does not read any field")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("ProjectionAndAggregateTest") && p.contains("model itself")));
    }
}
