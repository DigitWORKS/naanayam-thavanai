package tn.digitworks.naanayamthavanai.area;

import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaInput;
import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/areas")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class AreaController {

    private final AreaService service;

    public AreaController(AreaService service) {
        this.service = service;
    }

    @GetMapping
    public List<AreaResponse> list(@RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public AreaResponse create(@Valid @RequestBody AreaInput in) {
        return service.create(in);
    }

    @PatchMapping("/{areaId}")
    @PreAuthorize("hasRole('OWNER')")
    public AreaResponse update(@PathVariable long areaId, @Valid @RequestBody AreaInput in) {
        return service.update(areaId, in);
    }
}
