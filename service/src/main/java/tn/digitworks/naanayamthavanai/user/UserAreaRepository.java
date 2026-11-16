package tn.digitworks.naanayamthavanai.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface UserAreaRepository extends JpaRepository<UserArea, UserArea.Key> {

    List<UserArea> findByUserIdIn(Collection<Long> userIds);

    @Query("select ua.areaId from UserArea ua where ua.userId = :userId")
    List<Long> findAreaIdsByUserId(Long userId);

    @Modifying
    @Query("delete from UserArea ua where ua.userId = :userId")
    void deleteByUserId(Long userId);
}
