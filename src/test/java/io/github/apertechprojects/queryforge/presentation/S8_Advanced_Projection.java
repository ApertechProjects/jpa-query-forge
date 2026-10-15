package io.github.apertechprojects.queryforge.presentation;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.annotation.ForgeSubModel;
import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.criteria.CriteriaList;
import io.github.apertechprojects.queryforge.query.ForgeQuery;
import io.github.apertechprojects.queryforge.criteria.CriteriaOperator;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.AdminUser;
import io.github.apertechprojects.queryforge.testenv.repository.AdminUserRepository;
import io.github.apertechprojects.queryforge.util.PresentationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Pair;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class S8_Advanced_Projection extends BaseTestInstance {
    private @Autowired AdminUserRepository adminUserRepository;


    public static class AuthorizationSummary {
        private Long adminId;
        private String adminUsername;
        private Long roleId;
        private String roleName;
        private Long authorizationId;
        private String authorizationName;
        private String menuIcon;

        public AuthorizationSummary() {
        }

        public AuthorizationSummary(Long adminId, String adminUsername, Long roleId, String roleName, Long authorizationId, String authorizationName, String menuIcon) {
            this.adminId = adminId;
            this.adminUsername = adminUsername;
            this.roleId = roleId;
            this.roleName = roleName;
            this.authorizationId = authorizationId;
            this.authorizationName = authorizationName;
            this.menuIcon = menuIcon;
        }

        public Long getAdminId() {
            return adminId;
        }

        public void setAdminId(Long adminId) {
            this.adminId = adminId;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getAuthorizationName() {
            return authorizationName;
        }

        public void setAuthorizationName(String authorizationName) {
            this.authorizationName = authorizationName;
        }

        public String getMenuIcon() {
            return menuIcon;
        }

        public void setMenuIcon(String menuIcon) {
            this.menuIcon = menuIcon;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AuthorizationSummary)) return false;
            AuthorizationSummary that = (AuthorizationSummary) o;
            return Objects.equals(adminId, that.adminId) &&
                    Objects.equals(adminUsername, that.adminUsername) &&
                    Objects.equals(roleId, that.roleId) &&
                    Objects.equals(roleName, that.roleName) &&
                    Objects.equals(authorizationId, that.authorizationId) &&
                    Objects.equals(authorizationName, that.authorizationName) &&
                    Objects.equals(menuIcon, that.menuIcon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(adminId, adminUsername, roleId, roleName, authorizationId, authorizationName, menuIcon);
        }
    }

    @Test
    public void roleJoin() {
        ForgeQuery dynamicQuery = new ForgeQuery();
        dynamicQuery.getSelect().add(Pair.of("id", "adminId"));
        dynamicQuery.getSelect().add(Pair.of("username", "adminUsername"));
        dynamicQuery.getSelect().add(Pair.of("roles.id", "roleId"));
        dynamicQuery.getSelect().add(Pair.of("roles.name", "roleName"));
        dynamicQuery.getSelect().add(Pair.of("roles.roleAuthorizations.authorization.id", "authorizationId"));
        dynamicQuery.getSelect().add(Pair.of("roles.roleAuthorizations.authorization.name", "authorizationName"));
        dynamicQuery.getSelect().add(Pair.of("roles.roleAuthorizations.authorization.menuIcon", "menuIcon"));
        var criteriaList = CriteriaList.of(Criteria.of("roles.roleAuthorizations.authorization.menuIcon", CriteriaOperator.START_WITH, "icon"));
        dynamicQuery.getWhere().addAll(criteriaList);
        PresentationUtil.prettyPrint(dynamicQuery);
        List<AdminUser> result = adminUserRepository.findAll(criteriaList);
        PresentationUtil.prettyPrint(result);
        assertEquals(List.of(adminUser1, adminUser2, adminUser3, adminUser4, adminUser5), result);

        List<AuthorizationSummary> result2 = adminUserRepository.findAll(dynamicQuery, AuthorizationSummary.class);
        PresentationUtil.prettyPrint(result2);

        assertEquals(List.of(new AuthorizationSummary(1L, "admin1", 1L, "role1", 1L, "auth1", "icon1"),
                new AuthorizationSummary(2L, "admin2", 2L, "role2", 2L, "auth2", "icon2"),
                new AuthorizationSummary(3L, "admin3", 3L, "role3", 3L, "auth3", "icon3"),
                new AuthorizationSummary(4L, "admin4", 4L, "role4", 4L, "auth4", "icon4"),
                new AuthorizationSummary(5L, "admin5", 5L, "role5", 5L, "auth5", "icon5")), result2);
    }


    @ForgeModel // DONT MISS THIS ANNOTATION
    public static class AnnotatedAuthorizationSummary {
        @ForgeField("id")
        private Long adminId;
        @ForgeField("username")
        private String adminUsername;
        @ForgeField("roles.id")
        private Long roleId;
        @ForgeField("roles.name")
        private String roleName;
        @ForgeField("roles.roleAuthorizations.authorization.id")
        private Long authorizationId;
        @ForgeField("roles.roleAuthorizations.authorization.name")
        private String authorizationName;
        @ForgeField("roles.roleAuthorizations.authorization.menuIcon")
        private String menuIcon;

        public AnnotatedAuthorizationSummary() {
        }

        public AnnotatedAuthorizationSummary(Long adminId, String adminUsername, Long roleId, String roleName, Long authorizationId, String authorizationName, String menuIcon) {
            this.adminId = adminId;
            this.adminUsername = adminUsername;
            this.roleId = roleId;
            this.roleName = roleName;
            this.authorizationId = authorizationId;
            this.authorizationName = authorizationName;
            this.menuIcon = menuIcon;
        }

        public Long getAdminId() {
            return adminId;
        }

        public void setAdminId(Long adminId) {
            this.adminId = adminId;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getAuthorizationName() {
            return authorizationName;
        }

        public void setAuthorizationName(String authorizationName) {
            this.authorizationName = authorizationName;
        }

        public String getMenuIcon() {
            return menuIcon;
        }

        public void setMenuIcon(String menuIcon) {
            this.menuIcon = menuIcon;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnnotatedAuthorizationSummary)) return false;
            AnnotatedAuthorizationSummary that = (AnnotatedAuthorizationSummary) o;
            return Objects.equals(adminId, that.adminId) &&
                    Objects.equals(adminUsername, that.adminUsername) &&
                    Objects.equals(roleId, that.roleId) &&
                    Objects.equals(roleName, that.roleName) &&
                    Objects.equals(authorizationId, that.authorizationId) &&
                    Objects.equals(authorizationName, that.authorizationName) &&
                    Objects.equals(menuIcon, that.menuIcon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(adminId, adminUsername, roleId, roleName, authorizationId, authorizationName, menuIcon);
        }
    }

    @Test
    public void roleJoinWithAnnotatedModel() {
        ForgeQuery dynamicQuery = new ForgeQuery();
        dynamicQuery.getWhere().add(Criteria.of("roles.roleAuthorizations.authorization.menuIcon", CriteriaOperator.START_WITH, "icon"));
        PresentationUtil.prettyPrint(dynamicQuery);
        List<AnnotatedAuthorizationSummary> result2 = adminUserRepository.findAll(dynamicQuery, AnnotatedAuthorizationSummary.class);
        PresentationUtil.prettyPrint(result2);

        assertEquals(List.of(new AnnotatedAuthorizationSummary(1L, "admin1", 1L, "role1", 1L, "auth1", "icon1"),
                new AnnotatedAuthorizationSummary(2L, "admin2", 2L, "role2", 2L, "auth2", "icon2"),
                new AnnotatedAuthorizationSummary(3L, "admin3", 3L, "role3", 3L, "auth3", "icon3"),
                new AnnotatedAuthorizationSummary(4L, "admin4", 4L, "role4", 4L, "auth4", "icon4"),
                new AnnotatedAuthorizationSummary(5L, "admin5", 5L, "role5", 5L, "auth5", "icon5")), result2);
    }


    @ForgeModel
    public static class AnnotatedAuthorizationSummarySubModel {
        @ForgeField("id")
        private Long adminId;
        @ForgeField("username")
        private String adminUsername;

        @ForgeSubModel("roles")
        private Role role;

        public AnnotatedAuthorizationSummarySubModel() {
        }

        public AnnotatedAuthorizationSummarySubModel(Long adminId, String adminUsername, Role role) {
            this.adminId = adminId;
            this.adminUsername = adminUsername;
            this.role = role;
        }

        public Long getAdminId() {
            return adminId;
        }

        public void setAdminId(Long adminId) {
            this.adminId = adminId;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnnotatedAuthorizationSummarySubModel)) return false;
            AnnotatedAuthorizationSummarySubModel that = (AnnotatedAuthorizationSummarySubModel) o;
            return Objects.equals(adminId, that.adminId) &&
                    Objects.equals(adminUsername, that.adminUsername) &&
                    Objects.equals(role, that.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(adminId, adminUsername, role);
        }

        @ForgeModel
        public record Role(
                @ForgeField("id") Long roleId,
                @ForgeField("name") String roleName,
                @ForgeSubModel("roleAuthorizations") RoleAuthorization roleAuthorization) {


            @ForgeModel
            public record RoleAuthorization(
                    @ForgeField("authorization.id") Long authorizationId,
                    @ForgeField("authorization.name") String authorizationName,
                    @ForgeField("authorization.menuIcon") String menuIcon
            ) {

            }
        }
    }


    @Test
    public void roleJoinAnnotatedAuthorizationSummarySubModel() {
        ForgeQuery dynamicQuery = new ForgeQuery();
        dynamicQuery.getWhere().add(Criteria.of("roles.roleAuthorizations.authorization.menuIcon", CriteriaOperator.START_WITH, "icon"));
        PresentationUtil.prettyPrint(dynamicQuery);
        List<AnnotatedAuthorizationSummarySubModel> result2 = adminUserRepository.findAll(dynamicQuery, AnnotatedAuthorizationSummarySubModel.class);
        PresentationUtil.prettyPrint(result2);

        assertEquals(List.of(new AnnotatedAuthorizationSummarySubModel(1L, "admin1", new AnnotatedAuthorizationSummarySubModel.Role(1L, "role1", new AnnotatedAuthorizationSummarySubModel.Role.RoleAuthorization(1L, "auth1", "icon1"))),
                new AnnotatedAuthorizationSummarySubModel(2L, "admin2", new AnnotatedAuthorizationSummarySubModel.Role(2L, "role2", new AnnotatedAuthorizationSummarySubModel.Role.RoleAuthorization(2L, "auth2", "icon2"))),
                new AnnotatedAuthorizationSummarySubModel(3L, "admin3", new AnnotatedAuthorizationSummarySubModel.Role(3L, "role3", new AnnotatedAuthorizationSummarySubModel.Role.RoleAuthorization(3L, "auth3", "icon3"))),
                new AnnotatedAuthorizationSummarySubModel(4L, "admin4", new AnnotatedAuthorizationSummarySubModel.Role(4L, "role4", new AnnotatedAuthorizationSummarySubModel.Role.RoleAuthorization(4L, "auth4", "icon4"))),
                new AnnotatedAuthorizationSummarySubModel(5L, "admin5", new AnnotatedAuthorizationSummarySubModel.Role(5L, "role5", new AnnotatedAuthorizationSummarySubModel.Role.RoleAuthorization(5L, "auth5", "icon5")))), result2);
    }


    @ForgeModel
    public record AnnotatedAuthorizationSummarySubModel2(
            @ForgeField("id") Long adminId,
            @ForgeField("username") String adminUsername,
            @ForgeSubModel("roles") Role2 role) {

        @ForgeModel
        public static class Role2 {
            @ForgeField("id")
            private Long roleId;
            @ForgeField("name")
            private String roleName;
            @ForgeSubModel("roleAuthorizations")
            private RoleAuthorization2 roleAuthorization;

            public Role2() {
            }

            public Role2(Long roleId, String roleName, RoleAuthorization2 roleAuthorization) {
                this.roleId = roleId;
                this.roleName = roleName;
                this.roleAuthorization = roleAuthorization;
            }

            public Long getRoleId() {
                return roleId;
            }

            public void setRoleId(Long roleId) {
                this.roleId = roleId;
            }

            public String getRoleName() {
                return roleName;
            }

            public void setRoleName(String roleName) {
                this.roleName = roleName;
            }

            public RoleAuthorization2 getRoleAuthorization() {
                return roleAuthorization;
            }

            public void setRoleAuthorization(RoleAuthorization2 roleAuthorization) {
                this.roleAuthorization = roleAuthorization;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Role2)) return false;
                Role2 role2 = (Role2) o;
                return Objects.equals(roleId, role2.roleId) &&
                        Objects.equals(roleName, role2.roleName) &&
                        Objects.equals(roleAuthorization, role2.roleAuthorization);
            }

            @Override
            public int hashCode() {
                return Objects.hash(roleId, roleName, roleAuthorization);
            }


            @ForgeModel
            public record RoleAuthorization2(
                    @ForgeField("authorization.id") Long authorizationId,
                    @ForgeField("authorization.name") String authorizationName,
                    @ForgeField("authorization.menuIcon") String menuIcon
            ) {
            }
        }
    }

    @Test
    public void roleJoinAnnotatedAuthorizationSummarySubModel2() {
        ForgeQuery dynamicQuery = new ForgeQuery();
        dynamicQuery.getWhere().add(Criteria.of("roles.roleAuthorizations.authorization.menuIcon", CriteriaOperator.START_WITH, "icon"));
        PresentationUtil.prettyPrint(dynamicQuery);
        List<AnnotatedAuthorizationSummarySubModel2> result2 = adminUserRepository.findAll(dynamicQuery, AnnotatedAuthorizationSummarySubModel2.class);
        PresentationUtil.prettyPrint(result2);

        assertEquals(List.of(new AnnotatedAuthorizationSummarySubModel2(1L, "admin1", new AnnotatedAuthorizationSummarySubModel2.Role2(1L, "role1", new AnnotatedAuthorizationSummarySubModel2.Role2.RoleAuthorization2(1L, "auth1", "icon1"))),
                new AnnotatedAuthorizationSummarySubModel2(2L, "admin2", new AnnotatedAuthorizationSummarySubModel2.Role2(2L, "role2", new AnnotatedAuthorizationSummarySubModel2.Role2.RoleAuthorization2(2L, "auth2", "icon2"))),
                new AnnotatedAuthorizationSummarySubModel2(3L, "admin3", new AnnotatedAuthorizationSummarySubModel2.Role2(3L, "role3", new AnnotatedAuthorizationSummarySubModel2.Role2.RoleAuthorization2(3L, "auth3", "icon3"))),
                new AnnotatedAuthorizationSummarySubModel2(4L, "admin4", new AnnotatedAuthorizationSummarySubModel2.Role2(4L, "role4", new AnnotatedAuthorizationSummarySubModel2.Role2.RoleAuthorization2(4L, "auth4", "icon4"))),
                new AnnotatedAuthorizationSummarySubModel2(5L, "admin5", new AnnotatedAuthorizationSummarySubModel2.Role2(5L, "role5", new AnnotatedAuthorizationSummarySubModel2.Role2.RoleAuthorization2(5L, "auth5", "icon5")))), result2);
    }


    @ForgeModel
    public record AnnotatedAuthorizationSummarySubModel3(
            @ForgeField("id") Long adminId,
            @ForgeField("username") String adminUsername,
            @ForgeSubModel() Role3 role) { // empty sub model annotation

        @ForgeModel
        public static class Role3 {
            @ForgeField("roles.id")
            private Long roleId;
            @ForgeField("roles.name")
            private String roleName;
            @ForgeSubModel("roles.roleAuthorizations")
            private RoleAuthorization3 roleAuthorization;

            public Role3() {
            }

            public Role3(Long roleId, String roleName, RoleAuthorization3 roleAuthorization) {
                this.roleId = roleId;
                this.roleName = roleName;
                this.roleAuthorization = roleAuthorization;
            }

            public Long getRoleId() {
                return roleId;
            }

            public void setRoleId(Long roleId) {
                this.roleId = roleId;
            }

            public String getRoleName() {
                return roleName;
            }

            public void setRoleName(String roleName) {
                this.roleName = roleName;
            }

            public RoleAuthorization3 getRoleAuthorization() {
                return roleAuthorization;
            }

            public void setRoleAuthorization(RoleAuthorization3 roleAuthorization) {
                this.roleAuthorization = roleAuthorization;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Role3)) return false;
                Role3 role3 = (Role3) o;
                return Objects.equals(roleId, role3.roleId) &&
                        Objects.equals(roleName, role3.roleName) &&
                        Objects.equals(roleAuthorization, role3.roleAuthorization);
            }

            @Override
            public int hashCode() {
                return Objects.hash(roleId, roleName, roleAuthorization);
            }


            @ForgeModel
            public record RoleAuthorization3(
                    @ForgeField("authorization.id") Long authorizationId,
                    @ForgeField("authorization.name") String authorizationName,
                    @ForgeField("authorization.menuIcon") String menuIcon
            ) {
            }
        }
    }

    @Test
    public void roleJoinAnnotatedAuthorizationSummarySubModel3EmptySubModel() {
        ForgeQuery dynamicQuery = new ForgeQuery();
        dynamicQuery.getWhere().add(Criteria.of("roles.roleAuthorizations.authorization.menuIcon", CriteriaOperator.START_WITH, "icon"));
        PresentationUtil.prettyPrint(dynamicQuery);
        List<AnnotatedAuthorizationSummarySubModel3> result2 = adminUserRepository.findAll(dynamicQuery, AnnotatedAuthorizationSummarySubModel3.class);
        PresentationUtil.prettyPrint(result2);

        assertEquals(List.of(new AnnotatedAuthorizationSummarySubModel3(1L, "admin1", new AnnotatedAuthorizationSummarySubModel3.Role3(1L, "role1", new AnnotatedAuthorizationSummarySubModel3.Role3.RoleAuthorization3(1L, "auth1", "icon1"))),
                new AnnotatedAuthorizationSummarySubModel3(2L, "admin2", new AnnotatedAuthorizationSummarySubModel3.Role3(2L, "role2", new AnnotatedAuthorizationSummarySubModel3.Role3.RoleAuthorization3(2L, "auth2", "icon2"))),
                new AnnotatedAuthorizationSummarySubModel3(3L, "admin3", new AnnotatedAuthorizationSummarySubModel3.Role3(3L, "role3", new AnnotatedAuthorizationSummarySubModel3.Role3.RoleAuthorization3(3L, "auth3", "icon3"))),
                new AnnotatedAuthorizationSummarySubModel3(4L, "admin4", new AnnotatedAuthorizationSummarySubModel3.Role3(4L, "role4", new AnnotatedAuthorizationSummarySubModel3.Role3.RoleAuthorization3(4L, "auth4", "icon4"))),
                new AnnotatedAuthorizationSummarySubModel3(5L, "admin5", new AnnotatedAuthorizationSummarySubModel3.Role3(5L, "role5", new AnnotatedAuthorizationSummarySubModel3.Role3.RoleAuthorization3(5L, "auth5", "icon5")))), result2);
    }
}
