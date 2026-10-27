package com.elijahlyle.budgetmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class BudgetManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BudgetManagerApplication.class, args);
	}

}
