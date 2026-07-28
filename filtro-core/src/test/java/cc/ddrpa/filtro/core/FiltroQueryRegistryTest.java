package cc.ddrpa.filtro.core;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.QueryIntent;
import cc.ddrpa.filtro.core.provider.AnnotatedClassFiltroFieldMetaProvider;
import cc.ddrpa.filtro.core.provider.FiltroFieldMetaProvider;
import cc.ddrpa.filtro.core.provider.InMemoryFiltroFieldMetaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroQueryRegistryTest {

    private InMemoryFiltroFieldMetaProvider inMemory;
    private FiltroRegistry registry;

    private static FiltroFieldMeta meta(String name, Class<?>... groups) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField(name);
        m.setQueryIntent(QueryIntent.SEARCH);
        m.setJavaType(String.class);
        if (groups.length == 0) {
            m.setGroups(Collections.emptySet());
        } else {
            m.setGroups(Set.of(groups));
        }
        return m;
    }

    @BeforeEach
    void setUp() {
        inMemory = new InMemoryFiltroFieldMetaProvider();
        registry = new FiltroRegistry(List.of(inMemory));
        inMemory.register(Book.class, List.of(
                meta("title"),                              // no groups
                meta("secret", AdminRole.class),           // AdminRole only
                meta("adminNote", SysAdmin.class)          // SysAdmin only
        ));
    }

    interface AdminRole {
    }

    interface SysAdmin extends AdminRole {
    }

    interface GuestRole {
    }

    static class Book {
    }

    static class Asset {
    }

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
            inMemory.register(String.class, List.of(meta("value")));
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

        @Test
        @SuppressWarnings("deprecation")
        void deprecatedRegisterDelegatesToInMemory() {
            registry.register(Asset.class, List.of(meta("color")));
            assertThat(registry.get(Asset.class, void.class))
                    .extracting(FiltroFieldMeta::getField)
                    .containsExactly("color");
        }

        @Test
        void registerUpsertsExistingType() {
            inMemory.register(Book.class, List.of(meta("updatedTitle")));
            assertThat(registry.get(Book.class, void.class))
                    .extracting(FiltroFieldMeta::getField)
                    .containsExactly("updatedTitle");
        }
    }

    @Nested
    class ProviderSelection {

        @Test
        void customProviderWinsOverInMemoryAndAnnotated() {
            AnnotatedClassFiltroFieldMetaProvider annotated = new AnnotatedClassFiltroFieldMetaProvider();
            annotated.register(Book.class, List.of(meta("fromAnnotated")));
            inMemory.register(Book.class, List.of(meta("fromInMemory")));

            FiltroFieldMetaProvider custom = new FiltroFieldMetaProvider() {
                @Override
                public boolean supports(Class<?> criteriaType) {
                    return Book.class.equals(criteriaType);
                }

                @Override
                public List<FiltroFieldMeta> getFields(Class<?> criteriaType) {
                    return List.of(meta("fromCustom"));
                }
            };

            FiltroRegistry selecting = new FiltroRegistry(List.of(annotated, inMemory, custom));
            assertThat(selecting.get(Book.class, void.class))
                    .extracting(FiltroFieldMeta::getField)
                    .containsExactly("fromCustom");
        }

        @Test
        void inMemoryWinsOverAnnotatedWhenNoCustom() {
            AnnotatedClassFiltroFieldMetaProvider annotated = new AnnotatedClassFiltroFieldMetaProvider();
            annotated.register(Book.class, List.of(meta("fromAnnotated")));
            InMemoryFiltroFieldMetaProvider memory = new InMemoryFiltroFieldMetaProvider();
            memory.register(Book.class, List.of(meta("fromInMemory")));

            FiltroRegistry selecting = new FiltroRegistry(List.of(annotated, memory));
            assertThat(selecting.get(Book.class, void.class))
                    .extracting(FiltroFieldMeta::getField)
                    .containsExactly("fromInMemory");
        }

        @Test
        void fallsBackToAnnotatedWhenOthersDoNotSupport() {
            AnnotatedClassFiltroFieldMetaProvider annotated = new AnnotatedClassFiltroFieldMetaProvider();
            annotated.register(Book.class, List.of(meta("fromAnnotated")));
            InMemoryFiltroFieldMetaProvider memory = new InMemoryFiltroFieldMetaProvider();

            FiltroRegistry selecting = new FiltroRegistry(List.of(annotated, memory));
            assertThat(selecting.get(Book.class, void.class))
                    .extracting(FiltroFieldMeta::getField)
                    .containsExactly("fromAnnotated");
        }

        @Test
        void emptyClaimStillBlocksLowerPriorityProviders() {
            AnnotatedClassFiltroFieldMetaProvider annotated = new AnnotatedClassFiltroFieldMetaProvider();
            annotated.register(Book.class, List.of(meta("fromAnnotated")));

            FiltroFieldMetaProvider emptyClaim = new FiltroFieldMetaProvider() {
                @Override
                public boolean supports(Class<?> criteriaType) {
                    return Book.class.equals(criteriaType);
                }

                @Override
                public List<FiltroFieldMeta> getFields(Class<?> criteriaType) {
                    return List.of();
                }
            };

            FiltroRegistry selecting = new FiltroRegistry(List.of(annotated, emptyClaim));
            assertThat(selecting.get(Book.class, void.class)).isEmpty();
        }
    }
}
