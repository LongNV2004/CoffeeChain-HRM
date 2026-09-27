package com.example.coffee_hrm;

import com.example.coffee_hrm.common.time.VietnamTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CoffeeHrmApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(VietnamTime.ZONE));
		SpringApplication.run(CoffeeHrmApplication.class, args);
	}

}
