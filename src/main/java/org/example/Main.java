package org.example;

import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.example.model.Course;
import org.example.model.CourseType;
import org.example.model.Student;
import org.example.model.Teacher;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

class Main {
    static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("skillbox");
        EntityManager em = emf.createEntityManager();

        tupleCheck(em);

        em.close();
        emf.close();
    }

    private static void tupleCheck(EntityManager em) {
        inTransaction(em, entityManager -> {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Tuple> query = criteriaBuilder.createQuery(Tuple.class);

            Root<Course> courseRoot = query.from(Course.class);
            Join<Course, Teacher> teacherJoin = courseRoot.join("teacher", JoinType.LEFT);
            Join<Course, Student> studentJoin = courseRoot.join("students", JoinType.LEFT);


            query.select(criteriaBuilder.tuple(
                    courseRoot.alias("course"),
                    teacherJoin.alias("teacher"),
                    studentJoin.alias("student")
            ));

            List<Tuple> resultList = entityManager.createQuery(query).getResultList();

            for (Tuple t : resultList) {
                Course course = t.get("course", Course.class);
                Teacher teacher = t.get("teacher",Teacher.class);
                Student student = t.get("student", Student.class);
                System.out.println(
                        course.getName() + " | " + (teacher != null ? teacher.getName() : "no teacher") + " | " +
                                (student != null ? student.getName() : "no students")
                );
            }
        });
    }


    private static void getTeacherAge(EntityManager em, int age) {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Course> query = criteriaBuilder.createQuery(Course.class);
        Root<Course> from = query.from(Course.class);
        from.<Course, Teacher>fetch("teacher", JoinType.LEFT);
        Join<Course, Teacher> join = from.<Course, Teacher>join("teacher", JoinType.LEFT);

        query.where(criteriaBuilder.gt(join.get("age"), age));
        query.select(from);

        List<Course> resultList = em.createQuery(query).getResultList();
        resultList.forEach(System.out::println);
    }

    private static void entityGraphCheck(EntityManager em) {
        inTransaction(em, entityManager -> {
            EntityGraph<Course> entityGraph = entityManager.createEntityGraph(Course.class);

            List<Course> resultList = entityManager.createQuery("select c from Course c", Course.class)
                    .setHint("jakarta.persistence.fetchgraph", "Course.full")
                    .getResultList();

            resultList.forEach(System.out::println);

        });
    }

    private static void orphanCheck(EntityManager em) {
        inTransaction(em, entityManager -> {
            Teacher teacher = entityManager
                    .createQuery("select t from Teacher t join t.courses", Teacher.class)
                    .setMaxResults(1)
                    .getSingleResult();
            Course removed = teacher.getCourses().getFirst();
            System.out.println("Removing: " + removed.getName());
            teacher.getCourses().remove(removed);
        });

        em.clear();
        Teacher teacher = em
                .createQuery("select t from Teacher t join t.courses", Teacher.class)
                .setMaxResults(1)
                .getSingleResult();
        System.out.println("Courses left: " + teacher.getCourses().size());
    }

    private static void testCascade(EntityManager em) {
        Course course = new Course();
        course.setName("Course");
        course.setType(CourseType.BUSINESS);
        course.setDuration(110);
        course.setDescription("desc");
        course.setPrice(7717);

        Teacher teacher = new Teacher();
        teacher.setName("New Teacher");
        teacher.setSalary(100);
        teacher.setAge(30);
        course.setTeacher(teacher);

        inTransaction(em, (Consumer<EntityManager>) em2 -> em2.persist(course)); // що станеться?
    }

    private static void nPlusOneCheck(EntityManager em) {
        inTransaction(em, entityManager -> {
            List<Course> courses = entityManager
                    .createQuery("select c from Course c join fetch c.students", Course.class)
                    .getResultList();

            for (Course course : courses) {
                System.out.println(course.getName() + " students: " + course.getStudents().size());
            }
        });
    }

    private static void mergeCheck(EntityManager em) {
        Course detachedCourse = new Course();
        detachedCourse.setName("Merged Course");
        detachedCourse.setType(CourseType.BUSINESS);
        detachedCourse.setDuration(10);
        detachedCourse.setDescription("desc");
        detachedCourse.setId(getCourse(em, 1).getId());
        detachedCourse.setPrice(777);

        inTransaction(em, entityManager -> {
            Course managed = entityManager.merge(detachedCourse);
            System.out.println("detached contains: " + entityManager.contains(detachedCourse));
            System.out.println("managed contains: " + entityManager.contains(managed));
            System.out.println("same object: " + (managed == detachedCourse));
            System.out.println("managed name: " + managed.getName());
        });

        // Перевірка в БД: clear() спорожняє кеш, find піде в БД
        em.clear();
        Course fromDb = getCourse(em, detachedCourse.getId());
        System.out.println("DB name: " + fromDb.getName());
        System.out.println("DB price: " + fromDb.getPrice());
    }

    private static void checkConditions(EntityManager em) {
        inTransaction(em, entityManager -> {
            Course course = new Course();
            course.setName("Test");
            course.setType(CourseType.BUSINESS);  // NOT NULL
            course.setPrice(1000);                 // якщо NOT NULL
            course.setDuration(10);
            course.setDescription("desc");
            // transient: Hibernate про нього не знає
            System.out.println("new: " + entityManager.contains(course)); // false

            entityManager.persist(course);
            // managed: під наглядом
            System.out.println("after persist: " + entityManager.contains(course)); // true

            entityManager.flush(); // щоб ID згенерувався
            System.out.println("id: " + course.getId());

            entityManager.clear();
            // detached: вийшов з persistence context
            System.out.println("after clear: " + entityManager.contains(course)); // false

            Course found = entityManager.find(Course.class, course.getId());
            // managed знову — і це ІНШИЙ об'єкт, ніж course!
            System.out.println("found == course: " + (found == course)); // false
            System.out.println("after find: " + entityManager.contains(found)); // true
        });
    }

    private static void clearCheck(EntityManager em) {
        inTransaction(em, entityManager -> {
            Course course = getCourse(entityManager, 1);
            course.setPrice(40_000);
            entityManager.clear();
        });
    }

    private static void dirtyChecking(EntityManager em) {
        // Dirty checking: find повертає managed-сутність,
        // зміна поля + commit -> Hibernate сам генерує UPDATE
        inTransaction(em, entityManager -> {
            Course course = getCourse(entityManager, 1);
            System.out.println("Before: " + course.getName() + " price=" + course.getPrice());
            course.setPrice(30000);
            // жодного update-методу не викликаємо
        });

        // em.clear() спорожняє persistence context — наступний find
        // піде в БД, а не поверне той самий об'єкт з кешу
        em.clear();
        Course reloaded = getCourse(em, 1);
        System.out.println("After: " + reloaded.getName() + " price=" + reloaded.getPrice());
    }

    private static List<Course> getCourseGreaterThanPrice(EntityManager em, int price) {
        return inTransaction(em, entityManager -> {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Course> query = criteriaBuilder.createQuery(Course.class);
            Root<Course> from = query.from(Course.class);
            query.select(from).where(criteriaBuilder.greaterThan(from.<Integer>get("price"), price));
            return entityManager.createQuery(query).getResultList();
        });
    }

    private static List<Course> getListCourse(EntityManager em) {
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

    private static Course getCourse(EntityManager em, int id) {
        return em.find(Course.class, id);

    }
}
