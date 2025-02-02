package org.finos.springbot.example.claim.expenses;

import org.finos.springbot.workflow.annotations.Work;

import jakarta.validation.constraints.Min;

@Work
public class NewClaim {

	String description;

	@Min(0)
	Number amount;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Number getAmount() {
		return amount;
	}

	public void setAmount(Number amount) {
		this.amount = amount;
	}
	
	
}
