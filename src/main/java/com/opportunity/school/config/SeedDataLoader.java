package com.opportunity.school.config;

import com.opportunity.school.model.*;
import com.opportunity.school.model.enums.*;
import com.opportunity.school.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/**
 * Seeds the default ADMIN plus a full demo dataset (dev profile only).
 * Run with --spring.profiles.active=dev to get the demo data.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final AcademicYearRepository yearRepository;
    private final TermRepository termRepository;
    private final SchoolClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final PaymentRepository paymentRepository;

    private static final String DEFAULT_ADMIN_EMAIL = "admin@oes.com";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(DEFAULT_ADMIN_EMAIL)) {
            log.info("Seed data already present; skipping");
            return;
        }

        PasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

        // 1. Default admin
        userRepository.save(User.builder()
                .email(DEFAULT_ADMIN_EMAIL)
                .password(encoder.encode("Admin@2026"))
                .role(Role.ADMIN)
                .active(true)
                .build());
        log.info("Seeded default admin {}", DEFAULT_ADMIN_EMAIL);

        // 2. Subjects
        Subject letters = subjectRepository.save(Subject.builder().name("Letters").description("Language activities").build());
        Subject numbers = subjectRepository.save(Subject.builder().name("Numbers").description("Early numeracy").build());
        Subject writing = subjectRepository.save(Subject.builder().name("Writing").description("Pre-writing skills").build());

        // 3. Academic year 2026 with two terms
        AcademicYear year = yearRepository.save(AcademicYear.builder().year(2026).current(true)
                .startDate(LocalDate.of(2026, Month.JANUARY, 5))
                .endDate(LocalDate.of(2026, Month.DECEMBER, 4))
                .build());
        Term term1 = termRepository.save(Term.builder()
                .academicYear(year).number(TermNumber.TERM_1)
                .startDate(LocalDate.of(2026, Month.JANUARY, 5))
                .endDate(LocalDate.of(2026, Month.APRIL, 3))
                .current(true)
                .build());
        termRepository.save(Term.builder()
                .academicYear(year).number(TermNumber.TERM_2)
                .startDate(LocalDate.of(2026, Month.APRIL, 27))
                .endDate(LocalDate.of(2026, Month.AUGUST, 1))
                .build());

        // 4. Demo teacher with linked login
        User teacherUser = userRepository.save(User.builder()
                .email("teacher@oes.com")
                .password(encoder.encode("Teacher@2026"))
                .role(Role.TEACHER)
                .active(true)
                .build());
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .fullName("Grace Wanjiru")
                .phone("0722000111")
                .address("Nairobi")
                .active(true)
                .staffNumber("T-001")
                .joinDate(LocalDate.of(2025, Month.JANUARY, 13))
                .user(teacherUser)
                .build());

        // 5. Classes for 2026
        SchoolClass baby = classRepository.save(SchoolClass.builder()
                .level(ClassLevel.BABY_CLASS).name("Baby Class - Sun").academicYear(year).classTeacher(teacher).build());
        SchoolClass kg1 = classRepository.save(SchoolClass.builder()
                .level(ClassLevel.KG1).name("KG1 - Moon").academicYear(year).classTeacher(teacher).build());
        SchoolClass kg2 = classRepository.save(SchoolClass.builder()
                .level(ClassLevel.KG2).name("KG2 - Star").academicYear(year).build());
        SchoolClass kg3 = classRepository.save(SchoolClass.builder()
                .level(ClassLevel.KG3).name("KG3 - Rainbow").academicYear(year).build());

        // 6. Class subjects (teacher assigned)
        classSubjectRepository.save(ClassSubject.builder().schoolClass(baby).subject(letters).teacher(teacher).build());
        classSubjectRepository.save(ClassSubject.builder().schoolClass(baby).subject(numbers).teacher(teacher).build());
        classSubjectRepository.save(ClassSubject.builder().schoolClass(kg1).subject(letters).teacher(teacher).build());
        classSubjectRepository.save(ClassSubject.builder().schoolClass(kg1).subject(numbers).teacher(teacher).build());
        classSubjectRepository.save(ClassSubject.builder().schoolClass(kg1).subject(writing).teacher(teacher).build());

        // 7. Students + enrollments
        String[][] studentsData = {
                {"Amina Hassan", "F", "Baby Class - Sun"},
                {"Brian Otieno", "M", "Baby Class - Sun"},
                {"Cynthia Mwende", "F", "KG1 - Moon"},
                {"Dennis Kariuki", "M", "KG1 - Moon"},
                {"Esther Njeri", "F", "KG2 - Star"},
                {"Felix Mutua", "M", "KG2 - Star"},
                {"Gloria Achieng", "F", "KG3 - Rainbow"},
                {"Hassan Ali", "M", "KG3 - Rainbow"}
        };
        int seq = 1;
        for (String[] row : studentsData) {
            Student s = studentRepository.save(Student.builder()
                    .admissionNumber(String.format("ON-2026-%04d", seq))
                    .fullName(row[0])
                    .gender(row[1])
                    .dateOfBirth(LocalDate.of(2022, Month.MARCH, 10 + seq))
                    .guardianName("Guardian of " + row[0])
                    .guardianPhone("07" + String.format("%08d", 10000000 + seq))
                    .status(StudentStatus.ACTIVE)
                    .build());
            SchoolClass cls = switch (row[2]) {
                case "Baby Class - Sun" -> baby;
                case "KG1 - Moon" -> kg1;
                case "KG2 - Star" -> kg2;
                default -> kg3;
            };
            enrollmentRepository.save(Enrollment.builder()
                    .student(s).schoolClass(cls).academicYear(year)
                    .status(StudentStatus.ACTIVE)
                    .joinedOn(LocalDate.of(2026, Month.JANUARY, 5))
                    .build());
            seq++;
        }

        // 8. Attendance for the last 3 weekdays before today
        LocalDate today = LocalDate.now();
        LocalDate d = today.minusDays(1);
        int marked = 0;
        while (marked < 3 && d.isAfter(today.minusDays(14))) {
            if (d.getDayOfWeek().getValue() <= 5) {
                for (Student s : studentRepository.findAll()) {
                    AttendanceStatus st = (s.getId() % 7 == 0) ? AttendanceStatus.ABSENT
                            : (s.getId() % 5 == 0) ? AttendanceStatus.LATE
                            : AttendanceStatus.PRESENT;
                    attendanceRepository.save(Attendance.builder()
                            .student(s).date(d).status(st).build());
                }
                marked++;
            }
            d = d.minusDays(1);
        }

        // 9. Fees: general fee for term1 + a payment
        feeStructureRepository.save(FeeStructure.builder()
                .term(term1).schoolClass(null).amount(new BigDecimal("12000.00")).build());
        Student amina = studentRepository.findByAdmissionNumber("ON-2026-0001").orElseThrow();
        paymentRepository.save(Payment.builder()
                .receiptNumber("RCP-2026-000001")
                .student(amina).term(term1)
                .amount(new BigDecimal("5000.00"))
                .paymentDate(today)
                .method(PaymentMethod.CASH)
                .note("First instalment")
                .build());

        // 10. Exam with marks for KG1, OPEN
        Exam kg1Exam = examRepository.save(Exam.builder()
                .name("Mid Term 1 Opener")
                .schoolClass(kg1)
                .term(term1)
                .maxMarks(100)
                .status(ExamStatus.OPEN)
                .build());
        List<Student> kg1Students = enrollmentRepository.findBySchoolClassAndStatus(kg1, StudentStatus.ACTIVE)
                .stream().map(Enrollment::getStudent).toList();
        for (Student s : kg1Students) {
            markRepository.save(Mark.builder().exam(kg1Exam).student(s).subject(letters).score(60 + (int) (s.getId() % 30)).build());
            markRepository.save(Mark.builder().exam(kg1Exam).student(s).subject(numbers).score(55 + (int) (s.getId() % 35)).build());
            // Writing left unmarked to demonstrate pending marks
        }

        log.info("Dev seed data created: 1 admin, 1 teacher, 4 classes, 8 students, exam & fees");
    }
}
