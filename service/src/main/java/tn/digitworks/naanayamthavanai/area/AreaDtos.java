package tn.digitworks.naanayamthavanai.area;

import jakarta.validation.constraints.Size;

public final class AreaDtos {

    private AreaDtos() {
    }

    public record AreaResponse(Long id, String name, boolean isActive) {
        public static AreaResponse of(Area a) {
            return new AreaResponse(a.getId(), a.getName(), a.isActive());
        }
    }

    /** Create: name required. Patch: any field. */
    public record AreaInput(@Size(min = 1, max = 100) String name, Boolean isActive) {
    }
}
