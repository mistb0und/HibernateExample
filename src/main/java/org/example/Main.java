package org.example;

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.example.model.Course;
import org.example.model.CourseType;
import org.example.model.Student;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

class Main {
    static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("skillbox");
        EntityManager em = emf.createEntityManager();
        List<Course> course = getCourseGreaterThanPrice(em, 100000);

        course.forEach(course1 -> System.out.println(course1.getName() + " - " + course1.getPrice()));

        em.close();
        emf.close();
    }

    private static List<Course> getCourseGreaterThanPrice(EntityManager em, int price) {
        return inTransaction(em, entityManager -> {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Course> query = criteriaBuilder.createQuery(Course.class);
            Root<Course> from = query.from(Course.class);
            query.select(from).where(criteriaBuilder.greaterThan(from.<Integer>get("price"), price));
             return  entityManager.createQuery(query).getResultList();
        });
    }

    private static List<Course> getCourse(EntityManager em) {
        return inTransaction(em, entityManager -> {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Course> query = criteriaBuilder.createQuery(Course.class);
            Root<Course> from = query.from(Course.class);
            query.select(from);
            return entityManager.createQuery(query).getResultList();
        });
    }

    private static List<Student> printStudents(EntityManager em) {
        return inTransaction(em, entityManager -> {
            return entityManager
                    .createQuery("select c from Course c", Course.class)
                    .getResultList()
                    .stream()
                    .map(Course::getStudents)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        });
    }

    private static void printTeacherName(EntityManager em, String name) {
        inTransaction(em, entityManager -> {
            entityManager
                    .createQuery("select c from Course c where c.name=:name", Course.class)
                    .setParameter("name", name)
                    .getResultList()
                    .stream()
                    .map(Course::getTeacher)
                    .findFirst()
                    .ifPresent(teacher -> System.out.println(teacher.getName()));
        });
    }

    private static void deleteCourse(EntityManager em, int id) {
        inTransaction(em, entityManager -> {
            Course course = entityManager.find(Course.class, id);
            if (course != null) {
                entityManager.remove(course);
            }
        });
    }

    private static void deleteCourseByName(EntityManager em, String name) {
        inTransaction(em, entityManager -> {
            entityManager
                    .createQuery("SELECT c FROM Course c WHERE c.name = :name", Course.class)
                    .setParameter("name", name)
                    .getResultList()
                    .stream()
                    .findFirst().ifPresent(entityManager::remove);
        });
    }

    private static void setNameCourse(EntityManager em, int id, String name) {
        inTransaction(em, entityManager -> {
            Course course = entityManager.find(Course.class, id);
            if (course != null) {
                course.setName(name);
            }
        });
    }

    private static void saveCourse(EntityManager em) {
        Course course = new Course();
        course.setName("Course 1");
        course.setDescription("Course 1");
        course.setType(CourseType.BUSINESS);
        inTransaction(em, (Consumer<EntityManager>) entityManager -> entityManager.persist(course));

        System.out.println("Saved course with id: " + course.getId());
    }

    private static <T> T inTransaction(EntityManager em, Function<EntityManager, T> action) {
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        try {
            T result = action.apply(em);
            transaction.commit();
            return result;
        } catch (RuntimeException e) {
            transaction.rollback();
            throw e;
        }
    }

    private static void inTransaction(EntityManager em, Consumer<EntityManager> action) {
        inTransaction(em, entityManager -> {
            action.accept(entityManager);
            return null;
        });
    }

    private static void getCourse(EntityManager em, int id) {
        Course course = em.find(Course.class, id);
        System.out.println(course);
    }
}
