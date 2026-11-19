package tn.digitworks.naanayamthavanai.user;

import tn.digitworks.naanayamthavanai.area.Area;
import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaResponse;
import tn.digitworks.naanayamthavanai.area.AreaRepository;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserCreate;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserResponse;
import tn.digitworks.naanayamthavanai.user.UserDtos.UserUpdate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final AppUserRepository users;
    private final UserAreaRepository userAreas;
    private final AreaRepository areas;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, UserAreaRepository userAreas, AreaRepository areas,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userAreas = userAreas;
        this.areas = areas;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Role role) {
        long tenantId = CurrentUser.get().requireTenant();
        List<AppUser> list = role == null
                ? users.findByTenantIdOrderByName(tenantId)
                : users.findByTenantIdAndRoleOrderByName(tenantId, role);
        return toResponses(list);
    }

    /** Owners create agents. Owner accounts are created with the tenant. */
    @Transactional
    public UserResponse createAgent(UserCreate in) {
        long tenantId = CurrentUser.get().requireTenant();
        if (users.existsByPhone(in.phone())) {
            throw ApiException.conflict("PHONE_IN_USE", "A user with this phone number already exists");
        }
        AppUser user = users.save(new AppUser(tenantId, in.name().trim(), in.phone(),
                passwordEncoder.encode(in.pin()), Role.AGENT));
        if (in.areaIds() != null && !in.areaIds().isEmpty()) {
            assignAreas(tenantId, user.getId(), in.areaIds());
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(long userId, UserUpdate in) {
        CurrentUser me = CurrentUser.get();
        AppUser user = users.findByIdAndTenantId(userId, me.requireTenant())
                .orElseThrow(() -> ApiException.notFound("User"));
        if (in.name() != null) {
            user.setName(in.name().trim());
        }
        if (in.isActive() != null) {
            if (!in.isActive() && user.getId() == me.userId()) {
                throw ApiException.rule("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
            }
            if (user.isActive() && !in.isActive()) {
                user.revokeTokens();
            }
            user.setActive(in.isActive());
        }
        if (in.pin() != null) {
            user.setPinHash(passwordEncoder.encode(in.pin()));
            // A new PIN signs the old phone out (lost phone, agent left).
            user.revokeTokens();
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse replaceAreas(long userId, List<Long> areaIds) {
        long tenantId = CurrentUser.get().requireTenant();
        AppUser user = users.findByIdAndTenantId(userId, tenantId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() != Role.AGENT) {
            throw ApiException.rule("NOT_AN_AGENT", "Areas can only be assigned to agents");
        }
        userAreas.deleteByUserId(userId);
        userAreas.flush();
        assignAreas(tenantId, userId, areaIds);
        return toResponse(user);
    }

    private void assignAreas(long tenantId, long userId, Collection<Long> areaIds) {
        Set<Long> wanted = new HashSet<>(areaIds);
        List<Area> found = areas.findByTenantIdAndIdIn(tenantId, wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.rule("INVALID_AREA", "One or more areas do not exist");
        }
        wanted.forEach(areaId -> userAreas.save(new UserArea(tenantId, userId, areaId)));
    }

    // ------------------------------------------------------------------ mapping

    public UserResponse toResponse(AppUser user) {
        return toResponses(List.of(user)).getFirst();
    }

    private List<UserResponse> toResponses(List<AppUser> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> areaIdsByUser = userAreas.findByUserIdIn(list.stream().map(AppUser::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(UserArea::getUserId,
                        Collectors.mapping(UserArea::getAreaId, Collectors.toList())));
        Set<Long> allAreaIds = areaIdsByUser.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Long tenantId = list.getFirst().getTenantId();
        Map<Long, Area> areaById = allAreaIds.isEmpty() || tenantId == null ? Map.of()
                : areas.findByTenantIdAndIdIn(tenantId, allAreaIds).stream()
                        .collect(Collectors.toMap(Area::getId, Function.identity()));

        return list.stream().map(u -> new UserResponse(
                u.getId(), u.getName(), u.getPhone(), u.getRole(), u.isActive(),
                areaIdsByUser.getOrDefault(u.getId(), List.of()).stream()
                        .map(areaById::get).filter(a -> a != null).map(AreaResponse::of).toList(),
                u.getLastLoginAt())).toList();
    }
}
