package tn.digitworks.naanayamthavanai.tenant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A new table added by a migration must also be erased when a business is deleted. */
class TenantErasureTablesTest {

    private static final Pattern CREATE = Pattern.compile("CREATE TABLE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET = Pattern.compile("(?:DELETE FROM|UPDATE)\\s+(\\w+)");

    @Test
    void everyMigrationTableIsErased() throws IOException {
        Path dir = Stream.of(Path.of("../db/migrations"), Path.of("db/migrations"))
                .filter(Files::isDirectory).findFirst().orElseThrow();
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher m = CREATE.matcher(Files.readString(f));
                while (m.find()) {
                    tables.add(m.group(1).toLowerCase());
                }
            }
        }
        Set<String> erased = new TreeSet<>();
        for (String sql : TenantErasureService.STATEMENTS) {
            if (sql.startsWith("DELETE")) {
                Matcher m = TARGET.matcher(sql);
                assertTrue(m.find());
                erased.add(m.group(1));
            }
        }
        assertEquals(tables, erased);
        assertTrue(TenantErasureService.STATEMENTS.getLast().startsWith("DELETE FROM tenant "));
    }
}
