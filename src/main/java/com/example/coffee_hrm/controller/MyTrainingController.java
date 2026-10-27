package com.example.coffee_hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/training/my")
@PreAuthorize("hasRole('MANAGER')")
public class MyTrainingController {

    @GetMapping
    public String classListPage() {
        return "redirect:/training";
    }

    @GetMapping("/classes/{id}")
    public String classDetailPage(@PathVariable Integer id) {
        return "redirect:/training/classes/" + id;
    }
}
