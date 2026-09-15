package com.example.attendance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Data
@Component
@ConfigurationProperties(prefix = "attendance.semester")
public class SemesterProperties {
    private String academicYear;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
}
