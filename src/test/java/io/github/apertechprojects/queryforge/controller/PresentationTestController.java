package io.github.apertechprojects.queryforge.controller;

import io.github.apertechprojects.queryforge.criteria.CriteriaList;
import io.github.apertechprojects.queryforge.query.ForgeQuery;
import io.github.apertechprojects.queryforge.presentation.S9_Query_Builder;
import io.github.apertechprojects.queryforge.testenv.entity.school.Course;
import io.github.apertechprojects.queryforge.testenv.repository.AdminUserRepository;
import io.github.apertechprojects.queryforge.testenv.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/test-api")
public class PresentationTestController {
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private AdminUserRepository adminUserRepository;


    @GetMapping("/course")
    public ResponseEntity<List<Course>> getCourseWithCriteria(CriteriaList criteriaList) {
        List<Course> customerList = courseRepository.findAll(criteriaList);
        return ResponseEntity.ok().body(customerList);
    }

    @GetMapping("/course/as-page")
    public ResponseEntity<Page<Course>> getCourseWithSearchFilterAsPage(ForgeQuery dynamicQuery) {
        Page<Course> customerList = courseRepository.findAllAsPage(dynamicQuery);
        return ResponseEntity.ok().body(customerList);
    }

    @GetMapping("/course/as-list")
    public ResponseEntity<List<S9_Query_Builder.AuthorizationSummary>> getCourseWithSearchFilterAsList(ForgeQuery dynamicQuery) {
        List<S9_Query_Builder.AuthorizationSummary> customerList = adminUserRepository.findAll(dynamicQuery, S9_Query_Builder.AuthorizationSummary.class);
        return ResponseEntity.ok().body(customerList);
    }
}
