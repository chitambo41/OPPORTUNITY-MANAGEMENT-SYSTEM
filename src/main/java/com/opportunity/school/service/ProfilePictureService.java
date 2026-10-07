package com.opportunity.school.service;

import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.Student;
import com.opportunity.school.model.Teacher;
import com.opportunity.school.repository.StudentRepository;
import com.opportunity.school.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfilePictureService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000;

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @Transactional
    public void saveStudentPicture(Long studentId, MultipartFile file) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> NotFoundException.entity("Student", studentId));
        PictureData picture = readPicture(file);
        student.setProfilePicture(picture.bytes());
        student.setProfilePictureContentType(picture.contentType());
        studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public Optional<PictureData> getStudentPicture(Long studentId) {
        return studentRepository.findById(studentId)
                .flatMap(student -> pictureData(student.getProfilePicture(), student.getProfilePictureContentType()));
    }

    @Transactional
    public void saveTeacherPicture(Long teacherId, MultipartFile file) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> NotFoundException.entity("Teacher", teacherId));
        PictureData picture = readPicture(file);
        teacher.setProfilePicture(picture.bytes());
        teacher.setProfilePictureContentType(picture.contentType());
        teacherRepository.save(teacher);
    }

    @Transactional(readOnly = true)
    public Optional<PictureData> getTeacherPicture(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .flatMap(teacher -> pictureData(teacher.getProfilePicture(), teacher.getProfilePictureContentType()));
    }

    private PictureData readPicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose an image to upload");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException("Profile pictures must be 5 MB or smaller");
        }

        try {
            byte[] bytes = file.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) {
                    throw new BusinessException("The uploaded file is not a supported image");
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new BusinessException("Upload a JPEG or PNG image");
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase();
                    if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                        throw new BusinessException("Upload a JPEG or PNG image");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                        throw new BusinessException("Image dimensions are too large");
                    }
                    String contentType = format.equals("png") ? "image/png" : "image/jpeg";
                    return new PictureData(bytes, contentType);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded image");
        }
    }

    private Optional<PictureData> pictureData(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || contentType == null) {
            return Optional.empty();
        }
        return Optional.of(new PictureData(bytes, contentType));
    }

    public record PictureData(byte[] bytes, String contentType) { }
}