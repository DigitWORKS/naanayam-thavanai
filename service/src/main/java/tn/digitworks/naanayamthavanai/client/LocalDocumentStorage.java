package tn.digitworks.naanayamthavanai.client;

import tn.digitworks.naanayamthavanai.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Stores files under app.storage.local-dir/{tenantId}/{clientId}/{uuid}.{ext}. */
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private final Path root;

    public LocalDocumentStorage(@Value("${app.storage.local-dir:./data/documents}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(long tenantId, long clientId, MultipartFile file) {
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) {
            throw ApiException.rule("UNSUPPORTED_FILE_TYPE", "Only JPG, PNG, WEBP or PDF files are allowed");
        }
        if (file.isEmpty()) {
            throw ApiException.rule("EMPTY_FILE", "File is empty");
        }
        String key = tenantId + "/" + clientId + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store document", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("Document file");
        }
        return new FileSystemResource(path);
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.notFound("Document file");
        }
        return p;
    }
}
