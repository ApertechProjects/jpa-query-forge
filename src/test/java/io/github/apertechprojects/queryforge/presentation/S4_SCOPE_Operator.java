package io.github.apertechprojects.queryforge.presentation;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.criteria.CriteriaList;
import io.github.apertechprojects.queryforge.criteria.CriteriaOperator;
import io.github.apertechprojects.queryforge.testenv.entity.school.Course;
import io.github.apertechprojects.queryforge.testenv.repository.CourseRepository;
import io.github.apertechprojects.queryforge.testenv.repository.DepartmentRepository;
import io.github.apertechprojects.queryforge.util.PresentationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class S4_SCOPE_Operator extends BaseTestInstance {
    private @Autowired CourseRepository courseRepository;
    private @Autowired DepartmentRepository departmentRepository;


    //     (A OR B) AND (C OR D)
    @Test
    public void scope() {
        var criteriaList = CriteriaList.of(
                Criteria.of("", CriteriaOperator.PARENTHES,
                        CriteriaList.of(Criteria.of("id", CriteriaOperator.EQUAL, 1),
                                Criteria.OR(),
                                Criteria.of("id", CriteriaOperator.EQUAL, 2))),
                Criteria.of("", CriteriaOperator.PARENTHES,
                        CriteriaList.of(Criteria.of("id", CriteriaOperator.EQUAL, 2),
                                Criteria.OR(),
                                Criteria.of("id", CriteriaOperator.EQUAL, 3)))
        );
        PresentationUtil.prettyPrint(criteriaList);
        List<Course> courseList = courseRepository.findAll(criteriaList);
        PresentationUtil.prettyPrint(courseList);
        assertEquals(List.of(course2), courseList);
    }


    @Test
    public void scopeInsideScope() {
        var criteriaList = CriteriaList.of(
                Criteria.of("", CriteriaOperator.PARENTHES,
                        CriteriaList.of(Criteria.of("id", CriteriaOperator.EQUAL, 1, 2, 3),
                                Criteria.of("id", CriteriaOperator.NOT_EQUAL, 2),
                        Criteria.OR(),
                        Criteria.of("", CriteriaOperator.PARENTHES,
                                CriteriaList.of(Criteria.of("id", CriteriaOperator.EQUAL, 2),
                                        Criteria.of("id", CriteriaOperator.NOT_EQUAL, 3)))))
        );
        PresentationUtil.prettyPrint(criteriaList);
        List<Course> courseList = courseRepository.findAll(criteriaList);
        PresentationUtil.prettyPrint(courseList);
        assertEquals(List.of(course1, course2, course3), courseList);
    }
}
