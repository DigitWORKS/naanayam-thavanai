package tn.digitworks.naanayamthavanai.security;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.user.UserAreaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Which areas the caller may see. Owners see every area of their tenant; agents only their
 * assigned areas.
 */
@Component
public class AreaScope {

    /** Placeholder for JPQL "in" lists, which must not be empty. */
    private static final List<Long> NONE = List.of(-1L);

    private final UserAreaRepository userAreas;

    public AreaScope(UserAreaRepository userAreas) {
        this.userAreas = userAreas;
    }

    /**
     * @param all     true when not restricted
     * @param areaIds the allowed areas when restricted (never empty, safe for JPQL "in")
     */
    public record Scope(boolean all, List<Long> areaIds) {

        public boolean allows(Long areaId) {
            return all || areaIds.contains(areaId);
        }
    }

    public Scope current() {
        CurrentUser user = CurrentUser.get();
        if (!user.isAgent()) {
            return new Scope(true, NONE);
        }
        List<Long> ids = userAreas.findAreaIdsByUserId(user.userId());
        return new Scope(false, ids.isEmpty() ? NONE : ids);
    }

    /** Fails with 404 (not 403) so agents cannot probe other areas' records. */
    public void require(Long areaId, String what) {
        if (!current().allows(areaId)) {
            throw ApiException.notFound(what);
        }
    }

    public Set<Long> agentAreas(long userId) {
        return Set.copyOf(userAreas.findAreaIdsByUserId(userId));
    }
}
