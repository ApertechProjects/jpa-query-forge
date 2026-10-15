package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.school.Course;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaQueryForgeRepository<Course, Long> {
}
