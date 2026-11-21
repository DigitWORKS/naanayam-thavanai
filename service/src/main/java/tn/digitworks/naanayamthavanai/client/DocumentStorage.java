package tn.digitworks.naanayamthavanai.client;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Where client documents live. Local disk now; swap for S3 (encrypted, private) in production. */
public interface DocumentStorage {

    /** Stores the file and returns its storage key. */
    String store(long tenantId, long clientId, MultipartFile file);

    Resource load(String key);

    /** Removes every file of a tenant (account erasure). Missing files are not an error. */
    void deleteTenant(long tenantId);
}
