package tn.digitworks.naanayamthavanai.user;

import tn.digitworks.naanayamthavanai.user.UserDtos.AreaAssignment;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserCreate;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserResponse;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserUpdate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('OWNER')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return service.list(role);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreate in) {
        return service.createAgent(in);
    }

    @PatchMapping("/{userId}")
    public UserResponse update(@PathVariable long userId, @Valid @RequestBody UserUpdate in) {
        return service.update(userId, in);
    }

    @PutMapping("/{userId}/areas")
    public UserResponse replaceAreas(@PathVariable long userId, @Valid @RequestBody AreaAssignment in) {
        return service.replaceAreas(userId, in.areaIds());
    }
}
