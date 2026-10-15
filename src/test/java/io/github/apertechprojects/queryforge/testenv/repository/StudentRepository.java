package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.school.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, ForgeRepository<Student, Long> {
}
