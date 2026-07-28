package cc.ddrpa.filtro.core;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.QueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroRegistryTest {

    private FiltroRegistry registry;

    interface AdminRole {}
    interface SysAdmin extends AdminRole {}
    interface GuestRole {}

    private static FiltroFieldMeta meta(String name, Class<?>... groups) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField(name);
        m.setQueryIntent(QueryIntent.SEARCH);
        if (groups.length == 0) {
            m.setGroups(Collections.emptySet());
        } else {
            m.setGroups(Set.of(groups));
        }
        return m;
    }

    @BeforeEach
    void setUp() {
        registry = new FiltroRegistry();
        registry.register(Book.class, List.of(
                meta("title"),                              // no groups
                meta("secret", AdminRole.class),           // AdminRole only
                meta("adminNote", SysAdmin.class)          // SysAdmin only
        ));
    }

    static class Book {}

    @Nested
    class GroupFiltering {

        @Test
        void voidGroupReturnsOnlyUngrouped() {
            List<FiltroFieldMeta> result = registry.get(Book.class, void.class);
            assertThat(result).extracting(FiltroFieldMeta::getField).containsExactly("title");
        }

        @Test
        void nullGroupReturnsOnlyUngrouped() {
            List<FiltroFieldMeta> result = registry.get(Book.class, null);
            assertThat(result).extracting(FiltroFieldMeta::getField).containsExactly("title");
        }

        @Test
        void adminGroupSeesAdminFieldsAndUngrouped() {
            List<FiltroFieldMeta> result = registry.get(Book.class, AdminRole.class);
            assertThat(result).extracting(FiltroFieldMeta::getField)
                    .containsExactlyInAnyOrder("title", "secret");
        }

        @Test
        void sysAdminSeesSysAdminAdminAndUngrouped() {
            // SysAdmin extends AdminRole → visible for both SysAdmin and AdminRole fields
            List<FiltroFieldMeta> result = registry.get(Book.class, SysAdmin.class);
            assertThat(result).extracting(FiltroFieldMeta::getField)
                    .containsExactlyInAnyOrder("title", "secret", "adminNote");
        }

        @Test
        void adminRoleCannotSeeSysAdminFields() {
            // AdminRole is NOT assignable from SysAdmin → SysAdmin fields hidden
            List<FiltroFieldMeta> result = registry.get(Book.class, AdminRole.class);
            assertThat(result).extracting(FiltroFieldMeta::getField)
                    .doesNotContain("adminNote");
        }

        @Test
        void guestSeesOnlyUngrouped() {
            List<FiltroFieldMeta> result = registry.get(Book.class, GuestRole.class);
            assertThat(result).extracting(FiltroFieldMeta::getField).containsExactly("title");
        }

        @Test
        void unknownTypeReturnsEmpty() {
            assertThat(registry.get(String.class, void.class)).isEmpty();
        }

        @Test
        void getAsMapReturnsCorrectMap() {
            Map<String, FiltroFieldMeta> map = registry.getAsMap(Book.class, void.class);
            assertThat(map).containsOnlyKeys("title");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void hasTypeReturnsTrueAfterRegistration() {
            assertThat(registry.hasType(Book.class)).isTrue();
            assertThat(registry.hasType(String.class)).isFalse();
        }

        @Test
        void registeredTypeCountTracksRegistrations() {
            assertThat(registry.registeredTypeCount()).isEqualTo(1);
            registry.register(String.class, List.of(meta("value")));
            assertThat(registry.registeredTypeCount()).isEqualTo(2);
        }

        @Test
        void maxDepthDefaultsTo20() {
            assertThat(registry.getMaxDepth()).isEqualTo(20);
        }

        @Test
        void maxDepthCanBeSet() {
            registry.setMaxDepth(50);
            assertThat(registry.getMaxDepth()).isEqualTo(50);
        }
    }
}
