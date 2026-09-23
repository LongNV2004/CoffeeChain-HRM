package com.example.coffee_hrm.controller;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class StoreController {
    private final StoreService storeService;
    private final EmployeeService employeeService;
    // UC 1.1 - Admin View Cafe Locations
    @GetMapping("/stores")
    public String viewCafeLocations(Model model) {
        model.addAttribute(
                "stores",
                storeService.getAllStores()
        );
        return "admin/stores";
    }
    // UC 6 - View Store Employees
    @GetMapping("/stores/{storeId}/employees")
    public String viewStoreEmployees(
            @PathVariable Integer storeId,
            Model model
    ) {
        model.addAttribute(
                "store",
                storeService.getStoreById(storeId)
        );
        model.addAttribute(
                "employees",
                employeeService.getEmployeesByStore(storeId)
        );
        return "admin/store-employees";
    }
    // UC 7 - View Chain Employees
    @GetMapping("/employees")
    public String viewChainEmployees(Model model) {
        model.addAttribute(
                "employees",
                employeeService.getAllEmployees()
        );
        return "admin/chain-employees";
    }
}