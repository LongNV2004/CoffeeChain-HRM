package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.time.VietnamTime;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.LocalDateTime;

@ControllerAdvice
public class VietnamTimeModelAdvice {

    @ModelAttribute("vietnamNow")
    public LocalDateTime vietnamNow() {
        return VietnamTime.now();
    }

    @ModelAttribute("vietnamToday")
    public LocalDate vietnamToday() {
        return VietnamTime.today();
    }
}
